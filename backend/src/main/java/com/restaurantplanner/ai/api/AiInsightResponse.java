package com.restaurantplanner.ai.api;

import com.restaurantplanner.ai.domain.AiInsightType;
import com.restaurantplanner.ai.domain.AiSeverity;
import java.time.Instant;
import java.time.LocalDate;

public record AiInsightResponse(
    Long id,
    Long restaurantId,
    LocalDate date,
    AiInsightType type,
    AiSeverity severity,
    String title,
    String description,
    String entityType,
    Long entityId,
    String metadataJson,
    boolean dismissed,
    Instant createdAt,
    Instant updatedAt
) {
}
