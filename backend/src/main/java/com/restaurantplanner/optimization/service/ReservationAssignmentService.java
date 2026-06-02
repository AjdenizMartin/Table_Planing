package com.restaurantplanner.optimization.service;

import com.restaurantplanner.ai.service.AiService;
import com.restaurantplanner.audit.AuditService;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.common.api.ConflictException;
import com.restaurantplanner.common.api.NotFoundException;
import com.restaurantplanner.optimization.api.AssignReservationResponse;
import com.restaurantplanner.optimization.domain.AssignmentCandidate;
import com.restaurantplanner.optimization.domain.AssignmentCandidateType;
import com.restaurantplanner.optimization.domain.AssignmentExplanation;
import com.restaurantplanner.optimization.domain.CandidateAvailability;
import com.restaurantplanner.optimization.domain.ScoredCandidate;
import com.restaurantplanner.planning.service.PlanningService;
import com.restaurantplanner.realtime.RestaurantRealtimePublisher;
import com.restaurantplanner.reservation.domain.Reservation;
import com.restaurantplanner.reservation.domain.ReservationAssignment;
import com.restaurantplanner.reservation.domain.ReservationAssignmentRepository;
import com.restaurantplanner.reservation.domain.ReservationRepository;
import com.restaurantplanner.reservation.domain.ReservationStatus;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.user.domain.User;
import com.restaurantplanner.user.domain.UserRepository;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.context.annotation.Lazy;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReservationAssignmentService {

    private static final String TABLE_ASSIGNMENT = "TABLE";
    private static final String TABLE_COMBINATION_ASSIGNMENT = "TABLE_COMBINATION";

    private final CandidateFinder candidateFinder;
    private final AvailabilityChecker availabilityChecker;
    private final AssignmentScorer assignmentScorer;
    private final AssignmentExplainer assignmentExplainer;
    private final ReservationRepository reservationRepository;
    private final ReservationAssignmentRepository reservationAssignmentRepository;
    private final RestaurantRepository restaurantRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final UserRepository userRepository;
    private final RestaurantRealtimePublisher realtimePublisher;
    private final AuditService auditService;
    private final AiService aiService;
    private final PlanningService planningService;

    public ReservationAssignmentService(
        CandidateFinder candidateFinder,
        AvailabilityChecker availabilityChecker,
        AssignmentScorer assignmentScorer,
        AssignmentExplainer assignmentExplainer,
        ReservationRepository reservationRepository,
        ReservationAssignmentRepository reservationAssignmentRepository,
        RestaurantRepository restaurantRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        UserRepository userRepository,
        RestaurantRealtimePublisher realtimePublisher,
        AuditService auditService,
        AiService aiService,
        @Lazy PlanningService planningService
    ) {
        this.candidateFinder = candidateFinder;
        this.availabilityChecker = availabilityChecker;
        this.assignmentScorer = assignmentScorer;
        this.assignmentExplainer = assignmentExplainer;
        this.reservationRepository = reservationRepository;
        this.reservationAssignmentRepository = reservationAssignmentRepository;
        this.restaurantRepository = restaurantRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.userRepository = userRepository;
        this.realtimePublisher = realtimePublisher;
        this.auditService = auditService;
        this.aiService = aiService;
        this.planningService = planningService;
    }

    @Transactional
    public AssignReservationResponse assign(Long restaurantId, Long reservationId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        Reservation reservation = findReservationOrThrow(restaurantId, reservationId);
        if (isTerminalStatus(reservation.getStatus())) {
            throw new ConflictException("Terminal reservations cannot be assigned");
        }

        List<AssignmentCandidate> candidates = candidateFinder.findCandidates(restaurantId);
        if (candidates.isEmpty()) {
            return noAssignment(
                reservation,
                List.of("No active tables or combinations are configured for this restaurant"),
                candidates,
                List.of()
            );
        }

        List<ReservationAssignment> occupiedAssignments = reservationAssignmentRepository
            .findByActiveTrueAndReservationRestaurantIdAndReservationReservationDateAndReservationStatusIn(
                restaurantId,
                reservation.getReservationDate(),
                EnumSet.of(ReservationStatus.PENDING, ReservationStatus.CONFIRMED, ReservationStatus.SEATED)
            );

        Map<String, Integer> rejectionCounts = new LinkedHashMap<>();
        List<AssignmentCandidate> validCandidates = new ArrayList<>();
        Map<AssignmentCandidate, CandidateAvailability> availabilityByCandidate = new LinkedHashMap<>();

        for (AssignmentCandidate candidate : candidates) {
            CandidateAvailability availability = availabilityChecker.evaluate(candidate, reservation, occupiedAssignments);
            if (availability.available()) {
                validCandidates.add(candidate);
                availabilityByCandidate.put(candidate, availability);
            } else {
                for (String reason : availability.rejectionReasons()) {
                    rejectionCounts.merge(reason, 1, Integer::sum);
                }
            }
        }

        if (validCandidates.isEmpty()) {
            return noAssignment(
                reservation,
                buildNoCandidateReasons(rejectionCounts),
                candidates,
                occupiedAssignments
            );
        }

        List<ScoredCandidate> scoredCandidates = validCandidates.stream()
            .map(candidate -> assignmentScorer.score(candidate, reservation, availabilityByCandidate.get(candidate), validCandidates))
            .sorted(candidateComparator())
            .toList();

        ScoredCandidate best = scoredCandidates.get(0);
        AssignmentExplanation explanation = assignmentExplainer.explain(best, reservation);

        deactivateCurrentAssignments(reservation.getId());

        ReservationAssignment assignment = new ReservationAssignment();
        assignment.setReservation(reservation);
        assignment.setAssignmentType(best.candidate().type() == AssignmentCandidateType.TABLE
            ? TABLE_ASSIGNMENT
            : TABLE_COMBINATION_ASSIGNMENT);
        assignment.setDiningRoom(best.candidate().diningRooms().size() == 1 ? best.candidate().diningRooms().get(0) : null);
        assignment.setTable(best.candidate().table());
        assignment.setTableCombination(best.candidate().tableCombination());
        assignment.setScore(best.totalScore());
        assignment.setExplanationJson(explanation.explanationJson());
        assignment.setAssignedBy(findUserOrNull(authenticatedUser.userId()));
        assignment.setAssignedAt(Instant.now());
        assignment.setActive(true);

        ReservationAssignment saved = reservationAssignmentRepository.save(assignment);
        aiService.generateInsightsForDate(
            restaurantId,
            reservation.getReservationDate(),
            planningService.getPlanningDayInternal(restaurantId, reservation.getReservationDate())
        );
        auditService.record(restaurantId, "Reservation", reservation.getId(), "reservation.assigned", authenticatedUser.userId(), explanation.explanationJson());
        realtimePublisher.publishReservationEvent(
            "reservation.assigned",
            restaurantId,
            reservation.getId(),
            reservation.getReservationDate(),
            "Reservation assigned automatically"
        );

        return new AssignReservationResponse(
            true,
            reservation.getId(),
            saved.getId(),
            saved.getAssignmentType(),
            saved.getDiningRoom() == null ? null : saved.getDiningRoom().getId(),
            saved.getTable() == null ? null : saved.getTable().getId(),
            saved.getTable() == null ? null : saved.getTable().getCode(),
            saved.getTableCombination() == null ? null : saved.getTableCombination().getId(),
            saved.getTableCombination() == null ? null : saved.getTableCombination().getName(),
            saved.getScore(),
            explanation.summary(),
            explanation.explanationJson(),
            List.of(),
            null,
            null
        );
    }

    private Comparator<ScoredCandidate> candidateComparator() {
        return Comparator.comparingDouble(ScoredCandidate::totalScore).reversed()
            .thenComparing(scored -> scored.candidate().type() == AssignmentCandidateType.TABLE ? 0 : 1)
            .thenComparing(scored -> scored.candidate().maxCapacity())
            .thenComparing(scored -> scored.candidate().primaryRoomPriority())
            .thenComparing(scored -> scored.candidate().displayName())
            .thenComparing(scored -> scored.candidate().table() != null ? scored.candidate().table().getId() : Long.MAX_VALUE)
            .thenComparing(scored -> scored.candidate().tableCombination() != null
                ? scored.candidate().tableCombination().getId()
                : Long.MAX_VALUE);
    }

    private List<String> buildNoCandidateReasons(Map<String, Integer> rejectionCounts) {
        if (rejectionCounts.isEmpty()) {
            return List.of("No candidate satisfied the hard constraints");
        }

        List<String> reasons = new ArrayList<>();
        rejectionCounts.forEach((reason, count) -> reasons.add(count + " candidate(s) rejected due to " + reason));
        return reasons;
    }

    private void deactivateCurrentAssignments(Long reservationId) {
        for (ReservationAssignment existingAssignment : reservationAssignmentRepository.findByReservationIdAndActiveTrue(reservationId)) {
            existingAssignment.setActive(false);
        }
    }

    private Restaurant findAccessibleRestaurantOrThrow(Long restaurantId, AuthenticatedUser authenticatedUser) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant not found"));
        }

        return restaurantRepository.findAccessibleByIdAndUserId(restaurantId, authenticatedUser.userId())
            .orElseThrow(() -> new NotFoundException("Restaurant not found"));
    }

    private Reservation findReservationOrThrow(Long restaurantId, Long reservationId) {
        return reservationRepository.findByIdAndRestaurantId(reservationId, restaurantId)
            .orElseThrow(() -> new NotFoundException("Reservation not found"));
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
            throw new AccessDeniedException("Only PLATFORM_ADMIN, RESTAURANT_OWNER or MANAGER can assign reservations");
        }
    }

    private User findUserOrNull(Long userId) {
        return userRepository.findById(userId).orElse(null);
    }

    private boolean isTerminalStatus(ReservationStatus status) {
        return status == ReservationStatus.CANCELLED || status == ReservationStatus.COMPLETED || status == ReservationStatus.NO_SHOW;
    }

    private AssignReservationResponse noAssignment(
        Reservation reservation,
        List<String> reasons,
        List<AssignmentCandidate> candidates,
        List<ReservationAssignment> occupiedAssignments
    ) {
        LocalTime recommendedStart = findNextAvailableStartTime(reservation, candidates, occupiedAssignments);
        String recommendationSummary = recommendedStart == null
            ? "No same-day table option was found after the requested time. Try another date, reduce party size, or create/activate more table combinations."
            : "Nearest available table option is at " + recommendedStart + ". The reservation time was not changed automatically.";

        return new AssignReservationResponse(
            false,
            reservation.getId(),
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            null,
            reasons,
            recommendedStart == null ? null : recommendedStart.toString(),
            recommendationSummary
        );
    }

    private LocalTime findNextAvailableStartTime(
        Reservation reservation,
        List<AssignmentCandidate> candidates,
        List<ReservationAssignment> occupiedAssignments
    ) {
        if (candidates.isEmpty()) {
            return null;
        }

        int durationWithCleaning = reservation.getEstimatedDurationMin() + reservation.getCleaningBufferMin();
        int requestedStart = reservation.getStartTime().getHour() * 60 + reservation.getStartTime().getMinute();
        int latestStart = (24 * 60) - durationWithCleaning;

        for (int proposedStart = roundUpToNextQuarter(requestedStart + 1); proposedStart <= latestStart; proposedStart += 15) {
            int candidateStart = proposedStart;
            if (candidates.stream().anyMatch(candidate -> canCandidateFitAt(candidate, reservation, occupiedAssignments, candidateStart))) {
                return LocalTime.of(proposedStart / 60, proposedStart % 60);
            }
        }

        return null;
    }

    private int roundUpToNextQuarter(int minutes) {
        return ((minutes + 14) / 15) * 15;
    }

    private boolean canCandidateFitAt(
        AssignmentCandidate candidate,
        Reservation reservation,
        List<ReservationAssignment> occupiedAssignments,
        int proposedStartMinutes
    ) {
        if (candidate.maxCapacity() < reservation.getPartySize()) {
            return false;
        }

        if (candidate.minCapacity() > reservation.getPartySize()) {
            return false;
        }

        if (candidate.type() == AssignmentCandidateType.TABLE && (candidate.table() == null || !candidate.table().isActive())) {
            return false;
        }

        if (candidate.type() == AssignmentCandidateType.TABLE_COMBINATION
            && (candidate.tableCombination() == null || !candidate.tableCombination().isActive())) {
            return false;
        }

        if (candidate.tables().stream().anyMatch(table -> !table.isActive())) {
            return false;
        }

        if (!candidate.allDiningRoomsActive()) {
            return false;
        }

        if (reservation.isAccessibilityRequired() && !candidate.allDiningRoomsAccessible()) {
            return false;
        }

        int proposedEffectiveEnd = proposedStartMinutes + reservation.getEstimatedDurationMin() + reservation.getCleaningBufferMin();
        Set<Long> candidateTableIds = Set.copyOf(candidate.tableIds());

        for (ReservationAssignment assignment : occupiedAssignments) {
            if (assignment.getReservation().getId().equals(reservation.getId())) {
                continue;
            }

            Set<Long> occupiedTableIds = extractOccupiedTableIds(assignment);
            boolean sharesResource = occupiedTableIds.stream().anyMatch(candidateTableIds::contains);
            if (!sharesResource) {
                continue;
            }

            int occupiedStart = toMinutes(assignment.getReservation().getStartTime());
            int occupiedEffectiveEnd = toMinutes(assignment.getReservation().getEndTime())
                + assignment.getReservation().getCleaningBufferMin();

            boolean overlaps = proposedStartMinutes < occupiedEffectiveEnd && proposedEffectiveEnd > occupiedStart;
            if (overlaps) {
                return false;
            }
        }

        return true;
    }

    private int toMinutes(LocalTime time) {
        return (int) Duration.between(LocalTime.MIDNIGHT, time).toMinutes();
    }

    private Set<Long> extractOccupiedTableIds(ReservationAssignment assignment) {
        if (assignment.getTable() != null) {
            return Set.of(assignment.getTable().getId());
        }

        if (assignment.getTableCombination() != null) {
            return assignment.getTableCombination().getItems().stream()
                .map(item -> item.getTable().getId())
                .collect(java.util.stream.Collectors.toSet());
        }

        return Set.of();
    }
}
