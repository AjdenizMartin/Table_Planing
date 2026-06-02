package com.restaurantplanner.ai.service;

import com.restaurantplanner.ai.domain.AiInsight;
import com.restaurantplanner.ai.domain.AiInsightRepository;
import com.restaurantplanner.ai.domain.AiSeverity;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.common.api.NotFoundException;
import com.restaurantplanner.planning.api.PlanningDayResponse;
import com.restaurantplanner.realtime.RestaurantRealtimePublisher;
import com.restaurantplanner.reservation.domain.ReservationStatus;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AiService {

    static final Set<ReservationStatus> ACTIVE_OPERATIONAL_STATUSES = Set.of(
        ReservationStatus.PENDING,
        ReservationStatus.CONFIRMED,
        ReservationStatus.SEATED
    );

    private final AiInsightRepository aiInsightRepository;
    private final RestaurantRepository restaurantRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final InsightGenerator insightGenerator;
    private final RestaurantRealtimePublisher realtimePublisher;

    public AiService(
        AiInsightRepository aiInsightRepository,
        RestaurantRepository restaurantRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        InsightGenerator insightGenerator,
        RestaurantRealtimePublisher realtimePublisher
    ) {
        this.aiInsightRepository = aiInsightRepository;
        this.restaurantRepository = restaurantRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.insightGenerator = insightGenerator;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional
    public List<AiInsight> generateInsightsForDate(
        Long restaurantId,
        LocalDate date,
        PlanningDayResponse planningDay
    ) {
        Restaurant restaurant = restaurantRepository.findById(restaurantId)
            .orElseThrow(() -> new NotFoundException("Restaurant not found"));

        Set<String> dismissedInsightKeys = aiInsightRepository
            .findByRestaurantIdAndDateOrderByDismissedAscSeverityDescCreatedAtDesc(restaurantId, date)
            .stream()
            .filter(AiInsight::isDismissed)
            .map(this::insightKey)
            .collect(java.util.stream.Collectors.toSet());

        aiInsightRepository.deleteByRestaurantIdAndDate(restaurantId, date);
        List<AiInsight> generated = insightGenerator.generate(restaurant, date, planningDay);
        generated.forEach(insight -> insight.setDismissed(dismissedInsightKeys.contains(insightKey(insight))));
        List<AiInsight> saved = aiInsightRepository.saveAll(generated);
        realtimePublisher.publishAiInsightsUpdated(
            restaurantId,
            date,
            "AI insights updated for planning day"
        );
        return saved;
    }

    @Transactional(readOnly = true)
    public List<AiInsight> findByRestaurantIdAndDate(
        Long restaurantId,
        LocalDate date,
        AuthenticatedUser authenticatedUser
    ) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        return aiInsightRepository.findByRestaurantIdAndDateOrderByDismissedAscSeverityDescCreatedAtDesc(restaurantId, date)
            .stream()
            .sorted(
                Comparator.comparing(AiInsight::isDismissed)
                    .thenComparing((AiInsight insight) -> severityRank(insight.getSeverity()))
                    .thenComparing(AiInsight::getCreatedAt, Comparator.reverseOrder())
            )
            .toList();
    }

    @Transactional(readOnly = true)
    public Map<String, Long> summaryBySeverity(
        Long restaurantId,
        LocalDate date,
        AuthenticatedUser authenticatedUser
    ) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        Map<String, Long> summary = new LinkedHashMap<>();
        for (AiSeverity severity : AiSeverity.values()) {
            summary.put(severity.name(), 0L);
        }
        aiInsightRepository.countActiveBySeverity(restaurantId, date)
            .forEach(count -> summary.put(count.getSeverity().name(), count.getTotal()));
        return summary;
    }

    @Transactional
    public AiInsight dismissInsight(Long restaurantId, Long insightId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        AiInsight insight = aiInsightRepository.findByIdAndRestaurantId(insightId, restaurantId)
            .orElseThrow(() -> new NotFoundException("AI insight not found"));
        insight.setDismissed(true);
        realtimePublisher.publishAiInsightsUpdated(
            restaurantId,
            insight.getDate(),
            "AI insight dismissed"
        );
        return insight;
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
            throw new AccessDeniedException("Only PLATFORM_ADMIN, RESTAURANT_OWNER or MANAGER can manage AI insights");
        }
    }

    private int severityRank(AiSeverity severity) {
        return switch (severity) {
            case HIGH -> 0;
            case MEDIUM -> 1;
            case LOW -> 2;
        };
    }

    private String insightKey(AiInsight insight) {
        return insight.getType() + "|" + insight.getEntityType() + "|" + insight.getEntityId() + "|" + insight.getTitle();
    }
}
