package com.restaurantplanner.planning.service;

import com.restaurantplanner.ai.service.AiService;
import com.restaurantplanner.audit.AuditService;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.common.api.ConflictException;
import com.restaurantplanner.common.api.NotFoundException;
import com.restaurantplanner.optimization.domain.AssignmentCandidate;
import com.restaurantplanner.optimization.domain.AssignmentCandidateType;
import com.restaurantplanner.optimization.domain.CandidateAvailability;
import com.restaurantplanner.optimization.api.AssignmentSelectionRequest;
import com.restaurantplanner.optimization.service.AvailabilityChecker;
import com.restaurantplanner.optimization.service.ReservationAssignmentService;
import com.restaurantplanner.planning.api.MoveReservationRequest;
import com.restaurantplanner.planning.api.PlanningDayResponse;
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
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PlanningService {

    private static final Set<ReservationStatus> ASSIGNABLE_STATUSES = EnumSet.of(
        ReservationStatus.PENDING,
        ReservationStatus.CONFIRMED,
        ReservationStatus.ARRIVED,
        ReservationStatus.SEATED
    );

    private final RestaurantRepository restaurantRepository;
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
    private final PlanningSnapshotService planningSnapshotService;

    public PlanningService(
        RestaurantRepository restaurantRepository,
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
        AiService aiService,
        PlanningSnapshotService planningSnapshotService
    ) {
        this.restaurantRepository = restaurantRepository;
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
        this.planningSnapshotService = planningSnapshotService;
    }

    @Transactional(readOnly = true)
    public PlanningDayResponse getPlanningDay(Long restaurantId, LocalDate date, AuthenticatedUser authenticatedUser) {
        Restaurant restaurant = findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        return planningSnapshotService.build(restaurant, date);
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

        PlanningDayResponse planningDay = planningSnapshotService.build(restaurant, date);
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

        reservationAssignmentService.select(
            restaurantId,
            reservation.getId(),
            new AssignmentSelectionRequest(
                hasTable ? "TABLE" : "TABLE_COMBINATION",
                hasTable ? request.tableId() : request.tableCombinationId()
            ),
            authenticatedUser
        );
        PlanningDayResponse planningDay = planningSnapshotService.build(restaurant, reservation.getReservationDate());
        realtimePublisher.publishPlanningRecalculated(
            restaurantId,
            reservation.getReservationDate(),
            "Planning updated after manual move"
        );
        return planningDay;
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
