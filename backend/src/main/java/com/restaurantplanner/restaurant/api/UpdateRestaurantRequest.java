package com.restaurantplanner.restaurant.api;

import com.restaurantplanner.restaurant.domain.RestaurantStatus;
import jakarta.validation.constraints.Size;

public record UpdateRestaurantRequest(
    @Size(min = 1, max = 160) String name,
    @Size(min = 1, max = 160) String slug,
    @Size(min = 1, max = 80) String timezone,
    @Size(max = 40) String phone,
    RestaurantStatus status
) {
}

