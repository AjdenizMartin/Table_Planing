package com.restaurantplanner.customer.api;

import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.customer.service.CustomerService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/customers")
public class CustomerController {

    private final CustomerService customerService;

    public CustomerController(CustomerService customerService) {
        this.customerService = customerService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse create(
        @PathVariable Long restaurantId,
        @Valid @RequestBody CreateCustomerRequest request,
        Authentication authentication
    ) {
        return customerService.create(restaurantId, request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping
    public List<CustomerResponse> findAll(
        @PathVariable Long restaurantId,
        @RequestParam(required = false) String query,
        Authentication authentication
    ) {
        return customerService.findAll(restaurantId, query, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping("/{customerId}")
    public CustomerResponse findById(
        @PathVariable Long restaurantId,
        @PathVariable Long customerId,
        Authentication authentication
    ) {
        return customerService.findById(restaurantId, customerId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PatchMapping("/{customerId}")
    public CustomerResponse update(
        @PathVariable Long restaurantId,
        @PathVariable Long customerId,
        @Valid @RequestBody UpdateCustomerRequest request,
        Authentication authentication
    ) {
        return customerService.update(restaurantId, customerId, request, (AuthenticatedUser) authentication.getPrincipal());
    }
}
