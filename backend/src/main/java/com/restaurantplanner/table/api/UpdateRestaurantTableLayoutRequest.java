package com.restaurantplanner.table.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record UpdateRestaurantTableLayoutRequest(
    @NotNull @Min(0) @Max(10000) Integer x,
    @NotNull @Min(0) @Max(10000) Integer y,
    @NotNull @Min(20) @Max(5000) Integer width,
    @NotNull @Min(20) @Max(5000) Integer height
) {
}

