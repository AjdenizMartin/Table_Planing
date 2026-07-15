package com.restaurantplanner.tablecombination.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.util.List;

public record UpdateTableCombinationRequest(
    @Size(min = 1, max = 160) String name,
    @Min(1) Integer minCapacity,
    @Min(1) Integer maxCapacity,
    Boolean active,
    @Size(min = 2) List<@Min(1) Long> tableIds,
    String combinationType,
    String operationalCostLevel,
    @Min(0) Integer setupTimeMinutes,
    @Size(max = 50) List<@jakarta.validation.Valid TableCombinationResourceRequirementRequest> resourceRequirements
) {
}
