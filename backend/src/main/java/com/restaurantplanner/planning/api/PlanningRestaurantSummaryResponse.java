package com.restaurantplanner.planning.api;

public record PlanningRestaurantSummaryResponse(
    Long id,
    String name,
    String timezone
) {
}
