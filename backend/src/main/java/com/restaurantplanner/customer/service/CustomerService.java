package com.restaurantplanner.customer.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.common.api.NotFoundException;
import com.restaurantplanner.customer.api.CreateCustomerRequest;
import com.restaurantplanner.customer.api.CustomerMapper;
import com.restaurantplanner.customer.api.CustomerResponse;
import com.restaurantplanner.customer.api.UpdateCustomerRequest;
import com.restaurantplanner.customer.domain.Customer;
import com.restaurantplanner.customer.domain.CustomerRepository;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final RestaurantRepository restaurantRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final CustomerMapper customerMapper;
    private final ObjectMapper objectMapper;

    public CustomerService(
        CustomerRepository customerRepository,
        RestaurantRepository restaurantRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        CustomerMapper customerMapper,
        ObjectMapper objectMapper
    ) {
        this.customerRepository = customerRepository;
        this.restaurantRepository = restaurantRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.customerMapper = customerMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public CustomerResponse create(Long restaurantId, CreateCustomerRequest request, AuthenticatedUser authenticatedUser) {
        Restaurant restaurant = findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        Customer customer = new Customer();
        customer.setRestaurant(restaurant);
        customer.setFirstName(normalizeOptional(request.firstName()));
        customer.setLastName(normalizeOptional(request.lastName()));
        customer.setPhone(normalizeOptional(request.phone()));
        customer.setEmail(normalizeOptional(request.email()));
        customer.setNotes(normalizeOptional(request.notes()));
        customer.setTagsJson(normalizeOptional(request.tagsJson()));
        customer.setMobilityNeeds(normalizeOptional(request.mobilityNeeds()));

        validateContactIdentity(customer.getFirstName(), customer.getLastName(), customer.getPhone());
        validateTagsJson(customer.getTagsJson());

        return customerMapper.toResponse(customerRepository.save(customer));
    }

    @Transactional(readOnly = true)
    public List<CustomerResponse> findAll(Long restaurantId, String query, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        String normalizedQuery = normalizeSearchQuery(query);
        List<Customer> customers = normalizedQuery == null
            ? customerRepository.findByRestaurantIdOrderByLastNameAscFirstNameAscIdAsc(restaurantId)
            : customerRepository.searchByRestaurantId(restaurantId, normalizedQuery);

        return customers.stream()
            .map(customerMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public CustomerResponse findById(Long restaurantId, Long customerId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        return customerMapper.toResponse(findCustomerOrThrow(restaurantId, customerId));
    }

    @Transactional
    public CustomerResponse update(
        Long restaurantId,
        Long customerId,
        UpdateCustomerRequest request,
        AuthenticatedUser authenticatedUser
    ) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        Customer customer = findCustomerOrThrow(restaurantId, customerId);

        applyIfPresent(request.firstName(), value -> customer.setFirstName(normalizeOptional(value)));
        applyIfPresent(request.lastName(), value -> customer.setLastName(normalizeOptional(value)));
        applyIfPresent(request.phone(), value -> customer.setPhone(normalizeOptional(value)));
        applyIfPresent(request.email(), value -> customer.setEmail(normalizeOptional(value)));
        applyIfPresent(request.notes(), value -> customer.setNotes(normalizeOptional(value)));
        applyIfPresent(request.tagsJson(), value -> customer.setTagsJson(normalizeOptional(value)));
        applyIfPresent(request.mobilityNeeds(), value -> customer.setMobilityNeeds(normalizeOptional(value)));

        validateContactIdentity(customer.getFirstName(), customer.getLastName(), customer.getPhone());
        validateTagsJson(customer.getTagsJson());

        return customerMapper.toResponse(customer);
    }

    private Restaurant findAccessibleRestaurantOrThrow(Long restaurantId, AuthenticatedUser authenticatedUser) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant not found"));
        }

        return restaurantRepository.findAccessibleByIdAndUserId(restaurantId, authenticatedUser.userId())
            .orElseThrow(() -> new NotFoundException("Restaurant not found"));
    }

    private Customer findCustomerOrThrow(Long restaurantId, Long customerId) {
        return customerRepository.findByIdAndRestaurantId(customerId, restaurantId)
            .orElseThrow(() -> new NotFoundException("Customer not found"));
    }

    private void requireOwnerManagerOrAdmin(AuthenticatedUser authenticatedUser, Long restaurantId) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return;
        }

        boolean canManage = roleAssignmentRepository.findByUserId(authenticatedUser.userId()).stream()
            .anyMatch(assignment ->
                Objects.equals(assignment.getRestaurant().getId(), restaurantId)
                    && (assignment.getRole() == Role.RESTAURANT_OWNER || assignment.getRole() == Role.MANAGER)
            );

        if (!canManage) {
            throw new AccessDeniedException("Only PLATFORM_ADMIN, RESTAURANT_OWNER or MANAGER can modify customers");
        }
    }

    private void validateContactIdentity(String firstName, String lastName, String phone) {
        if (!StringUtils.hasText(phone) && !StringUtils.hasText(firstName) && !StringUtils.hasText(lastName)) {
            throw new IllegalArgumentException("Customer must have a phone number or at least one name");
        }
    }

    private void validateTagsJson(String tagsJson) {
        if (!StringUtils.hasText(tagsJson)) {
            return;
        }

        try {
            objectMapper.readTree(tagsJson);
        } catch (JsonProcessingException ex) {
            throw new IllegalArgumentException("tagsJson must be valid JSON");
        }
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private String normalizeSearchQuery(String query) {
        String normalized = normalizeOptional(query);
        return normalized == null ? null : normalized.toLowerCase();
    }

    private <T> void applyIfPresent(T value, Consumer<T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }
}
