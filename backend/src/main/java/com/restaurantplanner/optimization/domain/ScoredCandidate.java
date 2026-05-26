package com.restaurantplanner.optimization.domain;

import java.util.Map;

public record ScoredCandidate(
    AssignmentCandidate candidate,
    CandidateAvailability availability,
    double totalScore,
    Map<String, Double> bonuses,
    Map<String, Double> penalties
) {
}
