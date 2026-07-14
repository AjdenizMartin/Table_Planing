package com.restaurantplanner.table.api;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateRestaurantTableRequest(
    Long diningRoomId,
    String tableType,
    @Size(min = 1, max = 80) String code,
    @Size(max = 160) String label,
    @Min(1) Integer minCapacity,
    @Min(1) Integer maxCapacity,
    @Size(min = 1, max = 40) String shape,
    Boolean active
) {
}
