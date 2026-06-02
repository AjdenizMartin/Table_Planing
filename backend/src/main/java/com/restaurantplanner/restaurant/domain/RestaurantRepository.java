package com.restaurantplanner.restaurant.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RestaurantRepository extends JpaRepository<Restaurant, Long> {

    boolean existsBySlugIgnoreCase(String slug);

    Optional<Restaurant> findBySlugIgnoreCase(String slug);

    boolean existsBySlugIgnoreCaseAndIdNot(String slug, Long id);

    @Query("""
        select distinct r
        from Restaurant r
        join r.roleAssignments ra
        where ra.user.id = :userId
        order by r.id
        """)
    List<Restaurant> findAllAccessibleByUserId(@Param("userId") Long userId);

    @Query("""
        select r
        from Restaurant r
        where r.id = :restaurantId
          and exists (
            select 1
            from RoleAssignment ra
            where ra.restaurant = r
              and ra.user.id = :userId
          )
        """)
    Optional<Restaurant> findAccessibleByIdAndUserId(@Param("restaurantId") Long restaurantId, @Param("userId") Long userId);
}
