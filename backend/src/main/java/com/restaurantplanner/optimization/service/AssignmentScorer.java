package com.restaurantplanner.optimization.service;

import com.restaurantplanner.optimization.domain.AssignmentCandidate;
import com.restaurantplanner.optimization.domain.CandidateAvailability;
import com.restaurantplanner.optimization.domain.ScoredCandidate;
import com.restaurantplanner.reservation.domain.Reservation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AssignmentScorer {

    public ScoredCandidate score(
        AssignmentCandidate candidate,
        Reservation reservation,
        CandidateAvailability availability,
        List<AssignmentCandidate> validCandidates
    ) {
        int wastedCapacity = candidate.maxCapacity() - reservation.getPartySize();
        int smallestSufficientCapacity = validCandidates.stream().mapToInt(AssignmentCandidate::maxCapacity).min().orElse(candidate.maxCapacity());

        double capacityFit = Math.max(0, 40 - wastedCapacity * 6);
        double roomPriority = Math.max(0, 24 - (candidate.primaryRoomPriority() - 1) * 6);
        double futureFlexibility = Math.max(0, 16 - (candidate.maxCapacity() - smallestSufficientCapacity) * 3);
        double preferenceMatch = 0;
        double accessibilityMatch = reservation.isAccessibilityRequired() ? 14 : 0;

        double wastedCapacityPenalty = wastedCapacity * 7;
        double deadGapPenalty = deadGapPenalty(availability.gapBeforeMin()) + deadGapPenalty(availability.gapAfterMin());
        double largeTableBlockPenalty = largeTableBlockPenalty(reservation.getPartySize(), candidate.maxCapacity());
        double roomActivationPenalty = Math.max(0, (candidate.primaryRoomPriority() - 1) * 5);
        double combinationComplexityPenalty = Math.max(0, (candidate.tableCount() - 1) * 18);
        double operationalCostPenalty = candidate.advanced() ? candidate.operationalCostLevel().scorePenalty() : 0;
        double setupTimePenalty = candidate.advanced() ? Math.min(candidate.setupTimeMinutes() * 0.5, 30) : 0;

        Map<String, Double> bonuses = new LinkedHashMap<>();
        bonuses.put("capacity_fit", capacityFit);
        bonuses.put("room_priority", roomPriority);
        bonuses.put("future_flexibility", futureFlexibility);
        bonuses.put("preference_match", preferenceMatch);
        bonuses.put("accessibility_match", accessibilityMatch);

        Map<String, Double> penalties = new LinkedHashMap<>();
        penalties.put("wasted_capacity_penalty", wastedCapacityPenalty);
        penalties.put("dead_gap_penalty", deadGapPenalty);
        penalties.put("large_table_block_penalty", largeTableBlockPenalty);
        penalties.put("room_activation_penalty", roomActivationPenalty);
        penalties.put("combination_complexity_penalty", combinationComplexityPenalty);
        penalties.put("operational_cost_penalty", operationalCostPenalty);
        penalties.put("setup_time_penalty", setupTimePenalty);

        double total = bonuses.values().stream().mapToDouble(Double::doubleValue).sum()
            - penalties.values().stream().mapToDouble(Double::doubleValue).sum();

        return new ScoredCandidate(candidate, availability, total, bonuses, penalties);
    }

    private double deadGapPenalty(Integer gapMinutes) {
        if (gapMinutes == null) {
            return 0;
        }
        if (gapMinutes > 0 && gapMinutes < 30) {
            return 20;
        }
        if (gapMinutes >= 30 && gapMinutes < 60) {
            return 10;
        }
        return 0;
    }

    private double largeTableBlockPenalty(int partySize, int candidateCapacity) {
        if (partySize <= 2 && candidateCapacity >= 6) {
            return 25;
        }
        if (partySize <= 4 && candidateCapacity >= 8) {
            return 15;
        }
        return 0;
    }
}
