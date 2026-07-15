package com.restaurantplanner.optimization.api;

import java.util.List;
import java.util.Map;

public record AssignmentExplanationResponse(
    String summary,
    List<String> reasons,
    Map<String, Double> bonuses,
    Map<String, Double> penalties
) {
}
