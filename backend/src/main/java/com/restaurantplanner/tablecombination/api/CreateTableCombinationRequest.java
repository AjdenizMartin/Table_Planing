package com.restaurantplanner.tablecombination.api;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateTableCombinationRequest(
    @NotBlank @Size(max = 160) String name,
    @NotNull @Min(1) Integer minCapacity,
    @NotNull @Min(1) Integer maxCapacity,
    @NotNull Boolean active,
    @NotEmpty @Size(min = 2) List<@NotNull Long> tableIds,
    String combinationType,
    String operationalCostLevel,
    @Min(0) Integer setupTimeMinutes,
    @Size(max = 50) List<@NotNull @jakarta.validation.Valid TableCombinationResourceRequirementRequest> resourceRequirements
) {
}
