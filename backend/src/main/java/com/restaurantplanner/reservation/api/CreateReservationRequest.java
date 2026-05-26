package com.restaurantplanner.reservation.api;

import com.restaurantplanner.reservation.domain.ReservationChannel;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.time.LocalTime;

public record CreateReservationRequest(
    @NotNull Long customerId,
    ReservationChannel channel,
    @NotNull @Min(1) Integer partySize,
    @NotNull LocalDate reservationDate,
    @NotNull LocalTime startTime,
    LocalTime endTime,
    @NotNull @Min(1) Integer estimatedDurationMin,
    @NotNull @Min(0) Integer cleaningBufferMin,
    @Size(max = 4000) String specialRequests,
    @NotNull Boolean accessibilityRequired
) {
}
