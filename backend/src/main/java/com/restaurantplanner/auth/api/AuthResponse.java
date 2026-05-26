package com.restaurantplanner.auth.api;

import java.util.List;

public record AuthResponse(
    String accessToken,
    String refreshToken,
    long expiresIn,
    AuthUserResponse user,
    List<RestaurantAccessResponse> restaurants
) {
}

