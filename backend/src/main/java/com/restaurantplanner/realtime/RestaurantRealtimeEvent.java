package com.restaurantplanner.realtime;

import java.time.Instant;
import java.time.LocalDate;

public record RestaurantRealtimeEvent(
    String type,
    Long restaurantId,
    Long reservationId,
    Long tableId,
    Long diningRoomId,
    LocalDate date,
    String message,
    Instant occurredAt
) {
}
