package com.restaurantplanner.auth.api;

import java.util.List;

public record RestaurantAccessResponse(
    Long id,
    String name,
    String slug,
    List<String> roles
) {
}

