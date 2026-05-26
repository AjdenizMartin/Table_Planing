package com.restaurantplanner.reservation.api;

import com.restaurantplanner.reservation.domain.ReservationChannel;
import com.restaurantplanner.reservation.domain.ReservationStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;

public record ReservationResponse(
    Long id,
    Long restaurantId,
    Long customerId,
    String customerFirstName,
    String customerLastName,
    ReservationChannel channel,
    ReservationStatus status,
    Integer partySize,
    LocalDate reservationDate,
    LocalTime startTime,
    LocalTime endTime,
    Integer estimatedDurationMin,
    Integer cleaningBufferMin,
    Instant confirmedAt,
    Instant cancelledAt,
    String specialRequests,
    boolean accessibilityRequired,
    Instant createdAt,
    Instant updatedAt
) {
}
