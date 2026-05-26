package com.restaurantplanner.reservation.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationAssignmentRepository extends JpaRepository<ReservationAssignment, Long> {

    @EntityGraph(attributePaths = {
        "reservation",
        "table",
        "table.diningRoom",
        "tableCombination",
        "tableCombination.items",
        "tableCombination.items.table",
        "tableCombination.items.table.diningRoom"
    })
    List<ReservationAssignment> findByActiveTrueAndReservationRestaurantIdAndReservationReservationDateAndReservationStatusIn(
        Long restaurantId,
        LocalDate reservationDate,
        Collection<ReservationStatus> statuses
    );

    List<ReservationAssignment> findByReservationIdAndActiveTrue(Long reservationId);
}
