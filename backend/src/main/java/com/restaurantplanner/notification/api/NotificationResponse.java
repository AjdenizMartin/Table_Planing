package com.restaurantplanner.notification.api;

import com.restaurantplanner.notification.domain.NotificationType;
import java.time.Instant;

public record NotificationResponse(
    Long id,
    Long restaurantId,
    Long userId,
    NotificationType type,
    String title,
    String body,
    String entityType,
    Long entityId,
    boolean read,
    Instant createdAt
) {
}
