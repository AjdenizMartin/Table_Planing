package com.restaurantplanner.notification.api;

import com.restaurantplanner.notification.domain.NotificationLog;
import org.springframework.stereotype.Component;

@Component
public class NotificationLogMapper {

    public NotificationLogResponse toResponse(NotificationLog notificationLog) {
        return new NotificationLogResponse(
            notificationLog.getId(),
            notificationLog.getRestaurant().getId(),
            notificationLog.getReservation() == null ? null : notificationLog.getReservation().getId(),
            notificationLog.getCustomer() == null ? null : notificationLog.getCustomer().getId(),
            notificationLog.getChannel(),
            notificationLog.getTemplateCode(),
            notificationLog.getStatus(),
            notificationLog.getProviderMessageId(),
            notificationLog.getErrorMessage(),
            notificationLog.getSentAt(),
            notificationLog.getCreatedAt(),
            notificationLog.getUpdatedAt()
        );
    }
}
