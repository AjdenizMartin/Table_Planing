package com.restaurantplanner.planning.api;

import java.util.List;

public record PlanningTableResponse(
    Long id,
    String code,
    String label,
    Integer minCapacity,
    Integer maxCapacity,
    boolean active,
    Integer x,
    Integer y,
    Integer width,
    Integer height,
    List<PlanningReservationSummaryResponse> reservations
) {
}
