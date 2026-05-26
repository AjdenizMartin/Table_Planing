package com.restaurantplanner.auth.service;

import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignment;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.user.domain.User;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserFactory {

    public AuthenticatedUser fromUser(User user) {
        Set<Role> roles = user.getRoleAssignments().stream()
            .map(RoleAssignment::getRole)
            .collect(Collectors.toSet());

        Set<Long> restaurantIds = user.getRoleAssignments().stream()
            .map(roleAssignment -> roleAssignment.getRestaurant().getId())
            .collect(Collectors.toSet());

        return new AuthenticatedUser(
            user.getId(),
            user.getEmail(),
            user.getName(),
            roles,
            restaurantIds
        );
    }
}

