package com.restaurantplanner.auth.api;

import com.restaurantplanner.auth.domain.RoleAssignment;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.user.domain.User;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class AuthMapper {

    public AuthResponse toAuthResponse(User user, String accessToken, String refreshToken, long expiresIn) {
        return new AuthResponse(
            accessToken,
            refreshToken,
            expiresIn,
            toUserResponse(user),
            toRestaurantResponses(user)
        );
    }

    public MeResponse toMeResponse(User user, Long activeRestaurantId) {
        return new MeResponse(
            toUserResponse(user),
            toRestaurantResponses(user),
            activeRestaurantId
        );
    }

    private AuthUserResponse toUserResponse(User user) {
        return new AuthUserResponse(user.getId(), user.getName(), user.getEmail());
    }

    private List<RestaurantAccessResponse> toRestaurantResponses(User user) {
        Map<Restaurant, List<RoleAssignment>> assignmentsByRestaurant = user.getRoleAssignments().stream()
            .collect(Collectors.groupingBy(RoleAssignment::getRestaurant));

        return assignmentsByRestaurant.entrySet().stream()
            .map(entry -> new RestaurantAccessResponse(
                entry.getKey().getId(),
                entry.getKey().getName(),
                entry.getKey().getSlug(),
                entry.getValue().stream()
                    .map(roleAssignment -> roleAssignment.getRole().name())
                    .sorted()
                    .toList()
            ))
            .sorted(Comparator.comparing(RestaurantAccessResponse::id))
            .toList();
    }
}

