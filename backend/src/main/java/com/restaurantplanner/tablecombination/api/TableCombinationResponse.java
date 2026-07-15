package com.restaurantplanner.tablecombination.api;

import java.time.Instant;
import java.util.List;

public record TableCombinationResponse(
    Long id,
    Long restaurantId,
    String name,
    Integer minCapacity,
    Integer maxCapacity,
    boolean active,
    String combinationType,
    String operationalCostLevel,
    Integer setupTimeMinutes,
    List<TableCombinationItemResponse> items,
    List<TableCombinationResourceRequirementResponse> resourceRequirements,
    Instant createdAt,
    Instant updatedAt
) {
}
