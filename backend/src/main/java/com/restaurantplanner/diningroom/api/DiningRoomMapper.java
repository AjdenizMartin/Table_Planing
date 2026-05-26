package com.restaurantplanner.diningroom.api;

import com.restaurantplanner.diningroom.domain.DiningRoom;
import org.springframework.stereotype.Component;

@Component
public class DiningRoomMapper {

    public DiningRoomResponse toResponse(DiningRoom diningRoom) {
        return new DiningRoomResponse(
            diningRoom.getId(),
            diningRoom.getRestaurant().getId(),
            diningRoom.getName(),
            diningRoom.getPriority(),
            diningRoom.isAccessible(),
            diningRoom.isActive(),
            diningRoom.getLayoutWidth(),
            diningRoom.getLayoutHeight(),
            diningRoom.getCreatedAt(),
            diningRoom.getUpdatedAt()
        );
    }
}

