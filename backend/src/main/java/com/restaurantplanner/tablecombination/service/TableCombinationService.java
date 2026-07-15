package com.restaurantplanner.tablecombination.service;

import com.restaurantplanner.audit.AuditService;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.common.api.NotFoundException;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.storage.domain.StorageResource;
import com.restaurantplanner.storage.domain.StorageResourceRepository;
import com.restaurantplanner.table.domain.RestaurantTable;
import com.restaurantplanner.table.domain.RestaurantTableRepository;
import com.restaurantplanner.table.domain.TableType;
import com.restaurantplanner.tablecombination.api.CreateTableCombinationRequest;
import com.restaurantplanner.tablecombination.api.TableCombinationMapper;
import com.restaurantplanner.tablecombination.api.TableCombinationResponse;
import com.restaurantplanner.tablecombination.api.TableCombinationResourceRequirementRequest;
import com.restaurantplanner.tablecombination.api.UpdateTableCombinationRequest;
import com.restaurantplanner.tablecombination.domain.CombinationType;
import com.restaurantplanner.tablecombination.domain.OperationalCostLevel;
import com.restaurantplanner.tablecombination.domain.TableCombination;
import com.restaurantplanner.tablecombination.domain.TableCombinationItem;
import com.restaurantplanner.tablecombination.domain.TableCombinationRepository;
import com.restaurantplanner.tablecombination.domain.TableCombinationResourceRequirement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class TableCombinationService {

    private final TableCombinationRepository tableCombinationRepository;
    private final RestaurantTableRepository restaurantTableRepository;
    private final StorageResourceRepository storageResourceRepository;
    private final RestaurantRepository restaurantRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final TableCombinationMapper tableCombinationMapper;
    private final AuditService auditService;

    public TableCombinationService(
        TableCombinationRepository tableCombinationRepository,
        RestaurantTableRepository restaurantTableRepository,
        StorageResourceRepository storageResourceRepository,
        RestaurantRepository restaurantRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        TableCombinationMapper tableCombinationMapper,
        AuditService auditService
    ) {
        this.tableCombinationRepository = tableCombinationRepository;
        this.restaurantTableRepository = restaurantTableRepository;
        this.storageResourceRepository = storageResourceRepository;
        this.restaurantRepository = restaurantRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.tableCombinationMapper = tableCombinationMapper;
        this.auditService = auditService;
    }

    @Transactional
    public TableCombinationResponse create(
        Long restaurantId,
        CreateTableCombinationRequest request,
        AuthenticatedUser authenticatedUser
    ) {
        Restaurant restaurant = findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        String normalizedName = normalizeRequired(request.name(), "name");
        CombinationType combinationType = parseCombinationType(request.combinationType(), CombinationType.STANDARD);
        OperationalCostLevel operationalCostLevel = parseOperationalCostLevel(
            request.operationalCostLevel(),
            OperationalCostLevel.LOW
        );
        int setupTimeMinutes = request.setupTimeMinutes() == null ? 0 : request.setupTimeMinutes();
        validateCapacityRange(request.minCapacity(), request.maxCapacity());
        List<RestaurantTable> tables = resolveTables(restaurantId, request.tableIds());
        List<ResolvedResourceRequirement> requirements = resolveResourceRequirements(
            restaurantId,
            request.resourceRequirements()
        );
        validateAdvancedConfiguration(combinationType, setupTimeMinutes, requirements);
        validateCombinationCapacities(request.minCapacity(), request.maxCapacity(), tables, requirements);

        TableCombination combination = new TableCombination();
        combination.setRestaurant(restaurant);
        combination.setName(normalizedName);
        combination.setMinCapacity(request.minCapacity());
        combination.setMaxCapacity(request.maxCapacity());
        combination.setActive(request.active());
        combination.setCombinationType(combinationType);
        combination.setOperationalCostLevel(operationalCostLevel);
        combination.setSetupTimeMinutes(setupTimeMinutes);
        replaceItems(combination, tables);
        replaceResourceRequirements(combination, restaurant, requirements);

        TableCombination saved = tableCombinationRepository.save(combination);
        auditService.record(restaurantId, "TableCombination", saved.getId(), "combination.created", authenticatedUser.userId(), "{\"name\":\"" + saved.getName() + "\"}");
        return tableCombinationMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<TableCombinationResponse> findAll(Long restaurantId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        return tableCombinationRepository.findByRestaurantIdAndActiveTrueOrderByNameAscIdAsc(restaurantId).stream()
            .map(tableCombinationMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public TableCombinationResponse findById(Long restaurantId, Long combinationId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        return tableCombinationMapper.toResponse(findCombinationOrThrow(restaurantId, combinationId));
    }

    @Transactional
    public TableCombinationResponse update(
        Long restaurantId,
        Long combinationId,
        UpdateTableCombinationRequest request,
        AuthenticatedUser authenticatedUser
    ) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        TableCombination combination = findCombinationOrThrow(restaurantId, combinationId);

        CombinationType combinationType = request.combinationType() == null
            ? combination.getCombinationType()
            : parseCombinationType(request.combinationType(), combination.getCombinationType());
        OperationalCostLevel operationalCostLevel = request.operationalCostLevel() == null
            ? combination.getOperationalCostLevel()
            : parseOperationalCostLevel(request.operationalCostLevel(), combination.getOperationalCostLevel());
        int setupTimeMinutes = request.setupTimeMinutes() == null
            ? combination.getSetupTimeMinutes()
            : request.setupTimeMinutes();
        int minCapacity = request.minCapacity() == null ? combination.getMinCapacity() : request.minCapacity();
        int maxCapacity = request.maxCapacity() == null ? combination.getMaxCapacity() : request.maxCapacity();
        List<RestaurantTable> tables = request.tableIds() == null
            ? combination.getItems().stream().map(TableCombinationItem::getTable).toList()
            : resolveTables(restaurantId, request.tableIds());
        List<ResolvedResourceRequirement> requirements = request.resourceRequirements() == null
            ? combination.getResourceRequirements().stream()
                .map(requirement -> new ResolvedResourceRequirement(
                    requirement.getStorageResource(),
                    requirement.getQuantity()
                ))
                .toList()
            : resolveResourceRequirements(restaurantId, request.resourceRequirements());

        validateCapacityRange(minCapacity, maxCapacity);
        validateAdvancedConfiguration(combinationType, setupTimeMinutes, requirements);
        validateCombinationCapacities(minCapacity, maxCapacity, tables, requirements);

        if (request.name() != null) {
            combination.setName(normalizeRequired(request.name(), "name"));
        }
        combination.setMinCapacity(minCapacity);
        combination.setMaxCapacity(maxCapacity);
        combination.setCombinationType(combinationType);
        combination.setOperationalCostLevel(operationalCostLevel);
        combination.setSetupTimeMinutes(setupTimeMinutes);
        if (request.active() != null) {
            combination.setActive(request.active());
        }
        if (request.tableIds() != null) {
            replaceItems(combination, tables);
        }
        if (request.resourceRequirements() != null) {
            replaceResourceRequirements(combination, combination.getRestaurant(), requirements);
        }

        auditService.record(restaurantId, "TableCombination", combinationId, "combination.updated", authenticatedUser.userId(), null);
        return tableCombinationMapper.toResponse(combination);
    }

    @Transactional
    public void delete(Long restaurantId, Long combinationId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);
        TableCombination combination = findCombinationOrThrow(restaurantId, combinationId);
        combination.setActive(false);
        auditService.record(restaurantId, "TableCombination", combinationId, "combination.deactivated", authenticatedUser.userId(), null);
    }

    private Restaurant findAccessibleRestaurantOrThrow(Long restaurantId, AuthenticatedUser authenticatedUser) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant not found"));
        }

        return restaurantRepository.findAccessibleByIdAndUserId(restaurantId, authenticatedUser.userId())
            .orElseThrow(() -> new NotFoundException("Restaurant not found"));
    }

    private TableCombination findCombinationOrThrow(Long restaurantId, Long combinationId) {
        return tableCombinationRepository.findByIdAndRestaurantIdAndActiveTrue(combinationId, restaurantId)
            .orElseThrow(() -> new NotFoundException("Table combination not found"));
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
            throw new AccessDeniedException(
                "Only PLATFORM_ADMIN, RESTAURANT_OWNER or MANAGER can modify table combinations"
            );
        }
    }

    private List<RestaurantTable> resolveTables(Long restaurantId, List<Long> tableIds) {
        if (tableIds == null || tableIds.size() < 2) {
            throw new IllegalArgumentException("tableIds must contain at least two tables");
        }

        Set<Long> distinctIds = new LinkedHashSet<>(tableIds);
        if (distinctIds.size() != tableIds.size()) {
            throw new IllegalArgumentException("tableIds must not contain duplicates");
        }

        List<RestaurantTable> tables = restaurantTableRepository.findByRestaurantIdAndIdIn(restaurantId, new ArrayList<>(distinctIds));
        if (tables.size() != distinctIds.size()) {
            throw new NotFoundException("One or more tables do not belong to the restaurant");
        }
        if (tables.stream().anyMatch(table -> table.getTableType() == TableType.STORAGE)) {
            throw new IllegalArgumentException("Storage tables cannot be used in standard table combinations");
        }

        Map<Long, RestaurantTable> tablesById = tables.stream()
            .collect(Collectors.toMap(RestaurantTable::getId, table -> table));

        return tableIds.stream()
            .map(tableId -> {
                RestaurantTable table = tablesById.get(tableId);
                if (table == null) {
                    throw new NotFoundException("One or more tables do not belong to the restaurant");
                }
                return table;
            })
            .toList();
    }

    private void replaceItems(TableCombination combination, List<RestaurantTable> tables) {
        combination.getItems().clear();

        for (int index = 0; index < tables.size(); index++) {
            TableCombinationItem item = new TableCombinationItem();
            item.setTableCombination(combination);
            item.setTable(tables.get(index));
            item.setOrderIndex(index);
            combination.getItems().add(item);
        }
    }

    private List<ResolvedResourceRequirement> resolveResourceRequirements(
        Long restaurantId,
        List<TableCombinationResourceRequirementRequest> requests
    ) {
        if (requests == null || requests.isEmpty()) {
            return List.of();
        }

        Set<Long> distinctIds = requests.stream()
            .map(TableCombinationResourceRequirementRequest::storageResourceId)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        if (distinctIds.size() != requests.size()) {
            throw new IllegalArgumentException("resourceRequirements must not contain duplicate resources");
        }

        List<StorageResource> resources = storageResourceRepository.findByRestaurantIdAndIdIn(
            restaurantId,
            new ArrayList<>(distinctIds)
        );
        if (resources.size() != distinctIds.size()) {
            throw new NotFoundException("One or more storage resources do not belong to the restaurant");
        }

        Map<Long, StorageResource> resourcesById = resources.stream()
            .collect(Collectors.toMap(StorageResource::getId, resource -> resource));

        return requests.stream().map(request -> {
            StorageResource resource = resourcesById.get(request.storageResourceId());
            if (resource == null) {
                throw new NotFoundException("One or more storage resources do not belong to the restaurant");
            }
            if (!resource.isActive()) {
                throw new IllegalArgumentException("Inactive storage resources cannot be used in combinations");
            }
            if (request.quantity() > resource.getQuantity()) {
                throw new IllegalArgumentException(
                    "Required quantity for " + resource.getName() + " exceeds configured inventory"
                );
            }
            return new ResolvedResourceRequirement(resource, request.quantity());
        }).toList();
    }

    private void replaceResourceRequirements(
        TableCombination combination,
        Restaurant restaurant,
        List<ResolvedResourceRequirement> requirements
    ) {
        combination.getResourceRequirements().clear();
        for (ResolvedResourceRequirement resolved : requirements) {
            TableCombinationResourceRequirement requirement = new TableCombinationResourceRequirement();
            requirement.setRestaurant(restaurant);
            requirement.setTableCombination(combination);
            requirement.setStorageResource(resolved.resource());
            requirement.setQuantity(resolved.quantity());
            combination.getResourceRequirements().add(requirement);
        }
    }

    private void validateCapacityRange(int minCapacity, int maxCapacity) {
        if (minCapacity > maxCapacity) {
            throw new IllegalArgumentException("minCapacity must be less than or equal to maxCapacity");
        }
    }

    private void validateCombinationCapacities(
        int minCapacity,
        int maxCapacity,
        List<RestaurantTable> tables,
        List<ResolvedResourceRequirement> requirements
    ) {
        int combinedMaxCapacity = tables.stream().mapToInt(RestaurantTable::getMaxCapacity).sum()
            + requirements.stream().mapToInt(requirement ->
                requirement.quantity() * requirement.resource().getCapacityPerUnit()
            ).sum();
        if (maxCapacity > combinedMaxCapacity) {
            throw new IllegalArgumentException("maxCapacity must not exceed the combined max capacity of included tables");
        }
        if (minCapacity > combinedMaxCapacity) {
            throw new IllegalArgumentException("minCapacity must be compatible with included tables");
        }
    }

    private void validateAdvancedConfiguration(
        CombinationType combinationType,
        int setupTimeMinutes,
        List<ResolvedResourceRequirement> requirements
    ) {
        if (setupTimeMinutes < 0) {
            throw new IllegalArgumentException("setupTimeMinutes must not be negative");
        }
        if (combinationType == CombinationType.STANDARD && !requirements.isEmpty()) {
            throw new IllegalArgumentException("Standard combinations cannot require storage resources");
        }
        if (combinationType == CombinationType.STANDARD && setupTimeMinutes != 0) {
            throw new IllegalArgumentException("Standard combinations must have zero setup time");
        }
    }

    private CombinationType parseCombinationType(String value, CombinationType defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return CombinationType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported combinationType: " + value);
        }
    }

    private OperationalCostLevel parseOperationalCostLevel(String value, OperationalCostLevel defaultValue) {
        if (!StringUtils.hasText(value)) {
            return defaultValue;
        }
        try {
            return OperationalCostLevel.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported operationalCostLevel: " + value);
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

    private record ResolvedResourceRequirement(StorageResource resource, int quantity) {
    }
}
