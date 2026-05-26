package com.restaurantplanner.auth.api;

public record AuthUserResponse(
    Long id,
    String name,
    String email
) {
}

