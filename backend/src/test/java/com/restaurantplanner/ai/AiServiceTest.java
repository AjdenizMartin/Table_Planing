package com.restaurantplanner.ai;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurantplanner.ai.domain.AiInsight;
import com.restaurantplanner.ai.domain.AiInsightRepository;
import com.restaurantplanner.ai.domain.AiInsightType;
import com.restaurantplanner.ai.domain.AiSeverity;
import com.restaurantplanner.ai.service.AiService;
import com.restaurantplanner.ai.service.InsightGenerator;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.planning.api.PlanningDayResponse;
import com.restaurantplanner.realtime.RestaurantRealtimePublisher;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AiServiceTest {

    @Test
    void generateInsightsPreservesDismissedStateForSameLogicalInsight() {
        AiInsightRepository insightRepository = org.mockito.Mockito.mock(AiInsightRepository.class);
        RestaurantRepository restaurantRepository = org.mockito.Mockito.mock(RestaurantRepository.class);
        StubInsightGenerator insightGenerator = new StubInsightGenerator();
        CapturingRealtimePublisher realtimePublisher = new CapturingRealtimePublisher();
        AiService service = new AiService(
            insightRepository,
            restaurantRepository,
            org.mockito.Mockito.mock(RoleAssignmentRepository.class),
            insightGenerator,
            realtimePublisher
        );
        LocalDate date = LocalDate.of(2026, 6, 2);
        Restaurant restaurant = restaurant(1L);
        AiInsight dismissed = insight(AiInsightType.CAPACITY_UNDERUTILIZED, "Reservation", 20L, "Underused capacity", true);
        AiInsight regenerated = insight(AiInsightType.CAPACITY_UNDERUTILIZED, "Reservation", 20L, "Underused capacity", false);
        AiInsight newInsight = insight(AiInsightType.DEAD_GAP_OPPORTUNITY, "RestaurantTable", 5L, "Reusable scheduling gap", false);

        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(restaurant));
        when(insightRepository.findByRestaurantIdAndDateOrderByDismissedAscSeverityDescCreatedAtDesc(1L, date)).thenReturn(List.of(dismissed));
        insightGenerator.insights = List.of(regenerated, newInsight);
        when(insightRepository.saveAll(List.of(regenerated, newInsight))).thenReturn(List.of(regenerated, newInsight));

        List<AiInsight> saved = service.generateInsightsForDate(1L, date, emptyPlanningDay(date));

        assertTrue(saved.get(0).isDismissed());
        assertFalse(saved.get(1).isDismissed());
        verify(insightRepository).deleteByRestaurantIdAndDate(1L, date);
        assertTrue(realtimePublisher.published);
    }

    private PlanningDayResponse emptyPlanningDay(LocalDate date) {
        return new PlanningDayResponse(date, null, List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private Restaurant restaurant(Long id) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(id);
        restaurant.setName("Main");
        restaurant.setTimezone("Europe/Madrid");
        return restaurant;
    }

    private AiInsight insight(AiInsightType type, String entityType, Long entityId, String title, boolean dismissed) {
        AiInsight insight = new AiInsight();
        insight.setType(type);
        insight.setSeverity(AiSeverity.MEDIUM);
        insight.setTitle(title);
        insight.setDescription(title);
        insight.setEntityType(entityType);
        insight.setEntityId(entityId);
        insight.setDismissed(dismissed);
        return insight;
    }

    private static class StubInsightGenerator extends InsightGenerator {

        private List<AiInsight> insights = List.of();

        StubInsightGenerator() {
            super(null, null, null, null, null, null);
        }

        @Override
        public List<AiInsight> generate(Restaurant restaurant, LocalDate date, PlanningDayResponse planningDay) {
            return insights;
        }
    }

    private static class CapturingRealtimePublisher extends RestaurantRealtimePublisher {

        private boolean published;

        CapturingRealtimePublisher() {
            super(null);
        }

        @Override
        public void publishAiInsightsUpdated(Long restaurantId, LocalDate date, String message) {
            published = true;
        }
    }
}
