package com.restaurantplanner.storage.service;

import com.restaurantplanner.audit.AuditService;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.common.api.ConflictException;
import com.restaurantplanner.common.api.NotFoundException;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.storage.api.CreateStorageResourceRequest;
import com.restaurantplanner.storage.api.StorageAvailabilityResponse;
import com.restaurantplanner.storage.api.StorageResourceMapper;
import com.restaurantplanner.storage.api.StorageResourceResponse;
import com.restaurantplanner.storage.api.UpdateStorageResourceRequest;
import com.restaurantplanner.storage.domain.StorageResource;
import com.restaurantplanner.storage.domain.StorageResourceRepository;
import com.restaurantplanner.storage.domain.StorageResourceType;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class StorageResourceService {

    private final StorageResourceRepository storageResourceRepository;
    private final RestaurantRepository restaurantRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final StorageResourceMapper storageResourceMapper;
    private final AuditService auditService;

    public StorageResourceService(
        StorageResourceRepository storageResourceRepository,
        RestaurantRepository restaurantRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        StorageResourceMapper storageResourceMapper,
        AuditService auditService
    ) {
        this.storageResourceRepository = storageResourceRepository;
        this.restaurantRepository = restaurantRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.storageResourceMapper = storageResourceMapper;
        this.auditService = auditService;
    }

    @Transactional
    public StorageResourceResponse create(
        Long restaurantId,
        CreateStorageResourceRequest request,
        AuthenticatedUser authenticatedUser
    ) {
        Restaurant restaurant = findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        StorageResource resource = new StorageResource();
        resource.setRestaurant(restaurant);
        resource.setResourceType(parseResourceType(request.resourceType()));
        resource.setName(normalizeRequired(request.name(), "name"));
        resource.setQuantity(request.quantity());
        resource.setCapacityPerUnit(defaultToZero(request.capacityPerUnit()));
        resource.setSetupTimeMinutes(defaultToZero(request.setupTimeMinutes()));
        resource.setActive(request.active());
        resource.setNotes(normalizeOptional(request.notes()));

        StorageResource saved = storageResourceRepository.save(resource);
        auditService.record(restaurantId, "StorageResource", saved.getId(), "storage_resource.created", authenticatedUser.userId(), "{\"name\":\"" + saved.getName() + "\"}");
        return storageResourceMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<StorageResourceResponse> findAll(
        Long restaurantId,
        String resourceType,
        Boolean active,
        AuthenticatedUser authenticatedUser
    ) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        StorageResourceType parsedResourceType = resourceType == null ? null : parseResourceType(resourceType);

        List<StorageResource> resources;
        if (parsedResourceType != null && active != null) {
            resources = storageResourceRepository
                .findByRestaurantIdAndResourceTypeAndActiveOrderByResourceTypeAscNameAscIdAsc(
                    restaurantId,
                    parsedResourceType,
                    active
                );
        } else if (parsedResourceType != null) {
            resources = storageResourceRepository
                .findByRestaurantIdAndResourceTypeOrderByResourceTypeAscNameAscIdAsc(restaurantId, parsedResourceType);
        } else if (active != null) {
            resources = storageResourceRepository
                .findByRestaurantIdAndActiveOrderByResourceTypeAscNameAscIdAsc(restaurantId, active);
        } else {
            resources = storageResourceRepository.findByRestaurantIdOrderByResourceTypeAscNameAscIdAsc(restaurantId);
        }

        return resources.stream()
            .map(storageResourceMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public StorageResourceResponse findById(Long restaurantId, Long resourceId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        return storageResourceMapper.toResponse(findResourceOrThrow(restaurantId, resourceId));
    }

    @Transactional
    public StorageResourceResponse update(
        Long restaurantId,
        Long resourceId,
        UpdateStorageResourceRequest request,
        AuthenticatedUser authenticatedUser
    ) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        StorageResource resource = findResourceOrThrow(restaurantId, resourceId);
        if (request.resourceType() != null) {
            resource.setResourceType(parseResourceType(request.resourceType()));
        }
        if (request.name() != null) {
            resource.setName(normalizeRequired(request.name(), "name"));
        }
        applyIfPresent(request.quantity(), resource::setQuantity);
        applyIfPresent(request.capacityPerUnit(), resource::setCapacityPerUnit);
        applyIfPresent(request.setupTimeMinutes(), resource::setSetupTimeMinutes);
        applyIfPresent(request.active(), resource::setActive);
        if (request.notes() != null) {
            resource.setNotes(normalizeOptional(request.notes()));
        }

        auditService.record(restaurantId, "StorageResource", resourceId, "storage_resource.updated", authenticatedUser.userId(), null);
        return storageResourceMapper.toResponse(resource);
    }

    @Transactional
    public void delete(Long restaurantId, Long resourceId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);
        StorageResource resource = findResourceOrThrow(restaurantId, resourceId);
        resource.setActive(false);
        auditService.record(restaurantId, "StorageResource", resourceId, "storage_resource.deactivated", authenticatedUser.userId(), null);
    }

    @Transactional(readOnly = true)
    public StorageAvailabilityResponse checkAvailability(
        Long restaurantId,
        Long resourceId,
        Integer requestedQuantity,
        AuthenticatedUser authenticatedUser
    ) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        StorageResource resource = findResourceOrThrow(restaurantId, resourceId);
        if (!resource.isActive()) {
            throw new ConflictException("Storage resource is inactive");
        }
        if (requestedQuantity > resource.getQuantity()) {
            Map<String, Object> details = new LinkedHashMap<>();
            details.put("resourceId", resource.getId());
            details.put("requestedQuantity", requestedQuantity);
            details.put("availableQuantity", resource.getQuantity());
            throw new ConflictException("Requested quantity exceeds available storage resource quantity", details);
        }
        return new StorageAvailabilityResponse(resource.getId(), requestedQuantity, resource.getQuantity(), true);
    }

    private Restaurant findAccessibleRestaurantOrThrow(Long restaurantId, AuthenticatedUser authenticatedUser) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant not found"));
        }

        return restaurantRepository.findAccessibleByIdAndUserId(restaurantId, authenticatedUser.userId())
            .orElseThrow(() -> new NotFoundException("Restaurant not found"));
    }

    private StorageResource findResourceOrThrow(Long restaurantId, Long resourceId) {
        return storageResourceRepository.findByIdAndRestaurantId(resourceId, restaurantId)
            .orElseThrow(() -> new NotFoundException("Storage resource not found"));
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
            throw new AccessDeniedException("Only PLATFORM_ADMIN, RESTAURANT_OWNER or MANAGER can modify storage resources");
        }
    }

    private StorageResourceType parseResourceType(String value) {
        try {
            return StorageResourceType.valueOf(normalizeRequired(value, "resourceType").toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported resourceType: " + value);
        }
    }

    private String normalizeRequired(String value, String fieldName) {
        String normalized = normalizeOptional(value);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int defaultToZero(Integer value) {
        return value == null ? 0 : value;
    }

    private <T> void applyIfPresent(T value, Consumer<T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }
}
