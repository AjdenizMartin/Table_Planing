package com.restaurantplanner.diningroom.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiningRoomRepository extends JpaRepository<DiningRoom, Long> {

    boolean existsByRestaurantIdAndNameIgnoreCase(Long restaurantId, String name);

    boolean existsByRestaurantIdAndNameIgnoreCaseAndIdNot(Long restaurantId, String name, Long id);

    List<DiningRoom> findByRestaurantIdOrderByPriorityAscIdAsc(Long restaurantId);

    Optional<DiningRoom> findByIdAndRestaurantId(Long id, Long restaurantId);
}

