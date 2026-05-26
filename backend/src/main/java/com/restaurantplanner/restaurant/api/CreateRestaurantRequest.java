package com.restaurantplanner.restaurant.api;

import com.restaurantplanner.restaurant.domain.RestaurantStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRestaurantRequest(
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 160) String slug,
    @NotBlank @Size(max = 80) String timezone,
    @Size(max = 40) String phone,
    @NotNull RestaurantStatus status
) {
}

