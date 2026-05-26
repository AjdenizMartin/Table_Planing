package com.restaurantplanner.planning.api;

import com.restaurantplanner.reservation.domain.ReservationStatus;
import java.time.LocalDate;
import java.time.LocalTime;

public record PlanningReservationSummaryResponse(
    Long reservationId,
    Long customerId,
    String customerName,
    ReservationStatus status,
    Integer partySize,
    LocalDate reservationDate,
    LocalTime startTime,
    LocalTime endTime,
    LocalTime effectiveEndTime,
    Integer estimatedDurationMin,
    Integer cleaningBufferMin,
    boolean accessibilityRequired,
    String specialRequests,
    String assignmentType,
    Long tableId,
    String tableCode,
    Long tableCombinationId,
    String tableCombinationName
) {
}
