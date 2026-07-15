package com.restaurantplanner.storage.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record StorageAvailabilityRequest(
    @NotNull @Min(0) Integer requestedQuantity
) {
}
