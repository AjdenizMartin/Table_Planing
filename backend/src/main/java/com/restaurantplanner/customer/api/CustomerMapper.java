package com.restaurantplanner.customer.api;

import com.restaurantplanner.customer.domain.Customer;
import org.springframework.stereotype.Component;

@Component
public class CustomerMapper {

    public CustomerResponse toResponse(Customer customer) {
        return new CustomerResponse(
            customer.getId(),
            customer.getRestaurant().getId(),
            customer.getFirstName(),
            customer.getLastName(),
            customer.getPhone(),
            customer.getEmail(),
            customer.getNotes(),
            customer.getTagsJson(),
            customer.getMobilityNeeds(),
            customer.getCreatedAt(),
            customer.getUpdatedAt()
        );
    }
}
