package com.restaurantplanner.reservation.api;

import com.restaurantplanner.reservation.domain.ReservationChannel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record UpdateReservationRequest(
    Long customerId,
    ReservationChannel channel,
    @Min(1) Integer partySize,
    LocalDate reservationDate,
    LocalTime startTime,
    LocalTime endTime,
    @Min(1) Integer estimatedDurationMin,
    @Min(0) Integer cleaningBufferMin,
    @Size(max = 4000) String specialRequests,
    Boolean accessibilityRequired
) {
}
