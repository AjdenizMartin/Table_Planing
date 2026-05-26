package com.restaurantplanner.auth.api;

import java.util.List;

public record MeResponse(
    AuthUserResponse user,
    List<RestaurantAccessResponse> restaurants,
    Long activeRestaurantId
) {
}

