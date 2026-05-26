package com.restaurantplanner.reservation.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    @EntityGraph(attributePaths = {"restaurant", "customer"})
    Optional<Reservation> findByIdAndRestaurantId(Long id, Long restaurantId);

    @EntityGraph(attributePaths = {"restaurant", "customer"})
    List<Reservation> findByRestaurantIdOrderByReservationDateAscStartTimeAscIdAsc(Long restaurantId);

    @EntityGraph(attributePaths = {"restaurant", "customer"})
    List<Reservation> findByRestaurantIdAndReservationDateOrderByStartTimeAscIdAsc(Long restaurantId, LocalDate reservationDate);
}
