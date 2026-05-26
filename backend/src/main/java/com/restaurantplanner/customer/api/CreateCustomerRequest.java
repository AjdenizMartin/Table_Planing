package com.restaurantplanner.customer.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;

public record CreateCustomerRequest(
    @Size(max = 120) String firstName,
    @Size(max = 120) String lastName,
    @Size(max = 40) String phone,
    @Email @Size(max = 160) String email,
    @Size(max = 4000) String notes,
    @Size(max = 4000) String tagsJson,
    @Size(max = 255) String mobilityNeeds
) {
}
