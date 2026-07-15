package com.restaurantplanner.optimization.api;

import java.time.Instant;
import java.util.List;

public record AssignmentHistoryItemResponse(
    Long assignmentId,
    boolean active,
    String assignmentType,
    Long tableId,
    String tableCode,
    Long tableCombinationId,
    String tableCombinationName,
    Double score,
    String operationalCostLevel,
    Integer setupTimeMinutes,
    Long assignedByUserId,
    String assignedByName,
    Instant assignedAt,
    String explanationJson,
    List<AssignedResourceResponse> resources
) {
}
