package com.restaurantplanner.optimization.api;

import java.util.List;

public record AssignReservationResponse(
    boolean assigned,
    Long reservationId,
    Long assignmentId,
    String assignmentType,
    Long diningRoomId,
    Long tableId,
    String tableCode,
    Long tableCombinationId,
    String tableCombinationName,
    Double score,
    String summary,
    String explanationJson,
    List<String> reasons,
    String recommendedStartTime,
    String recommendationSummary
) {
}
