package com.restaurantplanner.table.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RestaurantTableRepository extends JpaRepository<RestaurantTable, Long> {

    boolean existsByRestaurantIdAndCodeIgnoreCase(Long restaurantId, String code);

    boolean existsByRestaurantIdAndCodeIgnoreCaseAndIdNot(Long restaurantId, String code, Long id);

    List<RestaurantTable> findByRestaurantIdOrderByDiningRoomIdAscCodeAsc(Long restaurantId);

    List<RestaurantTable> findByRestaurantIdAndTableTypeNotOrderByDiningRoomIdAscCodeAsc(Long restaurantId, TableType tableType);

    List<RestaurantTable> findByRestaurantIdAndIdIn(Long restaurantId, List<Long> ids);

    Optional<RestaurantTable> findByIdAndRestaurantId(Long id, Long restaurantId);
}
