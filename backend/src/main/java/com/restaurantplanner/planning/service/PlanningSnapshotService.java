package com.restaurantplanner.planning.service;

import com.restaurantplanner.common.api.NotFoundException;
import com.restaurantplanner.diningroom.domain.DiningRoom;
import com.restaurantplanner.diningroom.domain.DiningRoomRepository;
import com.restaurantplanner.planning.api.PlanningConflictResponse;
import com.restaurantplanner.planning.api.PlanningDayResponse;
import com.restaurantplanner.planning.api.PlanningDiningRoomResponse;
import com.restaurantplanner.planning.api.PlanningReservationSummaryResponse;
import com.restaurantplanner.planning.api.PlanningRestaurantSummaryResponse;
import com.restaurantplanner.planning.api.PlanningTableResponse;
import com.restaurantplanner.reservation.domain.Reservation;
import com.restaurantplanner.reservation.domain.ReservationAssignment;
import com.restaurantplanner.reservation.domain.ReservationAssignmentRepository;
import com.restaurantplanner.reservation.domain.ReservationRepository;
import com.restaurantplanner.reservation.domain.ReservationStatus;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.table.domain.RestaurantTable;
import com.restaurantplanner.table.domain.RestaurantTableRepository;
import com.restaurantplanner.table.domain.TableType;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningSnapshotService {

    private static final Set<ReservationStatus> PLANNING_VISIBLE_STATUSES = EnumSet.of(
        ReservationStatus.PENDING,
        ReservationStatus.CONFIRMED,
        ReservationStatus.SEATED,
        ReservationStatus.COMPLETED
    );

    private static final Set<ReservationStatus> ASSIGNABLE_STATUSES = EnumSet.of(
        ReservationStatus.PENDING,
        ReservationStatus.CONFIRMED,
        ReservationStatus.SEATED
    );

    private final RestaurantRepository restaurantRepository;
    private final DiningRoomRepository diningRoomRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationAssignmentRepository reservationAssignmentRepository;

    public PlanningSnapshotService(
        RestaurantRepository restaurantRepository,
        DiningRoomRepository diningRoomRepository,
        RestaurantTableRepository restaurantTableRepository,
        ReservationRepository reservationRepository,
        ReservationAssignmentRepository reservationAssignmentRepository
    ) {
        this.restaurantRepository = restaurantRepository;
        this.diningRoomRepository = diningRoomRepository;
        this.restaurantTableRepository = restaurantTableRepository;
        this.reservationRepository = reservationRepository;
        this.reservationAssignmentRepository = reservationAssignmentRepository;
    }

    @Transactional(readOnly = true)
    public PlanningDayResponse build(Long restaurantId, LocalDate date) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new NotFoundException("Restaurant not found"));
        return build(restaurant, date);
    }

    public PlanningDayResponse build(Restaurant restaurant, LocalDate date) {
        List<DiningRoom> diningRooms = diningRoomRepository.findByRestaurantIdOrderByPriorityAscIdAsc(restaurant.getId());
        List<RestaurantTable> tables = restaurantTableRepository.findByRestaurantIdAndTableTypeNotOrderByDiningRoomIdAscCodeAsc(
            restaurant.getId(),
            TableType.STORAGE
        );
        List<Reservation> reservations = reservationRepository.findByRestaurantIdAndReservationDateOrderByStartTimeAscIdAsc(
            restaurant.getId(),
            date
        );
        List<ReservationAssignment> activeAssignments = reservationAssignmentRepository
            .findByActiveTrueAndReservationRestaurantIdAndReservationReservationDateAndReservationStatusIn(
                restaurant.getId(),
                date,
                PLANNING_VISIBLE_STATUSES
            );

        Map<Long, ReservationAssignment> assignmentByReservationId = activeAssignments.stream()
            .collect(Collectors.toMap(assignment -> assignment.getReservation().getId(), assignment -> assignment, (left, right) -> right));

        List<PlanningReservationSummaryResponse> assignedReservations = activeAssignments.stream()
            .sorted(Comparator.comparing((ReservationAssignment assignment) -> assignment.getReservation().getStartTime())
                .thenComparing(assignment -> assignment.getReservation().getId()))
            .map(this::toPlanningReservationSummary)
            .toList();

        List<PlanningReservationSummaryResponse> unassignedReservations = reservations.stream()
            .filter(reservation -> ASSIGNABLE_STATUSES.contains(reservation.getStatus()))
            .filter(reservation -> !assignmentByReservationId.containsKey(reservation.getId()))
            .map(this::toUnassignedReservationSummary)
            .toList();

        Map<Long, List<PlanningReservationSummaryResponse>> reservationsByTableId = buildReservationsByTableId(activeAssignments);

        List<PlanningDiningRoomResponse> diningRoomResponses = diningRooms.stream()
            .map(diningRoom -> toDiningRoomResponse(diningRoom, tables, reservationsByTableId))
            .toList();

        return new PlanningDayResponse(
            date,
            new PlanningRestaurantSummaryResponse(restaurant.getId(), restaurant.getName(), restaurant.getTimezone()),
            diningRoomResponses,
            assignedReservations,
            unassignedReservations,
            buildConflicts(activeAssignments),
            buildTimeBlocks()
        );
    }

    private Map<Long, List<PlanningReservationSummaryResponse>> buildReservationsByTableId(List<ReservationAssignment> assignments) {
        Map<Long, List<PlanningReservationSummaryResponse>> reservationsByTableId = new HashMap<>();
        for (ReservationAssignment assignment : assignments) {
            PlanningReservationSummaryResponse summary = toPlanningReservationSummary(assignment);
            if (assignment.getTable() != null) {
                reservationsByTableId.computeIfAbsent(assignment.getTable().getId(), key -> new ArrayList<>()).add(summary);
            } else if (assignment.getTableCombination() != null) {
                assignment.getTableCombination().getItems().forEach(item ->
                    reservationsByTableId.computeIfAbsent(item.getTable().getId(), key -> new ArrayList<>()).add(summary)
                );
            }
        }

        reservationsByTableId.values().forEach(list ->
            list.sort(Comparator.comparing(PlanningReservationSummaryResponse::startTime).thenComparing(PlanningReservationSummaryResponse::reservationId))
        );
        return reservationsByTableId;
    }

    private PlanningDiningRoomResponse toDiningRoomResponse(
        DiningRoom diningRoom,
        List<RestaurantTable> allTables,
        Map<Long, List<PlanningReservationSummaryResponse>> reservationsByTableId
    ) {
        List<PlanningTableResponse> tableResponses = allTables.stream()
            .filter(table -> Objects.equals(table.getDiningRoom().getId(), diningRoom.getId()))
            .map(table -> new PlanningTableResponse(
                table.getId(),
                table.getCode(),
                table.getLabel(),
                table.getMinCapacity(),
                table.getMaxCapacity(),
                table.isActive(),
                table.getX(),
                table.getY(),
                table.getWidth(),
                table.getHeight(),
                reservationsByTableId.getOrDefault(table.getId(), List.of())
            ))
            .toList();

        return new PlanningDiningRoomResponse(
            diningRoom.getId(),
            diningRoom.getName(),
            diningRoom.getPriority(),
            diningRoom.isAccessible(),
            diningRoom.isActive(),
            diningRoom.getLayoutWidth(),
            diningRoom.getLayoutHeight(),
            tableResponses
        );
    }

    private PlanningReservationSummaryResponse toPlanningReservationSummary(ReservationAssignment assignment) {
        Reservation reservation = assignment.getReservation();
        return new PlanningReservationSummaryResponse(
            reservation.getId(),
            reservation.getCustomer().getId(),
            buildCustomerName(reservation),
            reservation.getStatus(),
            reservation.getPartySize(),
            reservation.getReservationDate(),
            reservation.getStartTime(),
            reservation.getEndTime(),
            reservation.getEndTime().plusMinutes(reservation.getCleaningBufferMin()),
            reservation.getEstimatedDurationMin(),
            reservation.getCleaningBufferMin(),
            reservation.isAccessibilityRequired(),
            reservation.getSpecialRequests(),
            assignment.getAssignmentType(),
            assignment.getTable() == null ? null : assignment.getTable().getId(),
            assignment.getTable() == null ? null : assignment.getTable().getCode(),
            assignment.getTableCombination() == null ? null : assignment.getTableCombination().getId(),
            assignment.getTableCombination() == null ? null : assignment.getTableCombination().getName()
        );
    }

    private PlanningReservationSummaryResponse toUnassignedReservationSummary(Reservation reservation) {
        return new PlanningReservationSummaryResponse(
            reservation.getId(),
            reservation.getCustomer().getId(),
            buildCustomerName(reservation),
            reservation.getStatus(),
            reservation.getPartySize(),
            reservation.getReservationDate(),
            reservation.getStartTime(),
            reservation.getEndTime(),
            reservation.getEndTime().plusMinutes(reservation.getCleaningBufferMin()),
            reservation.getEstimatedDurationMin(),
            reservation.getCleaningBufferMin(),
            reservation.isAccessibilityRequired(),
            reservation.getSpecialRequests(),
            null,
            null,
            null,
            null,
            null
        );
    }

    private List<PlanningConflictResponse> buildConflicts(List<ReservationAssignment> assignments) {
        List<PlanningConflictResponse> conflicts = new ArrayList<>();
        Map<Long, List<ReservationAssignment>> assignmentsByTableId = new LinkedHashMap<>();

        for (ReservationAssignment assignment : assignments) {
            if (assignment.getTable() != null) {
                assignmentsByTableId.computeIfAbsent(assignment.getTable().getId(), key -> new ArrayList<>()).add(assignment);
            } else if (assignment.getTableCombination() != null) {
                assignment.getTableCombination().getItems().forEach(item ->
                    assignmentsByTableId.computeIfAbsent(item.getTable().getId(), key -> new ArrayList<>()).add(assignment)
                );
            }
        }

        for (Map.Entry<Long, List<ReservationAssignment>> entry : assignmentsByTableId.entrySet()) {
            List<ReservationAssignment> resourceAssignments = entry.getValue().stream()
                .sorted(Comparator.comparing((ReservationAssignment assignment) -> assignment.getReservation().getStartTime())
                    .thenComparing(assignment -> assignment.getReservation().getId()))
                .toList();

            for (int index = 0; index < resourceAssignments.size(); index++) {
                for (int otherIndex = index + 1; otherIndex < resourceAssignments.size(); otherIndex++) {
                    ReservationAssignment first = resourceAssignments.get(index);
                    ReservationAssignment second = resourceAssignments.get(otherIndex);
                    LocalTime firstEffectiveEnd = first.getReservation().getEndTime().plusMinutes(first.getReservation().getCleaningBufferMin());
                    LocalTime secondEffectiveEnd = second.getReservation().getEndTime().plusMinutes(second.getReservation().getCleaningBufferMin());

                    boolean overlaps = first.getReservation().getStartTime().isBefore(secondEffectiveEnd)
                        && firstEffectiveEnd.isAfter(second.getReservation().getStartTime());

                    if (overlaps) {
                        LocalTime overlapStart = first.getReservation().getStartTime().isAfter(second.getReservation().getStartTime())
                            ? first.getReservation().getStartTime()
                            : second.getReservation().getStartTime();
                        LocalTime overlapEnd = firstEffectiveEnd.isBefore(secondEffectiveEnd) ? firstEffectiveEnd : secondEffectiveEnd;
                        conflicts.add(new PlanningConflictResponse(
                            "OVERLAP",
                            "TABLE",
                            entry.getKey(),
                            first.getTable() != null ? first.getTable().getCode() : second.getTable() != null ? second.getTable().getCode() : "table",
                            List.of(first.getReservation().getId(), second.getReservation().getId()),
                            overlapStart,
                            overlapEnd,
                            "Two reservations overlap on the same table resource"
                        ));
                    }
                }
            }
        }

        return conflicts;
    }

    private List<String> buildTimeBlocks() {
        List<String> blocks = new ArrayList<>();
        LocalTime current = LocalTime.MIDNIGHT;
        for (int index = 0; index < 48; index++) {
            blocks.add(current.toString());
            current = current.plusMinutes(30);
        }
        return blocks;
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
}
