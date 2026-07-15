package com.restaurantplanner.table.api;

import com.restaurantplanner.table.domain.RestaurantTable;
import org.springframework.stereotype.Component;

@Component
public class RestaurantTableMapper {

    public RestaurantTableResponse toResponse(RestaurantTable table) {
        return new RestaurantTableResponse(
            table.getId(),
            table.getRestaurant().getId(),
            table.getDiningRoom() == null ? null : table.getDiningRoom().getId(),
            table.getTableType().name(),
            table.getCode(),
            table.getLabel(),
            table.getMinCapacity(),
            table.getMaxCapacity(),
            table.getShape(),
            table.getX(),
            table.getY(),
            table.getWidth(),
            table.getHeight(),
            table.isActive(),
            table.getCreatedAt(),
            table.getUpdatedAt()
        );
    }
}
