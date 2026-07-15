package com.restaurantplanner.tablecombination.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record TableCombinationResourceRequirementRequest(
    @NotNull @Min(1) Long storageResourceId,
    @NotNull @Min(1) Integer quantity
) {
}
