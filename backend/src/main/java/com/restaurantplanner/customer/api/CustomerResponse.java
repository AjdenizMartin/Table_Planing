package com.restaurantplanner.customer.api;

import java.time.Instant;

public record CustomerResponse(
    Long id,
    Long restaurantId,
    String firstName,
    String lastName,
    String phone,
    String email,
    String notes,
    String tagsJson,
    String mobilityNeeds,
    Instant createdAt,
    Instant updatedAt
) {
}
