package com.restaurantplanner.notification.api;

import com.restaurantplanner.notification.domain.Notification;
import org.springframework.stereotype.Component;

@Component
public class NotificationMapper {

    public NotificationResponse toResponse(Notification notification) {
        return new NotificationResponse(
            notification.getId(),
            notification.getRestaurantId(),
            notification.getUserId(),
            notification.getType(),
            notification.getTitle(),
            notification.getBody(),
            notification.getEntityType(),
            notification.getEntityId(),
            notification.isRead(),
            notification.getCreatedAt()
        );
    }
}
