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
    List<TableCombinationItemResponse> items,
    Instant createdAt,
    Instant updatedAt
) {
}
