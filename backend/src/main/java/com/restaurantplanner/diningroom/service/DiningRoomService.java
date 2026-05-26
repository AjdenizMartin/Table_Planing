package com.restaurantplanner.diningroom.service;

import com.restaurantplanner.audit.AuditService;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.common.api.ConflictException;
import com.restaurantplanner.common.api.NotFoundException;
import com.restaurantplanner.diningroom.api.CreateDiningRoomRequest;
import com.restaurantplanner.diningroom.api.DiningRoomMapper;
import com.restaurantplanner.diningroom.api.DiningRoomResponse;
import com.restaurantplanner.diningroom.api.UpdateDiningRoomRequest;
import com.restaurantplanner.diningroom.domain.DiningRoom;
import com.restaurantplanner.diningroom.domain.DiningRoomRepository;
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
public class DiningRoomService {

    private final DiningRoomRepository diningRoomRepository;
    private final RestaurantRepository restaurantRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final DiningRoomMapper diningRoomMapper;
    private final AuditService auditService;

    public DiningRoomService(
        DiningRoomRepository diningRoomRepository,
        RestaurantRepository restaurantRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        DiningRoomMapper diningRoomMapper,
        AuditService auditService
    ) {
        this.diningRoomRepository = diningRoomRepository;
        this.restaurantRepository = restaurantRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.diningRoomMapper = diningRoomMapper;
        this.auditService = auditService;
    }

    @Transactional
    public DiningRoomResponse create(Long restaurantId, CreateDiningRoomRequest request, AuthenticatedUser authenticatedUser) {
        Restaurant restaurant = findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        String normalizedName = normalizeRequiredName(request.name());
        if (diningRoomRepository.existsByRestaurantIdAndNameIgnoreCase(restaurantId, normalizedName)) {
            throw new ConflictException("Dining room name already exists for restaurant");
        }

        DiningRoom diningRoom = new DiningRoom();
        diningRoom.setRestaurant(restaurant);
        diningRoom.setName(normalizedName);
        diningRoom.setPriority(request.priority());
        diningRoom.setAccessible(request.accessible());
        diningRoom.setActive(request.active());
        diningRoom.setLayoutWidth(request.layoutWidth());
        diningRoom.setLayoutHeight(request.layoutHeight());

        DiningRoom saved = diningRoomRepository.save(diningRoom);
        auditService.record(restaurantId, "DiningRoom", saved.getId(), "diningroom.created", authenticatedUser.userId(), "{\"name\":\"" + saved.getName() + "\"}");
        return diningRoomMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<DiningRoomResponse> findAll(Long restaurantId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        return diningRoomRepository.findByRestaurantIdOrderByPriorityAscIdAsc(restaurantId).stream()
            .map(diningRoomMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public DiningRoomResponse findById(Long restaurantId, Long diningRoomId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        DiningRoom diningRoom = findDiningRoomOrThrow(restaurantId, diningRoomId);
        return diningRoomMapper.toResponse(diningRoom);
    }

    @Transactional
    public DiningRoomResponse update(
        Long restaurantId,
        Long diningRoomId,
        UpdateDiningRoomRequest request,
        AuthenticatedUser authenticatedUser
    ) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        DiningRoom diningRoom = findDiningRoomOrThrow(restaurantId, diningRoomId);

        if (request.name() != null) {
            String normalizedName = normalizeRequiredName(request.name());
            if (diningRoomRepository.existsByRestaurantIdAndNameIgnoreCaseAndIdNot(restaurantId, normalizedName, diningRoomId)) {
                throw new ConflictException("Dining room name already exists for restaurant");
            }
            diningRoom.setName(normalizedName);
        }

        applyIfPresent(request.priority(), diningRoom::setPriority);
        applyIfPresent(request.accessible(), diningRoom::setAccessible);
        applyIfPresent(request.active(), diningRoom::setActive);
        applyIfPresent(request.layoutWidth(), diningRoom::setLayoutWidth);
        applyIfPresent(request.layoutHeight(), diningRoom::setLayoutHeight);

        auditService.record(restaurantId, "DiningRoom", diningRoomId, "diningroom.updated", authenticatedUser.userId(), null);
        return diningRoomMapper.toResponse(diningRoom);
    }

    @Transactional
    public void delete(Long restaurantId, Long diningRoomId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        DiningRoom diningRoom = findDiningRoomOrThrow(restaurantId, diningRoomId);
        diningRoom.setActive(false);
        auditService.record(restaurantId, "DiningRoom", diningRoomId, "diningroom.deactivated", authenticatedUser.userId(), null);
    }

    private Restaurant findAccessibleRestaurantOrThrow(Long restaurantId, AuthenticatedUser authenticatedUser) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant not found"));
        }

        return restaurantRepository.findAccessibleByIdAndUserId(restaurantId, authenticatedUser.userId())
            .orElseThrow(() -> new NotFoundException("Restaurant not found"));
    }

    private DiningRoom findDiningRoomOrThrow(Long restaurantId, Long diningRoomId) {
        return diningRoomRepository.findByIdAndRestaurantId(diningRoomId, restaurantId)
            .orElseThrow(() -> new NotFoundException("Dining room not found"));
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
            throw new AccessDeniedException("Only PLATFORM_ADMIN, RESTAURANT_OWNER or MANAGER can modify dining rooms");
        }
    }

    private String normalizeRequiredName(String name) {
        String normalized = normalizeOptionalValue(name);
        if (!StringUtils.hasText(normalized)) {
            throw new IllegalArgumentException("name must not be blank");
        }
        return normalized;
    }

    private String normalizeOptionalValue(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private <T> void applyIfPresent(T value, Consumer<T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }
}
