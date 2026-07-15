package com.restaurantplanner.optimization.api;

public record AssignedResourceResponse(
    Long storageResourceId,
    String resourceType,
    String resourceName,
    Integer quantity,
    Integer capacityPerUnit,
    Integer setupTimeMinutes
) {
}
