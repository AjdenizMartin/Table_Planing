package com.restaurantplanner.auth.security;

import com.restaurantplanner.auth.domain.Role;
import java.util.Set;

public record AuthenticatedUser(
    Long userId,
    String email,
    String name,
    Set<Role> roles,
    Set<Long> restaurantIds
) {

    public boolean hasRole(Role role) {
        return roles.contains(role);
    }

    public boolean canAccessRestaurant(Long restaurantId) {
        return hasRole(Role.PLATFORM_ADMIN) || restaurantIds.contains(restaurantId);
    }
}

