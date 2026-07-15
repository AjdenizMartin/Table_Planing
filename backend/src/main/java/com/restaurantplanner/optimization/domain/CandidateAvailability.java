package com.restaurantplanner.optimization.domain;

import java.util.List;
import java.util.Map;

public record CandidateAvailability(
    boolean available,
    List<String> rejectionReasons,
    Integer gapBeforeMin,
    Integer gapAfterMin,
    Map<Long, Integer> availableResourceQuantities
) {
    public CandidateAvailability(
        boolean available,
        List<String> rejectionReasons,
        Integer gapBeforeMin,
        Integer gapAfterMin
    ) {
        this(available, rejectionReasons, gapBeforeMin, gapAfterMin, Map.of());
    }
}
