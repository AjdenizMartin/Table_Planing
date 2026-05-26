package com.restaurantplanner.restaurant.service;

import com.restaurantplanner.audit.AuditService;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignment;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.common.api.ConflictException;
import com.restaurantplanner.common.api.NotFoundException;
import com.restaurantplanner.restaurant.api.CreateRestaurantRequest;
import com.restaurantplanner.restaurant.api.RestaurantMapper;
import com.restaurantplanner.restaurant.api.RestaurantResponse;
import com.restaurantplanner.restaurant.api.UpdateRestaurantRequest;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.user.domain.User;
import com.restaurantplanner.user.domain.UserRepository;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RestaurantService {

    private final RestaurantRepository restaurantRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final UserRepository userRepository;
    private final RestaurantMapper restaurantMapper;
    private final AuditService auditService;

    public RestaurantService(
        RestaurantRepository restaurantRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        UserRepository userRepository,
        RestaurantMapper restaurantMapper,
        AuditService auditService
    ) {
        this.restaurantRepository = restaurantRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.userRepository = userRepository;
        this.restaurantMapper = restaurantMapper;
        this.auditService = auditService;
    }

    @Transactional
    public RestaurantResponse create(CreateRestaurantRequest request, AuthenticatedUser authenticatedUser) {
        requirePlatformAdmin(authenticatedUser);

        String normalizedSlug = normalizeSlug(request.slug());
        if (restaurantRepository.existsBySlugIgnoreCase(normalizedSlug)) {
            throw new ConflictException("Restaurant slug already exists");
        }

        Restaurant restaurant = new Restaurant();
        restaurant.setName(normalizeRequiredValue(request.name(), "name"));
        restaurant.setSlug(normalizedSlug);
        restaurant.setTimezone(normalizeRequiredValue(request.timezone(), "timezone"));
        restaurant.setPhone(normalizeOptionalValue(request.phone()));
        restaurant.setStatus(request.status());
        restaurant.setSettingsJson("{}");
        Restaurant savedRestaurant = restaurantRepository.save(restaurant);

        User creator = userRepository.findWithRoleAssignmentsById(authenticatedUser.userId())
            .orElseThrow(() -> new NotFoundException("Authenticated user not found"));

        RoleAssignment ownerAssignment = new RoleAssignment();
        ownerAssignment.setUser(creator);
        ownerAssignment.setRestaurant(savedRestaurant);
        ownerAssignment.setRole(Role.RESTAURANT_OWNER);
        roleAssignmentRepository.save(ownerAssignment);

        auditService.record(savedRestaurant.getId(), "Restaurant", savedRestaurant.getId(), "restaurant.created", authenticatedUser.userId(), "{\"name\":\"" + normalizeValue(request.name()) + "\"}");
        return restaurantMapper.toResponse(savedRestaurant, List.of(Role.RESTAURANT_OWNER.name()));
    }

    private String normalizeValue(String value) {
        if (value == null) return "";
        return value.replace("\"", "'");
    }

    @Transactional(readOnly = true)
    public List<RestaurantResponse> findAll(AuthenticatedUser authenticatedUser) {
        List<Restaurant> restaurants = authenticatedUser.hasRole(Role.PLATFORM_ADMIN)
            ? restaurantRepository.findAll().stream()
                .sorted(Comparator.comparing(Restaurant::getId))
                .toList()
            : restaurantRepository.findAllAccessibleByUserId(authenticatedUser.userId());

        Map<Long, List<String>> rolesByRestaurantId = roleAssignmentRepository.findByUserId(authenticatedUser.userId()).stream()
            .collect(Collectors.groupingBy(
                assignment -> assignment.getRestaurant().getId(),
                Collectors.mapping(assignment -> assignment.getRole().name(), Collectors.collectingAndThen(Collectors.toList(), list -> list.stream().sorted().toList()))
            ));

        return restaurants.stream()
            .map(restaurant -> restaurantMapper.toResponse(restaurant, rolesByRestaurantId.getOrDefault(restaurant.getId(), List.of())))
            .toList();
    }

    @Transactional(readOnly = true)
    public RestaurantResponse findById(Long restaurantId, AuthenticatedUser authenticatedUser) {
        Restaurant restaurant = findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        List<String> roles = roleAssignmentRepository.findByUserId(authenticatedUser.userId()).stream()
            .filter(assignment -> Objects.equals(assignment.getRestaurant().getId(), restaurantId))
            .map(assignment -> assignment.getRole().name())
            .sorted()
            .toList();

        return restaurantMapper.toResponse(restaurant, roles);
    }

    @Transactional
    public RestaurantResponse update(Long restaurantId, UpdateRestaurantRequest request, AuthenticatedUser authenticatedUser) {
        Restaurant restaurant = findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireAdminOrOwner(authenticatedUser, restaurantId);

        if (request.slug() != null) {
            String normalizedSlug = normalizeSlug(request.slug());
            if (restaurantRepository.existsBySlugIgnoreCaseAndIdNot(normalizedSlug, restaurantId)) {
                throw new ConflictException("Restaurant slug already exists");
            }
            restaurant.setSlug(normalizedSlug);
        }

        applyIfPresent(request.name(), value -> restaurant.setName(normalizeRequiredValue(value, "name")));
        applyIfPresent(request.timezone(), value -> restaurant.setTimezone(normalizeRequiredValue(value, "timezone")));
        if (request.phone() != null) {
            restaurant.setPhone(normalizeOptionalValue(request.phone()));
        }
        if (request.status() != null) {
            restaurant.setStatus(request.status());
        }

        List<String> roles = roleAssignmentRepository.findByUserId(authenticatedUser.userId()).stream()
            .filter(assignment -> Objects.equals(assignment.getRestaurant().getId(), restaurantId))
            .map(assignment -> assignment.getRole().name())
            .sorted()
            .toList();

        auditService.record(restaurantId, "Restaurant", restaurantId, "restaurant.updated", authenticatedUser.userId(), null);
        return restaurantMapper.toResponse(restaurant, roles);
    }

    private Restaurant findAccessibleRestaurantOrThrow(Long restaurantId, AuthenticatedUser authenticatedUser) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant not found"));
        }

        return restaurantRepository.findAccessibleByIdAndUserId(restaurantId, authenticatedUser.userId())
            .orElseThrow(() -> new NotFoundException("Restaurant not found"));
    }

    private void requirePlatformAdmin(AuthenticatedUser authenticatedUser) {
        if (!authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            throw new AccessDeniedException("Only PLATFORM_ADMIN can create restaurants");
        }
    }

    private void requireAdminOrOwner(AuthenticatedUser authenticatedUser, Long restaurantId) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return;
        }

        boolean isOwner = roleAssignmentRepository.findByUserId(authenticatedUser.userId()).stream()
            .anyMatch(assignment ->
                Objects.equals(assignment.getRestaurant().getId(), restaurantId)
                    && assignment.getRole() == Role.RESTAURANT_OWNER
            );

        if (!isOwner) {
            throw new AccessDeniedException("Only PLATFORM_ADMIN or RESTAURANT_OWNER can modify restaurant data");
        }
    }

    private void applyIfPresent(String value, Consumer<String> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }

    private String normalizeRequiredValue(String value, String fieldName) {
        String normalized = normalizeOptionalValue(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private String normalizeSlug(String slug) {
        return normalizeRequiredValue(slug, "slug").toLowerCase(Locale.ROOT);
    }

    private String normalizeOptionalValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

