package com.restaurantplanner.tablecombination.api;

public record TableCombinationResourceRequirementResponse(
    Long id,
    Long storageResourceId,
    String resourceType,
    String resourceName,
    Integer quantity,
    Integer capacityPerUnit,
    Integer capacityContribution,
    Integer resourceSetupTimeMinutes
) {
}
