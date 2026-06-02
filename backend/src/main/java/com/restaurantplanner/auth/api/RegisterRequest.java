package com.restaurantplanner.auth.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
    @NotBlank @Email @Size(max = 255) String email,
    @NotBlank @Size(min = 8, max = 128) String password,
    @NotBlank @Size(max = 160) String name,
    @NotBlank @Size(max = 160) String restaurantName
) {
}
