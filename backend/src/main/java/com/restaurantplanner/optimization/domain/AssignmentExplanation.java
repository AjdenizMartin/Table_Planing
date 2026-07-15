package com.restaurantplanner.optimization.domain;

import java.util.List;

public record AssignmentExplanation(
    String summary,
    String explanationJson,
    List<String> reasons
) {
    public AssignmentExplanation(String summary, String explanationJson) {
        this(summary, explanationJson, List.of());
    }
}
