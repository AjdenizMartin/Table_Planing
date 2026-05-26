package com.restaurantplanner.restaurant.api;

import com.restaurantplanner.restaurant.domain.RestaurantStatus;
import java.time.Instant;
import java.util.List;

public record RestaurantResponse(
    Long id,
    String name,
    String slug,
    String timezone,
    String phone,
    RestaurantStatus status,
    List<String> roles,
    Instant createdAt,
    Instant updatedAt
) {
}

