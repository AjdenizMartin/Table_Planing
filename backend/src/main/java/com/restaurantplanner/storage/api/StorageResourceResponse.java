package com.restaurantplanner.storage.api;

import java.time.Instant;

public record StorageResourceResponse(
    Long id,
    Long restaurantId,
    String resourceType,
    String name,
    Integer quantity,
    Integer capacityPerUnit,
    Integer setupTimeMinutes,
    boolean active,
    String notes,
    Instant createdAt,
    Instant updatedAt
) {
}
