package com.restaurantplanner.realtime;

import com.restaurantplanner.notification.domain.Notification;
import java.time.Instant;
import java.time.LocalDate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

@Service
public class RestaurantRealtimePublisher {

    private final SimpMessagingTemplate messagingTemplate;

    public RestaurantRealtimePublisher(SimpMessagingTemplate messagingTemplate) {
        this.messagingTemplate = messagingTemplate;
    }

    public void publishReservationEvent(
        String type,
        Long restaurantId,
        Long reservationId,
        LocalDate date,
        String message
    ) {
        RestaurantRealtimeEvent event = new RestaurantRealtimeEvent(
            type,
            restaurantId,
            reservationId,
            null,
            null,
            date,
            message,
            Instant.now()
        );

        sendToReservationsTopic(restaurantId, event);
        sendToPlanningTopic(restaurantId, event);
    }

    public void publishTableUpdated(
        Long restaurantId,
        Long tableId,
        Long diningRoomId,
        String message
    ) {
        sendToPlanningTopic(
            restaurantId,
            new RestaurantRealtimeEvent(
                "table.updated",
                restaurantId,
                null,
                tableId,
                diningRoomId,
                null,
                message,
                Instant.now()
            )
        );
    }

    public void publishNotification(Long restaurantId, Notification notification) {
        RestaurantRealtimeEvent event = new RestaurantRealtimeEvent(
            "notification",
            restaurantId,
            null,
            null,
            null,
            null,
            notification.getTitle(),
            Instant.now()
        );
        messagingTemplate.convertAndSend("/topic/restaurants/" + restaurantId + "/notifications", event);
    }

    public void publishAiInsightsUpdated(Long restaurantId, LocalDate date, String message) {
        RestaurantRealtimeEvent event = new RestaurantRealtimeEvent(
            "ai.insights.updated",
            restaurantId,
            null,
            null,
            null,
            date,
            message,
            Instant.now()
        );
        messagingTemplate.convertAndSend("/topic/restaurants/" + restaurantId + "/ai", event);
    }

    public void publishPlanningRecalculated(Long restaurantId, LocalDate date, String message) {
        sendToPlanningTopic(
            restaurantId,
            new RestaurantRealtimeEvent(
                "planning.recalculated",
                restaurantId,
                null,
                null,
                null,
                date,
                message,
                Instant.now()
            )
        );
    }

    private void sendToReservationsTopic(Long restaurantId, RestaurantRealtimeEvent event) {
        messagingTemplate.convertAndSend("/topic/restaurants/" + restaurantId + "/reservations", event);
    }

    private void sendToPlanningTopic(Long restaurantId, RestaurantRealtimeEvent event) {
        messagingTemplate.convertAndSend("/topic/restaurants/" + restaurantId + "/planning", event);
    }
}
