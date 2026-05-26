package com.restaurantplanner.notification.service;

import com.restaurantplanner.notification.domain.NotificationTemplateCode;

public record NotificationSendCommand(
    Long restaurantId,
    Long reservationId,
    Long customerId,
    String recipientPhone,
    String messageBody,
    NotificationTemplateCode templateCode
) {
}
