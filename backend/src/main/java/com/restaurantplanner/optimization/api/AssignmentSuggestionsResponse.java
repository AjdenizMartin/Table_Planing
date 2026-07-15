package com.restaurantplanner.optimization.api;

import java.util.List;

public record AssignmentSuggestionsResponse(
    Long reservationId,
    List<AssignmentSuggestionResponse> suggestions,
    List<String> rejectionReasons
) {
}
