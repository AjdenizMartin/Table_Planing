package com.restaurantplanner.optimization.api;

import java.util.List;

public record AssignmentSuggestionResponse(
    String candidateType,
    Long candidateId,
    String displayName,
    List<Long> tableIds,
    Integer minCapacity,
    Integer maxCapacity,
    Double score,
    boolean advanced,
    String operationalCostLevel,
    Integer setupTimeMinutes,
    List<AssignmentSuggestionResourceResponse> resources,
    AssignmentExplanationResponse explanation
) {
}
