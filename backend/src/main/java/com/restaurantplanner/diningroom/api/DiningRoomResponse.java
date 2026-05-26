package com.restaurantplanner.diningroom.api;

import java.time.Instant;

public record DiningRoomResponse(
    Long id,
    Long restaurantId,
    String name,
    Integer priority,
    boolean accessible,
    boolean active,
    Integer layoutWidth,
    Integer layoutHeight,
    Instant createdAt,
    Instant updatedAt
) {
}

