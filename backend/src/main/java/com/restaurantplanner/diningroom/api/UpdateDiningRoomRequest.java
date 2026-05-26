package com.restaurantplanner.diningroom.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateDiningRoomRequest(
    @Size(min = 1, max = 160) String name,
    @Min(1) Integer priority,
    Boolean accessible,
    Boolean active,
    @Min(100) @Max(10000) Integer layoutWidth,
    @Min(100) @Max(10000) Integer layoutHeight
) {
}

