package com.restaurantplanner.planning.api;

public record MoveReservationRequest(
    Long reservationId,
    Long tableId,
    Long tableCombinationId
) {
}
