package com.restaurantplanner.ai.service;

import com.restaurantplanner.ai.domain.AiInsight;
import com.restaurantplanner.ai.domain.AiInsightType;
import com.restaurantplanner.ai.domain.AiSeverity;
import com.restaurantplanner.audit.domain.AuditLog;
import com.restaurantplanner.audit.domain.AuditLogRepository;
import com.restaurantplanner.diningroom.domain.DiningRoom;
import com.restaurantplanner.diningroom.domain.DiningRoomRepository;
import com.restaurantplanner.optimization.service.CandidateFinder;
import com.restaurantplanner.planning.api.PlanningDayResponse;
import com.restaurantplanner.planning.api.PlanningDiningRoomResponse;
import com.restaurantplanner.planning.api.PlanningReservationSummaryResponse;
import com.restaurantplanner.planning.api.PlanningTableResponse;
import com.restaurantplanner.reservation.domain.Reservation;
import com.restaurantplanner.reservation.domain.ReservationAssignment;
import com.restaurantplanner.reservation.domain.ReservationAssignmentRepository;
import com.restaurantplanner.reservation.domain.ReservationRepository;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.tablecombination.domain.TableCombination;
import com.restaurantplanner.tablecombination.domain.TableCombinationRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class InsightGenerator {

    private final CandidateFinder candidateFinder;
    private final ReservationRepository reservationRepository;
    private final ReservationAssignmentRepository reservationAssignmentRepository;
    private final TableCombinationRepository tableCombinationRepository;
    private final DiningRoomRepository diningRoomRepository;
    private final AuditLogRepository auditLogRepository;

    public InsightGenerator(
        CandidateFinder candidateFinder,
        ReservationRepository reservationRepository,
        ReservationAssignmentRepository reservationAssignmentRepository,
        TableCombinationRepository tableCombinationRepository,
        DiningRoomRepository diningRoomRepository,
        AuditLogRepository auditLogRepository
    ) {
        this.candidateFinder = candidateFinder;
        this.reservationRepository = reservationRepository;
        this.reservationAssignmentRepository = reservationAssignmentRepository;
        this.tableCombinationRepository = tableCombinationRepository;
        this.diningRoomRepository = diningRoomRepository;
        this.auditLogRepository = auditLogRepository;
    }

    public List<AiInsight> generate(Restaurant restaurant, LocalDate date, PlanningDayResponse planningDay) {
        List<AiInsight> insights = new ArrayList<>();

        List<Reservation> reservations = reservationRepository
            .findByRestaurantIdAndReservationDateOrderByStartTimeAscIdAsc(restaurant.getId(), date);
        List<ReservationAssignment> assignments = reservationAssignmentRepository
            .findByActiveTrueAndReservationRestaurantIdAndReservationReservationDateAndReservationStatusIn(
                restaurant.getId(),
                date,
                AiService.ACTIVE_OPERATIONAL_STATUSES
            );
        Map<Long, ReservationAssignment> assignmentByReservationId = assignments.stream()
            .collect(LinkedHashMap::new, (map, assignment) -> map.put(assignment.getReservation().getId(), assignment), Map::putAll);

        insights.addAll(generateWastedLargeTableInsights(restaurant, date, reservations, assignmentByReservationId));
        insights.addAll(generateDeadGapInsights(restaurant, date, planningDay));
        insights.addAll(generateSuboptimalRoomUsageInsights(restaurant, date, planningDay));
        insights.addAll(generateCapacityUnderutilizedInsights(restaurant, date, planningDay));
        insights.addAll(generateHighNoShowRiskInsights(restaurant, date, reservations));
        insights.addAll(generateOverAssignedCombinationInsights(restaurant, date, reservations, assignmentByReservationId, planningDay));

        return deduplicate(insights);
    }

    private List<AiInsight> generateWastedLargeTableInsights(
        Restaurant restaurant,
        LocalDate date,
        List<Reservation> reservations,
        Map<Long, ReservationAssignment> assignmentByReservationId
    ) {
        List<AiInsight> insights = new ArrayList<>();
        for (Reservation reservation : reservations) {
            ReservationAssignment assignment = assignmentByReservationId.get(reservation.getId());
            if (assignment == null || assignment.getTable() == null) {
                continue;
            }

            int assignedCapacity = assignment.getTable().getMaxCapacity();
            if (reservation.getPartySize() > 2 || assignedCapacity < 6) {
                continue;
            }

            var alternative = candidateFinder.findCandidates(restaurant.getId()).stream()
                .filter(candidate -> candidate.table() != null)
                .filter(candidate -> !Objects.equals(candidate.table().getId(), assignment.getTable().getId()))
                .filter(candidate -> candidate.maxCapacity() >= reservation.getPartySize())
                .filter(candidate -> candidate.maxCapacity() < assignedCapacity)
                .filter(candidate -> candidate.primaryRoomPriority() == assignment.getTable().getDiningRoom().getPriority())
                .min(Comparator.comparingInt(candidate -> candidate.maxCapacity()));

            if (alternative.isEmpty()) {
                continue;
            }

            String description = "Table " + assignment.getTable().getCode() + " (" + assignedCapacity
                + " guests) was assigned to a party of " + reservation.getPartySize()
                + " when " + alternative.get().displayName()
                + " could accommodate the same service.";

            insights.add(buildInsight(
                restaurant,
                date,
                AiInsightType.WASTED_LARGE_TABLE,
                assignedCapacity >= 8 ? AiSeverity.HIGH : AiSeverity.MEDIUM,
                "Large table underused",
                description,
                "Reservation",
                reservation.getId(),
                "{\"assignedTableId\":" + assignment.getTable().getId()
                    + ",\"alternativeResource\":\"" + alternative.get().displayName() + "\"}"
            ));
        }
        return insights;
    }

    private List<AiInsight> generateDeadGapInsights(Restaurant restaurant, LocalDate date, PlanningDayResponse planningDay) {
        List<AiInsight> insights = new ArrayList<>();
        for (PlanningDiningRoomResponse room : planningDay.diningRooms()) {
            for (PlanningTableResponse table : room.tables()) {
                List<PlanningReservationSummaryResponse> reservations = table.reservations().stream()
                    .sorted(Comparator.comparing(PlanningReservationSummaryResponse::startTime))
                    .toList();

                for (int index = 0; index < reservations.size() - 1; index++) {
                    PlanningReservationSummaryResponse current = reservations.get(index);
                    PlanningReservationSummaryResponse next = reservations.get(index + 1);
                    LocalTime end = current.effectiveEndTime();
                    LocalTime start = next.startTime();
                    if (end == null || !start.isAfter(end)) {
                        continue;
                    }

                    long gapMinutes = java.time.Duration.between(end, start).toMinutes();
                    if (gapMinutes < 45 || gapMinutes > 120) {
                        continue;
                    }

                    String description = "Table " + table.code() + " has an available gap of "
                        + gapMinutes + " minutes between "
                        + current.customerName() + " and " + next.customerName()
                        + ", which could accommodate another short reservation.";

                    insights.add(buildInsight(
                        restaurant,
                        date,
                        AiInsightType.DEAD_GAP_OPPORTUNITY,
                        gapMinutes >= 75 ? AiSeverity.MEDIUM : AiSeverity.LOW,
                        "Reusable scheduling gap",
                        description,
                        "RestaurantTable",
                        table.id(),
                        "{\"gapMinutes\":" + gapMinutes + ",\"tableCode\":\"" + table.code() + "\"}"
                    ));
                }
            }
        }
        return insights;
    }

    private List<AiInsight> generateSuboptimalRoomUsageInsights(Restaurant restaurant, LocalDate date, PlanningDayResponse planningDay) {
        List<AiInsight> insights = new ArrayList<>();
        List<PlanningDiningRoomResponse> rooms = planningDay.diningRooms().stream()
            .sorted(Comparator.comparingInt(PlanningDiningRoomResponse::priority))
            .toList();

        if (rooms.size() < 2) {
            return insights;
        }

        PlanningDiningRoomResponse primaryRoom = rooms.get(0);
        boolean primaryHasIdleTable = primaryRoom.tables().stream().anyMatch(table -> table.active() && table.reservations().isEmpty());
        if (!primaryHasIdleTable) {
            return insights;
        }

        for (int index = 1; index < rooms.size(); index++) {
            PlanningDiningRoomResponse secondaryRoom = rooms.get(index);
            boolean secondaryUsed = secondaryRoom.tables().stream().anyMatch(table -> !table.reservations().isEmpty());
            if (!secondaryUsed) {
                continue;
            }

            String description = "Dining room " + secondaryRoom.name()
                + " is in use while the higher-priority dining room " + primaryRoom.name()
                + " still has unused tables.";
            insights.add(buildInsight(
                restaurant,
                date,
                AiInsightType.SUBOPTIMAL_ROOM_USAGE,
                AiSeverity.MEDIUM,
                "Suboptimal dining room use",
                description,
                "DiningRoom",
                secondaryRoom.id(),
                "{\"primaryRoomId\":" + primaryRoom.id() + ",\"secondaryRoomId\":" + secondaryRoom.id() + "}"
            ));
        }
        return insights;
    }

    private List<AiInsight> generateCapacityUnderutilizedInsights(Restaurant restaurant, LocalDate date, PlanningDayResponse planningDay) {
        List<AiInsight> insights = new ArrayList<>();
        for (PlanningDiningRoomResponse room : planningDay.diningRooms()) {
            for (PlanningTableResponse table : room.tables()) {
                for (PlanningReservationSummaryResponse reservation : table.reservations()) {
                    int wastedSeats = table.maxCapacity() - reservation.partySize();
                    if (wastedSeats < 3) {
                        continue;
                    }

                    String description = "A reservation for " + reservation.partySize()
                        + " guests occupies table " + table.code()
                        + ", leaving " + wastedSeats + " seats below its maximum capacity.";

                    insights.add(buildInsight(
                        restaurant,
                        date,
                        AiInsightType.CAPACITY_UNDERUTILIZED,
                        wastedSeats >= 5 ? AiSeverity.HIGH : AiSeverity.LOW,
                        "Underused capacity",
                        description,
                        "Reservation",
                        reservation.reservationId(),
                        "{\"tableId\":" + table.id() + ",\"wastedSeats\":" + wastedSeats + "}"
                    ));
                }
            }
        }
        return insights;
    }

    private List<AiInsight> generateHighNoShowRiskInsights(Restaurant restaurant, LocalDate date, List<Reservation> reservations) {
        List<AiInsight> insights = new ArrayList<>();
        List<AuditLog> auditLogs = auditLogRepository.findByRestaurantIdOrderByCreatedAtDesc(
            restaurant.getId(),
            PageRequest.of(0, 500)
        );

        for (Reservation reservation : reservations) {
            long customerNoShows = auditLogs.stream()
                .filter(log -> "Reservation".equals(log.getEntityType()))
                .filter(log -> Objects.equals(log.getEntityId(), reservation.getId()) || log.getMetadataJson() != null)
                .filter(log -> "reservation.no_show".equals(log.getAction()))
                .count();

            boolean riskByChannel = reservation.getChannel().name().equals("PHONE") || reservation.getChannel().name().equals("WEB");
            if (customerNoShows == 0 && !riskByChannel) {
                continue;
            }

            String description = "The reservation for " + buildCustomerName(reservation)
                + " has a no-show risk due to channel " + reservation.getChannel()
                + (customerNoShows > 0 ? " and recorded operational history." : ".");

            insights.add(buildInsight(
                restaurant,
                date,
                AiInsightType.HIGH_NO_SHOW_RISK,
                customerNoShows > 0 ? AiSeverity.HIGH : AiSeverity.MEDIUM,
                "High no-show risk",
                description,
                "Reservation",
                reservation.getId(),
                "{\"channel\":\"" + reservation.getChannel() + "\",\"historicalNoShows\":" + customerNoShows + "}"
            ));
        }
        return insights;
    }

    private List<AiInsight> generateOverAssignedCombinationInsights(
        Restaurant restaurant,
        LocalDate date,
        List<Reservation> reservations,
        Map<Long, ReservationAssignment> assignmentByReservationId,
        PlanningDayResponse planningDay
    ) {
        List<AiInsight> insights = new ArrayList<>();
        Map<Long, Set<Long>> occupiedSingleTables = buildOccupiedSingleTables(planningDay);

        for (Reservation reservation : reservations) {
            ReservationAssignment assignment = assignmentByReservationId.get(reservation.getId());
            if (assignment == null || assignment.getTableCombination() == null) {
                continue;
            }

            TableCombination combination = tableCombinationRepository.findById(assignment.getTableCombination().getId()).orElse(null);
            if (combination == null) {
                continue;
            }

            List<DiningRoom> rooms = diningRoomRepository.findByRestaurantIdOrderByPriorityAscIdAsc(restaurant.getId());
            boolean singleTableAvailable = planningDay.diningRooms().stream()
                .flatMap(room -> room.tables().stream())
                .filter(table -> table.active())
                .filter(table -> table.maxCapacity() >= reservation.getPartySize())
                .filter(table -> !occupiedSingleTables.getOrDefault(table.id(), Set.of()).contains(reservation.getId()))
                .anyMatch(table -> table.reservations().stream()
                    .noneMatch(other -> !Objects.equals(other.reservationId(), reservation.getId())
                        && overlaps(
                            reservation.getStartTime(),
                            reservation.getEndTime().plusMinutes(reservation.getCleaningBufferMin()),
                            other.startTime(),
                            other.effectiveEndTime()
                        )));

            if (!singleTableAvailable) {
                continue;
            }

            String description = "Combination " + combination.getName()
                + " was used for a reservation of " + reservation.getPartySize()
                + " guests while a single table was still available for the same time range.";

            insights.add(buildInsight(
                restaurant,
                date,
                AiInsightType.OVER_ASSIGNED_COMBINATION,
                AiSeverity.MEDIUM,
                "Table combination used prematurely",
                description,
                "Reservation",
                reservation.getId(),
                "{\"tableCombinationId\":" + combination.getId() + "}"
            ));
        }
        return insights;
    }

    private Map<Long, Set<Long>> buildOccupiedSingleTables(PlanningDayResponse planningDay) {
        Map<Long, Set<Long>> occupied = new LinkedHashMap<>();
        for (PlanningDiningRoomResponse room : planningDay.diningRooms()) {
            for (PlanningTableResponse table : room.tables()) {
                Set<Long> reservationIds = new HashSet<>();
                for (PlanningReservationSummaryResponse reservation : table.reservations()) {
                    reservationIds.add(reservation.reservationId());
                }
                occupied.put(table.id(), reservationIds);
            }
        }
        return occupied;
    }

    private boolean overlaps(
        LocalTime startA,
        LocalTime endA,
        LocalTime startB,
        LocalTime endB
    ) {
        if (endA == null || endB == null) {
            return false;
        }
        return startA.isBefore(endB) && endA.isAfter(startB);
    }

    private String buildCustomerName(Reservation reservation) {
        String firstName = reservation.getCustomer().getFirstName();
        String lastName = reservation.getCustomer().getLastName();
        if (firstName == null && lastName == null) {
            return reservation.getCustomer().getPhone();
        }
        if (firstName == null) {
            return lastName;
        }
        if (lastName == null) {
            return firstName;
        }
        return firstName + " " + lastName;
    }

    private List<AiInsight> deduplicate(List<AiInsight> insights) {
        Map<String, AiInsight> unique = new LinkedHashMap<>();
        for (AiInsight insight : insights) {
            String key = insight.getType() + "|" + insight.getEntityType() + "|" + insight.getEntityId() + "|" + insight.getTitle();
            unique.putIfAbsent(key, insight);
        }
        return new ArrayList<>(unique.values());
    }

    private AiInsight buildInsight(
        Restaurant restaurant,
        LocalDate date,
        AiInsightType type,
        AiSeverity severity,
        String title,
        String description,
        String entityType,
        Long entityId,
        String metadataJson
    ) {
        AiInsight insight = new AiInsight();
        insight.setRestaurant(restaurant);
        insight.setDate(date);
        insight.setType(type);
        insight.setSeverity(severity);
        insight.setTitle(title);
        insight.setDescription(description);
        insight.setEntityType(entityType);
        insight.setEntityId(entityId);
        insight.setMetadataJson(metadataJson);
        insight.setDismissed(false);
        return insight;
    }
}
