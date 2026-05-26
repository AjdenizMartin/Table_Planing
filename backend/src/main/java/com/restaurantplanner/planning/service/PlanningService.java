package com.restaurantplanner.planning.service;

import com.restaurantplanner.ai.service.AiService;
import com.restaurantplanner.audit.AuditService;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.common.api.ConflictException;
import com.restaurantplanner.common.api.NotFoundException;
import com.restaurantplanner.diningroom.domain.DiningRoom;
import com.restaurantplanner.diningroom.domain.DiningRoomRepository;
import com.restaurantplanner.optimization.domain.AssignmentCandidate;
import com.restaurantplanner.optimization.domain.AssignmentCandidateType;
import com.restaurantplanner.optimization.domain.CandidateAvailability;
import com.restaurantplanner.optimization.service.AvailabilityChecker;
import com.restaurantplanner.optimization.service.ReservationAssignmentService;
import com.restaurantplanner.planning.api.MoveReservationRequest;
import com.restaurantplanner.planning.api.PlanningConflictResponse;
import com.restaurantplanner.planning.api.PlanningDayResponse;
import com.restaurantplanner.planning.api.PlanningDiningRoomResponse;
import com.restaurantplanner.planning.api.PlanningReservationSummaryResponse;
import com.restaurantplanner.planning.api.PlanningRestaurantSummaryResponse;
import com.restaurantplanner.planning.api.PlanningTableResponse;
import com.restaurantplanner.realtime.RestaurantRealtimePublisher;
import com.restaurantplanner.reservation.domain.Reservation;
import com.restaurantplanner.reservation.domain.ReservationAssignment;
import com.restaurantplanner.reservation.domain.ReservationAssignmentRepository;
import com.restaurantplanner.reservation.domain.ReservationRepository;
import com.restaurantplanner.reservation.domain.ReservationStatus;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.table.domain.RestaurantTable;
import com.restaurantplanner.table.domain.RestaurantTableRepository;
import com.restaurantplanner.tablecombination.domain.TableCombination;
import com.restaurantplanner.tablecombination.domain.TableCombinationRepository;
import com.restaurantplanner.user.domain.User;
import com.restaurantplanner.user.domain.UserRepository;
import java.time.Instant;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningService {

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
    private final TableCombinationRepository tableCombinationRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationAssignmentRepository reservationAssignmentRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final ReservationAssignmentService reservationAssignmentService;
    private final AvailabilityChecker availabilityChecker;
    private final UserRepository userRepository;
    private final RestaurantRealtimePublisher realtimePublisher;
    private final AuditService auditService;
    private final AiService aiService;

    public PlanningService(
        RestaurantRepository restaurantRepository,
        DiningRoomRepository diningRoomRepository,
        RestaurantTableRepository restaurantTableRepository,
        TableCombinationRepository tableCombinationRepository,
        ReservationRepository reservationRepository,
        ReservationAssignmentRepository reservationAssignmentRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        ReservationAssignmentService reservationAssignmentService,
        AvailabilityChecker availabilityChecker,
        UserRepository userRepository,
        RestaurantRealtimePublisher realtimePublisher,
        AuditService auditService,
        AiService aiService
    ) {
        this.restaurantRepository = restaurantRepository;
        this.diningRoomRepository = diningRoomRepository;
        this.restaurantTableRepository = restaurantTableRepository;
        this.tableCombinationRepository = tableCombinationRepository;
        this.reservationRepository = reservationRepository;
        this.reservationAssignmentRepository = reservationAssignmentRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.reservationAssignmentService = reservationAssignmentService;
        this.availabilityChecker = availabilityChecker;
        this.userRepository = userRepository;
        this.realtimePublisher = realtimePublisher;
        this.auditService = auditService;
        this.aiService = aiService;
    }

    @Transactional(readOnly = true)
    public PlanningDayResponse getPlanningDay(Long restaurantId, LocalDate date, AuthenticatedUser authenticatedUser) {
        Restaurant restaurant = findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        return buildPlanningDay(restaurant, date);
    }

    @Transactional
    public PlanningDayResponse recalculate(Long restaurantId, LocalDate date, AuthenticatedUser authenticatedUser) {
        Restaurant restaurant = findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        List<Reservation> reservations = reservationRepository.findByRestaurantIdAndReservationDateOrderByStartTimeAscIdAsc(
            restaurantId,
            date
        );
        Set<Long> assignedReservationIds = reservationAssignmentRepository
            .findByActiveTrueAndReservationRestaurantIdAndReservationReservationDateAndReservationStatusIn(
                restaurantId,
                date,
                ASSIGNABLE_STATUSES
            )
            .stream()
            .map(assignment -> assignment.getReservation().getId())
            .collect(Collectors.toSet());

        for (Reservation reservation : reservations) {
            if (ASSIGNABLE_STATUSES.contains(reservation.getStatus()) && !assignedReservationIds.contains(reservation.getId())) {
                reservationAssignmentService.assign(restaurantId, reservation.getId(), authenticatedUser);
            }
        }

        PlanningDayResponse planningDay = buildPlanningDay(restaurant, date);
        aiService.generateInsightsForDate(restaurantId, date, planningDay);
        auditService.record(restaurantId, "Planning", null, "planning.recalculated", authenticatedUser.userId(), "{\"date\":\"" + date + "\"}");
        realtimePublisher.publishPlanningRecalculated(restaurantId, date, "Planning recalculated");
        return planningDay;
    }

    @Transactional
    public PlanningDayResponse moveReservation(
        Long restaurantId,
        MoveReservationRequest request,
        AuthenticatedUser authenticatedUser
    ) {
        Restaurant restaurant = findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        if (request.reservationId() == null) {
            throw new IllegalArgumentException("reservationId is required");
        }
        boolean hasTable = request.tableId() != null;
        boolean hasCombination = request.tableCombinationId() != null;
        if (hasTable == hasCombination) {
            throw new IllegalArgumentException("Exactly one of tableId or tableCombinationId must be provided");
        }

        Reservation reservation = reservationRepository.findByIdAndRestaurantId(request.reservationId(), restaurantId)
            .orElseThrow(() -> new NotFoundException("Reservation not found"));
        if (!ASSIGNABLE_STATUSES.contains(reservation.getStatus())) {
            throw new ConflictException("Only active operational reservations can be moved");
        }

        AssignmentCandidate candidate = hasTable
            ? buildTableCandidate(restaurantId, request.tableId())
            : buildCombinationCandidate(restaurantId, request.tableCombinationId());

        List<ReservationAssignment> occupiedAssignments = reservationAssignmentRepository
            .findByActiveTrueAndReservationRestaurantIdAndReservationReservationDateAndReservationStatusIn(
                restaurantId,
                reservation.getReservationDate(),
                ASSIGNABLE_STATUSES
            );
        CandidateAvailability availability = availabilityChecker.evaluate(candidate, reservation, occupiedAssignments);
        if (!availability.available()) {
            throw new ConflictException("Target resource is not available: " + String.join(", ", availability.rejectionReasons()));
        }

        deactivateCurrentAssignments(reservation.getId());

        ReservationAssignment assignment = new ReservationAssignment();
        assignment.setReservation(reservation);
        assignment.setAssignmentType(hasTable ? "MANUAL_TABLE" : "MANUAL_TABLE_COMBINATION");
        assignment.setDiningRoom(candidate.diningRooms().size() == 1 ? candidate.diningRooms().get(0) : null);
        assignment.setTable(candidate.table());
        assignment.setTableCombination(candidate.tableCombination());
        assignment.setScore(null);
        assignment.setExplanationJson(buildManualExplanationJson(candidate));
        assignment.setAssignedBy(findUserOrNull(authenticatedUser.userId()));
        assignment.setAssignedAt(Instant.now());
        assignment.setActive(true);
        reservationAssignmentRepository.save(assignment);
        PlanningDayResponse planningDay = buildPlanningDay(restaurant, reservation.getReservationDate());
        aiService.generateInsightsForDate(restaurantId, reservation.getReservationDate(), planningDay);
        auditService.record(restaurantId, "Reservation", reservation.getId(), "reservation.moved", authenticatedUser.userId(), "{\"tableId\":" + (request.tableId() != null ? request.tableId() : "null") + ",\"combinationId\":" + (request.tableCombinationId() != null ? request.tableCombinationId() : "null") + "}");
        realtimePublisher.publishReservationEvent(
            "reservation.assigned",
            restaurantId,
            reservation.getId(),
            reservation.getReservationDate(),
            "Reservation moved manually"
        );
        realtimePublisher.publishPlanningRecalculated(
            restaurantId,
            reservation.getReservationDate(),
            "Planning updated after manual move"
        );
        return planningDay;
    }

    @Transactional(readOnly = true)
    public PlanningDayResponse getPlanningDayInternal(Long restaurantId, LocalDate date) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new NotFoundException("Restaurant not found"));
        return buildPlanningDay(restaurant, date);
    }

    private PlanningDayResponse buildPlanningDay(Restaurant restaurant, LocalDate date) {
        List<DiningRoom> diningRooms = diningRoomRepository.findByRestaurantIdOrderByPriorityAscIdAsc(restaurant.getId());
        List<RestaurantTable> tables = restaurantTableRepository.findByRestaurantIdOrderByDiningRoomIdAscCodeAsc(restaurant.getId());
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
        while (current.isBefore(LocalTime.of(23, 59))) {
            blocks.add(current.toString());
            current = current.plusMinutes(30);
        }
        return blocks;
    }

    private AssignmentCandidate buildTableCandidate(Long restaurantId, Long tableId) {
        RestaurantTable table = restaurantTableRepository.findByIdAndRestaurantId(tableId, restaurantId)
            .orElseThrow(() -> new NotFoundException("Table not found"));
        return new AssignmentCandidate(
            AssignmentCandidateType.TABLE,
            table,
            null,
            List.of(table),
            table.getMinCapacity(),
            table.getMaxCapacity(),
            table.getCode()
        );
    }

    private AssignmentCandidate buildCombinationCandidate(Long restaurantId, Long tableCombinationId) {
        TableCombination combination = tableCombinationRepository.findByIdAndRestaurantIdAndActiveTrue(tableCombinationId, restaurantId)
            .orElseThrow(() -> new NotFoundException("Table combination not found"));
        List<RestaurantTable> tables = combination.getItems().stream().map(item -> item.getTable()).toList();
        return new AssignmentCandidate(
            AssignmentCandidateType.TABLE_COMBINATION,
            null,
            combination,
            tables,
            combination.getMinCapacity(),
            combination.getMaxCapacity(),
            combination.getName()
        );
    }

    private void deactivateCurrentAssignments(Long reservationId) {
        for (ReservationAssignment assignment : reservationAssignmentRepository.findByReservationIdAndActiveTrue(reservationId)) {
            assignment.setActive(false);
        }
    }

    private String buildManualExplanationJson(AssignmentCandidate candidate) {
        return "{\"summary\":\"Manual move applied\",\"resource\":\"" + candidate.displayName() + "\"}";
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

    private Restaurant findAccessibleRestaurantOrThrow(Long restaurantId, AuthenticatedUser authenticatedUser) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant not found"));
        }

        return restaurantRepository.findAccessibleByIdAndUserId(restaurantId, authenticatedUser.userId())
            .orElseThrow(() -> new NotFoundException("Restaurant not found"));
    }

    private void requireOwnerManagerOrAdmin(AuthenticatedUser authenticatedUser, Long restaurantId) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return;
        }

        boolean canManage = roleAssignmentRepository.findByUserId(authenticatedUser.userId()).stream()
            .anyMatch(assignment ->
                Objects.equals(assignment.getRestaurant().getId(), restaurantId)
                    && (assignment.getRole() == Role.RESTAURANT_OWNER || assignment.getRole() == Role.MANAGER)
            );

        if (!canManage) {
            throw new AccessDeniedException("Only PLATFORM_ADMIN, RESTAURANT_OWNER or MANAGER can manage planning");
        }
    }

    private User findUserOrNull(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }
}
