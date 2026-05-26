package com.restaurantplanner.reservation.api;

import com.restaurantplanner.reservation.domain.Reservation;
import org.springframework.stereotype.Component;

@Component
public class ReservationMapper {

    public ReservationResponse toResponse(Reservation reservation) {
        return new ReservationResponse(
            reservation.getId(),
            reservation.getRestaurant().getId(),
            reservation.getCustomer().getId(),
            reservation.getCustomer().getFirstName(),
            reservation.getCustomer().getLastName(),
            reservation.getChannel(),
            reservation.getStatus(),
            reservation.getPartySize(),
            reservation.getReservationDate(),
            reservation.getStartTime(),
            reservation.getEndTime(),
            reservation.getEstimatedDurationMin(),
            reservation.getCleaningBufferMin(),
            reservation.getConfirmedAt(),
            reservation.getCancelledAt(),
            reservation.getSpecialRequests(),
            reservation.isAccessibilityRequired(),
            reservation.getCreatedAt(),
            reservation.getUpdatedAt()
        );
    }
}
