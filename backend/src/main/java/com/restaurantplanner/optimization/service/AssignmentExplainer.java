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

        reasons.add("Capacidad ajustada para un grupo de " + reservation.getPartySize() + " comensales.");
        if (wastedCapacity == 0) {
            reasons.add("No desperdicia plazas en la asignacion.");
        } else if (wastedCapacity <= 2) {
            reasons.add("Mantiene un desperdicio de capacidad bajo.");
        }

        if (scoredCandidate.candidate().type().name().equals("TABLE")) {
            reasons.add("Prioriza una mesa individual frente a una combinacion mas compleja.");
        } else {
            reasons.add("Usa una combinacion porque aporta la capacidad necesaria.");
        }

        reasons.add("Respeta la prioridad operativa del salon seleccionado.");
        if (reservation.isAccessibilityRequired()) {
            reasons.add("Cumple el requisito de accesibilidad de la reserva.");
        }
        if (scoredCandidate.candidate().advanced()) {
            reasons.add(
                "Requiere preparacion de " + scoredCandidate.candidate().setupTimeMinutes()
                    + " minutos y coste operativo "
                    + scoredCandidate.candidate().operationalCostLevel().name().toLowerCase() + "."
            );
        }

        String summary = "Seleccionada " + scoredCandidate.candidate().displayName()
            + " con score " + String.format(java.util.Locale.US, "%.2f", scoredCandidate.totalScore()) + ".";

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
