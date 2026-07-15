package com.restaurantplanner.optimization.api;

public record AssignmentSuggestionResourceResponse(
    Long storageResourceId,
    String resourceType,
    String resourceName,
    Integer requiredQuantity,
    Integer availableQuantity,
    Integer capacityPerUnit,
    Integer capacityContribution
) {
}
