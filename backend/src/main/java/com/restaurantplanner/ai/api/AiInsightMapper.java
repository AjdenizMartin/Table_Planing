package com.restaurantplanner.ai.api;

import com.restaurantplanner.ai.domain.AiInsight;
import org.springframework.stereotype.Component;

@Component
public class AiInsightMapper {

    public AiInsightResponse toResponse(AiInsight insight) {
        return new AiInsightResponse(
            insight.getId(),
            insight.getRestaurant().getId(),
            insight.getDate(),
            insight.getType(),
            insight.getSeverity(),
            insight.getTitle(),
            insight.getDescription(),
            insight.getEntityType(),
            insight.getEntityId(),
            insight.getMetadataJson(),
            insight.isDismissed(),
            insight.getCreatedAt(),
            insight.getUpdatedAt()
        );
    }
}
