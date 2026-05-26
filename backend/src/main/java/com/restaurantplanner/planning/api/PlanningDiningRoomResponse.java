package com.restaurantplanner.planning.api;

import java.util.List;

public record PlanningDiningRoomResponse(
    Long id,
    String name,
    Integer priority,
    boolean accessible,
    boolean active,
    Integer layoutWidth,
    Integer layoutHeight,
    List<PlanningTableResponse> tables
) {
}
