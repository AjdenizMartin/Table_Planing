package com.restaurantplanner.table.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateRestaurantTableRequest(
    @NotNull Long diningRoomId,
    @NotBlank @Size(max = 80) String code,
    @Size(max = 160) String label,
    @NotNull @Min(1) Integer minCapacity,
    @NotNull @Min(1) Integer maxCapacity,
    @NotBlank @Size(max = 40) String shape,
    @NotNull @Min(0) @Max(10000) Integer x,
    @NotNull @Min(0) @Max(10000) Integer y,
    @NotNull @Min(20) @Max(5000) Integer width,
    @NotNull @Min(20) @Max(5000) Integer height,
    @NotNull Boolean active
) {
}

