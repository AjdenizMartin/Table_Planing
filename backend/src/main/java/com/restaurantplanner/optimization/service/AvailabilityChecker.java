package com.restaurantplanner.optimization.service;

import com.restaurantplanner.optimization.domain.AssignmentCandidate;
import com.restaurantplanner.optimization.domain.AssignmentCandidateType;
import com.restaurantplanner.optimization.domain.CandidateAvailability;
import com.restaurantplanner.reservation.domain.Reservation;
import com.restaurantplanner.reservation.domain.ReservationAssignment;
import java.time.Duration;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;

@Service
public class AvailabilityChecker {

    public CandidateAvailability evaluate(
        AssignmentCandidate candidate,
        Reservation reservation,
        List<ReservationAssignment> occupiedAssignments
    ) {
        List<String> reasons = new ArrayList<>();

        if (candidate.maxCapacity() < reservation.getPartySize()) {
            reasons.add("insufficient_capacity");
        }

        if (candidate.minCapacity() > reservation.getPartySize()) {
            reasons.add("below_min_capacity");
        }

        if (candidate.type() == AssignmentCandidateType.TABLE && (candidate.table() == null || !candidate.table().isActive())) {
            reasons.add("inactive_table");
        }

        if (candidate.type() == AssignmentCandidateType.TABLE_COMBINATION
            && (candidate.tableCombination() == null || !candidate.tableCombination().isActive())) {
            reasons.add("inactive_combination");
        }

        if (candidate.tables().stream().anyMatch(table -> !table.isActive())) {
            reasons.add("inactive_table");
        }

        if (!candidate.allDiningRoomsActive()) {
            reasons.add("inactive_dining_room");
        }

        if (reservation.isAccessibilityRequired() && !candidate.allDiningRoomsAccessible()) {
            reasons.add("accessibility_mismatch");
        }

        LocalTime requestedStart = reservation.getStartTime();
        LocalTime requestedEffectiveEnd = reservation.getEndTime().plusMinutes(reservation.getCleaningBufferMin());
        Set<Long> candidateTableIds = Set.copyOf(candidate.tableIds());

        Integer gapBeforeMin = null;
        Integer gapAfterMin = null;

        for (ReservationAssignment assignment : occupiedAssignments) {
            if (assignment.getReservation().getId().equals(reservation.getId())) {
                continue;
            }

            Set<Long> occupiedTableIds = extractOccupiedTableIds(assignment);
            boolean sharesResource = occupiedTableIds.stream().anyMatch(candidateTableIds::contains);
            if (!sharesResource) {
                continue;
            }

            LocalTime occupiedStart = assignment.getReservation().getStartTime();
            LocalTime occupiedEffectiveEnd = assignment.getReservation().getEndTime()
                .plusMinutes(assignment.getReservation().getCleaningBufferMin());

            boolean overlaps = requestedStart.isBefore(occupiedEffectiveEnd) && requestedEffectiveEnd.isAfter(occupiedStart);
            if (overlaps) {
                reasons.add("time_overlap");
            }

            if (!occupiedEffectiveEnd.isAfter(requestedStart)) {
                int gap = (int) Duration.between(occupiedEffectiveEnd, requestedStart).toMinutes();
                gapBeforeMin = gapBeforeMin == null ? gap : Math.min(gapBeforeMin, gap);
            }

            if (!requestedEffectiveEnd.isAfter(occupiedStart)) {
                int gap = (int) Duration.between(requestedEffectiveEnd, occupiedStart).toMinutes();
                gapAfterMin = gapAfterMin == null ? gap : Math.min(gapAfterMin, gap);
            }
        }

        return new CandidateAvailability(reasons.isEmpty(), reasons, gapBeforeMin, gapAfterMin);
    }

    private Set<Long> extractOccupiedTableIds(ReservationAssignment assignment) {
        if (assignment.getTable() != null) {
            return Set.of(assignment.getTable().getId());
        }

        if (assignment.getTableCombination() != null) {
            return assignment.getTableCombination().getItems().stream().map(item -> item.getTable().getId()).collect(java.util.stream.Collectors.toSet());
        }

        return Set.of();
    }
}
