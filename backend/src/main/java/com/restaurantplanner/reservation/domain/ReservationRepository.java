package com.restaurantplanner.reservation.domain;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    boolean existsByRestaurantIdAndCustomerId(Long restaurantId, Long customerId);

    @EntityGraph(attributePaths = {"restaurant", "customer"})
    Optional<Reservation> findByIdAndRestaurantId(Long id, Long restaurantId);

    @EntityGraph(attributePaths = {"restaurant", "customer"})
    List<Reservation> findByRestaurantIdOrderByReservationDateAscStartTimeAscIdAsc(Long restaurantId);

    @EntityGraph(attributePaths = {"restaurant", "customer"})
    List<Reservation> findByRestaurantIdAndReservationDateOrderByStartTimeAscIdAsc(Long restaurantId, LocalDate reservationDate);

    @Query("""
        select distinct r from Reservation r
        join fetch r.customer c
        where r.restaurant.id = :restaurantId
        and (:customerQuery is null or (
            (c.firstName is not null and lower(c.firstName) like :customerQuery)
            or (c.lastName is not null and lower(c.lastName) like :customerQuery)
            or (c.firstName is not null and c.lastName is not null and lower(concat(c.firstName, ' ', c.lastName)) like :customerQuery)
        ))
        and (:status is null or r.status = :status)
        and (:partySize is null or r.partySize = :partySize)
        order by r.reservationDate desc, r.startTime asc, r.id asc
        """)
    List<Reservation> search(
        @Param("restaurantId") Long restaurantId,
        @Param("customerQuery") String customerQuery,
        @Param("status") ReservationStatus status,
        @Param("partySize") Integer partySize
    );
}
