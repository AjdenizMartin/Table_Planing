package com.restaurantplanner.storage.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

public record UpdateStorageResourceRequest(
    @Size(min = 1, max = 40) String resourceType,
    @Size(min = 1, max = 160) String name,
    @Min(0) Integer quantity,
    @Min(0) Integer capacityPerUnit,
    @Min(0) Integer setupTimeMinutes,
    Boolean active,
    @Size(max = 2000) String notes
) {
}
