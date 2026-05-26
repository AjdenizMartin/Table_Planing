package com.restaurantplanner.table.api;

import java.time.Instant;

public record RestaurantTableResponse(
    Long id,
    Long restaurantId,
    Long diningRoomId,
    String code,
    String label,
    Integer minCapacity,
    Integer maxCapacity,
    String shape,
    Integer x,
    Integer y,
    Integer width,
    Integer height,
    boolean active,
    Instant createdAt,
    Instant updatedAt
) {
}

