package com.restaurantplanner.reservation.domain;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationAssignmentResourceRepository
    extends JpaRepository<ReservationAssignmentResource, Long> {

    @EntityGraph(attributePaths = {
        "reservationAssignment",
        "reservationAssignment.reservation",
        "storageResource"
    })
    List<ReservationAssignmentResource> findByRestaurantIdAndStorageResourceIdAndReservationAssignmentActiveTrueAndReservationAssignmentReservationReservationDateGreaterThanEqualAndReservationAssignmentReservationStatusIn(
        Long restaurantId,
        Long storageResourceId,
        LocalDate reservationDate,
        Collection<ReservationStatus> statuses
    );
}
