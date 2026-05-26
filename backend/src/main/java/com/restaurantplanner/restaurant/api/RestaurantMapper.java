package com.restaurantplanner.restaurant.api;

import com.restaurantplanner.auth.domain.RoleAssignment;
import com.restaurantplanner.restaurant.domain.Restaurant;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class RestaurantMapper {

    public RestaurantResponse toResponse(Restaurant restaurant, List<String> roles) {
        return new RestaurantResponse(
            restaurant.getId(),
            restaurant.getName(),
            restaurant.getSlug(),
            restaurant.getTimezone(),
            restaurant.getPhone(),
            restaurant.getStatus(),
            roles,
            restaurant.getCreatedAt(),
            restaurant.getUpdatedAt()
        );
    }
}

