package com.restaurantplanner.onboarding;

import java.util.List;

public record PilotOnboardingManifest(
    RestaurantInput restaurant,
    List<UserInput> users
) {

    public record RestaurantInput(
        String name,
        String slug,
        String timezone,
        String phone
    ) {
    }

    public record UserInput(
        String name,
        String email,
        String role,
        String passwordFile
    ) {
    }
}
