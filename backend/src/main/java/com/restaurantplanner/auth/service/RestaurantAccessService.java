package com.restaurantplanner.auth.service;

import com.restaurantplanner.auth.security.AuthenticatedUser;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

@Service
public class RestaurantAccessService {

    public void assertCanAccessRestaurant(AuthenticatedUser user, Long restaurantId) {
        if (restaurantId == null) {
            return;
        }

        if (!user.canAccessRestaurant(restaurantId)) {
            throw new AccessDeniedException("User is not allowed to access restaurant " + restaurantId);
        }
    }
}
