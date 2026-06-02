package com.restaurantplanner.customer.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Optional<Customer> findByIdAndRestaurantId(Long id, Long restaurantId);

    List<Customer> findByRestaurantIdOrderByLastNameAscFirstNameAscIdAsc(Long restaurantId);

    @Query("""
        select c
        from Customer c
        where c.restaurant.id = :restaurantId
          and (
            lower(coalesce(c.firstName, '')) like concat('%', :query, '%')
            or lower(coalesce(c.lastName, '')) like concat('%', :query, '%')
            or lower(concat(coalesce(c.firstName, ''), ' ', coalesce(c.lastName, ''))) like concat('%', :query, '%')
            or lower(coalesce(c.phone, '')) like concat('%', :query, '%')
          )
        order by c.lastName asc, c.firstName asc, c.id asc
        """)
    List<Customer> searchByRestaurantId(
        @Param("restaurantId") Long restaurantId,
        @Param("query") String query
    );
}
