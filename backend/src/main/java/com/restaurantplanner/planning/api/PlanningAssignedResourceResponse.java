package com.restaurantplanner.planning.api;

public record PlanningAssignedResourceResponse(
    Long storageResourceId,
    String resourceType,
    String resourceName,
    Integer quantity,
    Integer capacityPerUnit
) {
}
