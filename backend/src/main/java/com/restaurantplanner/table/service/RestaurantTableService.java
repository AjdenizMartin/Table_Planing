package com.restaurantplanner.table.service;

import com.restaurantplanner.audit.AuditService;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.common.api.ConflictException;
import com.restaurantplanner.common.api.NotFoundException;
import com.restaurantplanner.diningroom.domain.DiningRoom;
import com.restaurantplanner.diningroom.domain.DiningRoomRepository;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.realtime.RestaurantRealtimePublisher;
import com.restaurantplanner.table.api.CreateRestaurantTableRequest;
import com.restaurantplanner.table.api.RestaurantTableMapper;
import com.restaurantplanner.table.api.RestaurantTableResponse;
import com.restaurantplanner.table.api.UpdateRestaurantTableLayoutRequest;
import com.restaurantplanner.table.api.UpdateRestaurantTableRequest;
import com.restaurantplanner.table.domain.RestaurantTable;
import com.restaurantplanner.table.domain.RestaurantTableRepository;
import com.restaurantplanner.table.domain.TableType;
import java.util.List;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class RestaurantTableService {

    private final RestaurantTableRepository restaurantTableRepository;
    private final RestaurantRepository restaurantRepository;
    private final DiningRoomRepository diningRoomRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final RestaurantTableMapper restaurantTableMapper;
    private final RestaurantRealtimePublisher realtimePublisher;
    private final AuditService auditService;

    public RestaurantTableService(
        RestaurantTableRepository restaurantTableRepository,
        RestaurantRepository restaurantRepository,
        DiningRoomRepository diningRoomRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        RestaurantTableMapper restaurantTableMapper,
        RestaurantRealtimePublisher realtimePublisher,
        AuditService auditService
    ) {
        this.restaurantTableRepository = restaurantTableRepository;
        this.restaurantRepository = restaurantRepository;
        this.diningRoomRepository = diningRoomRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.restaurantTableMapper = restaurantTableMapper;
        this.realtimePublisher = realtimePublisher;
        this.auditService = auditService;
    }

    @Transactional
    public RestaurantTableResponse create(
        Long restaurantId,
        CreateRestaurantTableRequest request,
        AuthenticatedUser authenticatedUser
    ) {
        Restaurant restaurant = findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        String normalizedCode = normalizeRequired(request.code(), "code");
        if (restaurantTableRepository.existsByRestaurantIdAndCodeIgnoreCase(restaurantId, normalizedCode)) {
            throw new ConflictException("Table code already exists for restaurant");
        }

        validateCapacityRange(request.minCapacity(), request.maxCapacity());
        TableType tableType = parseTableTypeOrDefault(request.tableType());
        DiningRoom diningRoom = resolveDiningRoomForTableType(request.diningRoomId(), tableType, restaurantId);

        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setDiningRoom(diningRoom);
        table.setTableType(tableType);
        table.setCode(normalizedCode);
        table.setLabel(normalizeOptional(request.label()));
        table.setMinCapacity(request.minCapacity());
        table.setMaxCapacity(request.maxCapacity());
        table.setShape(normalizeRequired(request.shape(), "shape"));
        table.setX(request.x());
        table.setY(request.y());
        table.setWidth(request.width());
        table.setHeight(request.height());
        table.setActive(request.active());

        RestaurantTable saved = restaurantTableRepository.save(table);
        auditService.record(restaurantId, "RestaurantTable", saved.getId(), "table.created", authenticatedUser.userId(), "{\"code\":\"" + saved.getCode() + "\"}");
        realtimePublisher.publishTableUpdated(
            restaurantId,
            saved.getId(),
            saved.getDiningRoom() == null ? null : saved.getDiningRoom().getId(),
            "Table created"
        );
        return restaurantTableMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<RestaurantTableResponse> findAll(Long restaurantId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        return restaurantTableRepository.findByRestaurantIdOrderByDiningRoomIdAscCodeAsc(restaurantId).stream()
            .map(restaurantTableMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public RestaurantTableResponse findById(Long restaurantId, Long tableId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        return restaurantTableMapper.toResponse(findTableOrThrow(restaurantId, tableId));
    }

    @Transactional
    public RestaurantTableResponse update(
        Long restaurantId,
        Long tableId,
        UpdateRestaurantTableRequest request,
        AuthenticatedUser authenticatedUser
    ) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        RestaurantTable table = findTableOrThrow(restaurantId, tableId);

        if (request.diningRoomId() != null) {
            table.setDiningRoom(findDiningRoomForRestaurantOrThrow(request.diningRoomId(), restaurantId));
        }

        if (request.tableType() != null) {
            table.setTableType(parseTableTypeOrDefault(request.tableType()));
        }

        if (request.code() != null) {
            String normalizedCode = normalizeRequired(request.code(), "code");
            if (restaurantTableRepository.existsByRestaurantIdAndCodeIgnoreCaseAndIdNot(restaurantId, normalizedCode, tableId)) {
                throw new ConflictException("Table code already exists for restaurant");
            }
            table.setCode(normalizedCode);
        }

        if (request.minCapacity() != null || request.maxCapacity() != null) {
            int minCapacity = request.minCapacity() != null ? request.minCapacity() : table.getMinCapacity();
            int maxCapacity = request.maxCapacity() != null ? request.maxCapacity() : table.getMaxCapacity();
            validateCapacityRange(minCapacity, maxCapacity);
            table.setMinCapacity(minCapacity);
            table.setMaxCapacity(maxCapacity);
        }

        if (request.shape() != null) {
            table.setShape(normalizeRequired(request.shape(), "shape"));
        }
        if (request.label() != null) {
            table.setLabel(normalizeOptional(request.label()));
        }
        applyIfPresent(request.active(), table::setActive);
        table.setDiningRoom(resolveDiningRoomForTableType(
            table.getDiningRoom() == null ? null : table.getDiningRoom().getId(),
            table.getTableType(),
            restaurantId
        ));

        auditService.record(restaurantId, "RestaurantTable", tableId, "table.updated", authenticatedUser.userId(), null);
        realtimePublisher.publishTableUpdated(
            restaurantId,
            table.getId(),
            table.getDiningRoom() == null ? null : table.getDiningRoom().getId(),
            "Table updated"
        );
        return restaurantTableMapper.toResponse(table);
    }

    @Transactional
    public RestaurantTableResponse updateLayout(
        Long restaurantId,
        Long tableId,
        UpdateRestaurantTableLayoutRequest request,
        AuthenticatedUser authenticatedUser
    ) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        RestaurantTable table = findTableOrThrow(restaurantId, tableId);
        validateLayoutWithinDiningRoom(table, request);
        table.setX(request.x());
        table.setY(request.y());
        table.setWidth(request.width());
        table.setHeight(request.height());

        auditService.record(restaurantId, "RestaurantTable", tableId, "table.layout_updated", authenticatedUser.userId(), "{\"x\":" + request.x() + ",\"y\":" + request.y() + "}");
        realtimePublisher.publishTableUpdated(
            restaurantId,
            table.getId(),
            table.getDiningRoom() == null ? null : table.getDiningRoom().getId(),
            "Table layout updated"
        );
        return restaurantTableMapper.toResponse(table);
    }

    @Transactional
    public void delete(Long restaurantId, Long tableId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);
        RestaurantTable table = findTableOrThrow(restaurantId, tableId);
        table.setActive(false);
        auditService.record(restaurantId, "RestaurantTable", tableId, "table.deactivated", authenticatedUser.userId(), null);
        realtimePublisher.publishTableUpdated(
            restaurantId,
            table.getId(),
            table.getDiningRoom() == null ? null : table.getDiningRoom().getId(),
            "Table deactivated"
        );
    }

    private Restaurant findAccessibleRestaurantOrThrow(Long restaurantId, AuthenticatedUser authenticatedUser) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant not found"));
        }

        return restaurantRepository.findAccessibleByIdAndUserId(restaurantId, authenticatedUser.userId())
            .orElseThrow(() -> new NotFoundException("Restaurant not found"));
    }

    private DiningRoom findDiningRoomForRestaurantOrThrow(Long diningRoomId, Long restaurantId) {
        return diningRoomRepository.findByIdAndRestaurantId(diningRoomId, restaurantId)
            .orElseThrow(() -> new NotFoundException("Dining room not found for restaurant"));
    }

    private DiningRoom resolveDiningRoomForTableType(Long diningRoomId, TableType tableType, Long restaurantId) {
        if (tableType == TableType.STORAGE) {
            return null;
        }
        if (diningRoomId == null) {
            throw new IllegalArgumentException("diningRoomId is required for non-storage tables");
        }
        return findDiningRoomForRestaurantOrThrow(diningRoomId, restaurantId);
    }

    private TableType parseTableTypeOrDefault(String value) {
        if (!StringUtils.hasText(value)) {
            return TableType.FIXED;
        }
        try {
            return TableType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Unsupported tableType: " + value);
        }
    }

    private RestaurantTable findTableOrThrow(Long restaurantId, Long tableId) {
        return restaurantTableRepository.findByIdAndRestaurantId(tableId, restaurantId)
            .orElseThrow(() -> new NotFoundException("Table not found"));
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
            throw new AccessDeniedException("Only PLATFORM_ADMIN, RESTAURANT_OWNER or MANAGER can modify tables");
        }
    }

    private void validateCapacityRange(int minCapacity, int maxCapacity) {
        if (minCapacity > maxCapacity) {
            throw new IllegalArgumentException("minCapacity must be less than or equal to maxCapacity");
        }
    }

    private void validateLayoutWithinDiningRoom(
        RestaurantTable table,
        UpdateRestaurantTableLayoutRequest request
    ) {
        if (table.getDiningRoom() == null) {
            throw new IllegalArgumentException("Storage tables do not have a dining room layout");
        }

        int layoutWidth = table.getDiningRoom().getLayoutWidth();
        int layoutHeight = table.getDiningRoom().getLayoutHeight();

        if (request.width() > layoutWidth || request.height() > layoutHeight) {
            throw new IllegalArgumentException("Table size must fit within dining room layout");
        }

        if (request.x() + request.width() > layoutWidth || request.y() + request.height() > layoutHeight) {
            throw new IllegalArgumentException("Table position must stay within dining room layout");
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

    private <T> void applyIfPresent(T value, Consumer<T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }
}
