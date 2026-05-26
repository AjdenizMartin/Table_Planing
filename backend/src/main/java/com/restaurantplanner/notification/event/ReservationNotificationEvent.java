package com.restaurantplanner.notification.event;

import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationNotificationEvent(
    String eventType,
    Long restaurantId,
    Long reservationId,
    Long customerId,
    String customerEmail,
    String customerName,
    int partySize,
    LocalDate date,
    LocalTime startTime,
    Long userId
) {
}
