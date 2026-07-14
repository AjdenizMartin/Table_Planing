package com.restaurantplanner.storage.api;

public record StorageAvailabilityResponse(
    Long resourceId,
    Integer requestedQuantity,
    Integer availableQuantity,
    boolean available
) {
}
