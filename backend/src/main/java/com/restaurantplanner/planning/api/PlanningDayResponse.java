package com.restaurantplanner.planning.api;

import java.time.LocalDate;
import java.util.List;

public record PlanningDayResponse(
    LocalDate date,
    PlanningRestaurantSummaryResponse restaurant,
    List<PlanningDiningRoomResponse> diningRooms,
    List<PlanningReservationSummaryResponse> assignedReservations,
    List<PlanningReservationSummaryResponse> unassignedReservations,
    List<PlanningConflictResponse> conflicts,
    List<String> timeBlocks
) {
}
