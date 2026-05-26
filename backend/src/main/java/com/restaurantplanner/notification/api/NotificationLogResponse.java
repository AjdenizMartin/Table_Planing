package com.restaurantplanner.notification.api;

import com.restaurantplanner.notification.domain.NotificationChannel;
import com.restaurantplanner.notification.domain.NotificationDeliveryStatus;
import com.restaurantplanner.notification.domain.NotificationTemplateCode;
import java.time.Instant;

public record NotificationLogResponse(
    Long id,
    Long restaurantId,
    Long reservationId,
    Long customerId,
    NotificationChannel channel,
    NotificationTemplateCode templateCode,
    NotificationDeliveryStatus status,
    String providerMessageId,
    String errorMessage,
    Instant sentAt,
    Instant createdAt,
    Instant updatedAt
) {
}
