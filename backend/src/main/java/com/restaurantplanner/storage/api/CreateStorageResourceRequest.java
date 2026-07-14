package com.restaurantplanner.storage.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreateStorageResourceRequest(
    @NotBlank @Size(max = 40) String resourceType,
    @NotBlank @Size(max = 160) String name,
    @NotNull @Min(0) Integer quantity,
    @Min(0) Integer capacityPerUnit,
    @Min(0) Integer setupTimeMinutes,
    @NotNull Boolean active,
    @Size(max = 2000) String notes
) {
}
