package com.restaurantplanner.planning.api;

import java.time.LocalTime;
import java.util.List;

public record PlanningConflictResponse(
    String type,
    String resourceType,
    Long resourceId,
    String resourceLabel,
    List<Long> reservationIds,
    LocalTime overlappingStart,
    LocalTime overlappingEnd,
    String message
) {
}
