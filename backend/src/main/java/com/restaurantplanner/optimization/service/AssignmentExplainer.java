package com.restaurantplanner.optimization.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantplanner.optimization.domain.AssignmentExplanation;
import com.restaurantplanner.optimization.domain.ScoredCandidate;
import com.restaurantplanner.reservation.domain.Reservation;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class AssignmentExplainer {

    private final ObjectMapper objectMapper;

    public AssignmentExplainer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AssignmentExplanation explain(ScoredCandidate scoredCandidate, Reservation reservation) {
        List<String> reasons = new ArrayList<>();
        int wastedCapacity = scoredCandidate.candidate().maxCapacity() - reservation.getPartySize();

        reasons.add("Capacity is suitable for a party of " + reservation.getPartySize() + " guests.");
        if (wastedCapacity == 0) {
            reasons.add("The assignment wastes no seats.");
        } else if (wastedCapacity <= 2) {
            reasons.add("Unused capacity remains low.");
        }

        if (scoredCandidate.candidate().type().name().equals("TABLE")) {
            reasons.add("A single table is preferred over a more complex combination.");
        } else {
            reasons.add("A table combination provides the required capacity.");
        }

        reasons.add("The selected dining room's operational priority is respected.");
        if (reservation.isAccessibilityRequired()) {
            reasons.add("The reservation's accessibility requirement is satisfied.");
        }
        if (scoredCandidate.candidate().advanced()) {
            reasons.add(
                "Requires " + scoredCandidate.candidate().setupTimeMinutes()
                    + " minutes of setup and an operational cost of "
                    + scoredCandidate.candidate().operationalCostLevel().name().toLowerCase() + "."
            );
        }

        String summary = "Selected " + scoredCandidate.candidate().displayName()
            + " with a score of " + String.format(java.util.Locale.US, "%.2f", scoredCandidate.totalScore()) + ".";

        Map<String, Object> explanation = new LinkedHashMap<>();
        explanation.put("summary", summary);
        explanation.put("candidateType", scoredCandidate.candidate().type().name());
        explanation.put(
            "candidateId",
            scoredCandidate.candidate().table() != null
                ? scoredCandidate.candidate().table().getId()
                : scoredCandidate.candidate().tableCombination().getId()
        );
        explanation.put("displayName", scoredCandidate.candidate().displayName());
        explanation.put("tableIds", scoredCandidate.candidate().tableIds());
        explanation.put("advanced", scoredCandidate.candidate().advanced());
        explanation.put("operationalCostLevel", scoredCandidate.candidate().operationalCostLevel().name());
        explanation.put("setupTimeMinutes", scoredCandidate.candidate().setupTimeMinutes());
        explanation.put("score", scoredCandidate.totalScore());
        explanation.put("resources", scoredCandidate.candidate().resourceRequirements().stream().map(requirement -> Map.of(
            "storageResourceId", requirement.resource().getId(),
            "resourceName", requirement.resource().getName(),
            "resourceType", requirement.resource().getResourceType().name(),
            "quantity", requirement.quantity(),
            "capacityPerUnit", requirement.resource().getCapacityPerUnit()
        )).toList());
        explanation.put("bonuses", scoredCandidate.bonuses());
        explanation.put("penalties", scoredCandidate.penalties());
        explanation.put("reasons", reasons);

        try {
            return new AssignmentExplanation(summary, objectMapper.writeValueAsString(explanation), reasons);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("Could not serialize assignment explanation");
        }
    }
}
