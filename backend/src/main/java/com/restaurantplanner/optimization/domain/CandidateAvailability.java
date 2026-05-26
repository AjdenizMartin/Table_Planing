package com.restaurantplanner.optimization.domain;

import java.util.List;

public record CandidateAvailability(
    boolean available,
    List<String> rejectionReasons,
    Integer gapBeforeMin,
    Integer gapAfterMin
) {
}
