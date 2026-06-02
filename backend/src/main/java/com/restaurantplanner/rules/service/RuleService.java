package com.restaurantplanner.rules.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.common.api.NotFoundException;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.rules.api.CreateRuleRequest;
import com.restaurantplanner.rules.api.RuleMapper;
import com.restaurantplanner.rules.api.RuleResponse;
import com.restaurantplanner.rules.api.UpdateRuleRequest;
import com.restaurantplanner.rules.domain.RestaurantRule;
import com.restaurantplanner.rules.domain.RuleRepository;
import com.restaurantplanner.rules.domain.RuleType;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RuleService {

    private final RuleRepository ruleRepository;
    private final RestaurantRepository restaurantRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final RuleMapper ruleMapper;
    private final ObjectMapper objectMapper;

    public RuleService(
        RuleRepository ruleRepository,
        RestaurantRepository restaurantRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        RuleMapper ruleMapper,
        ObjectMapper objectMapper
    ) {
        this.ruleRepository = ruleRepository;
        this.restaurantRepository = restaurantRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.ruleMapper = ruleMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public RuleResponse create(Long restaurantId, CreateRuleRequest request, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        Restaurant restaurant = restaurantRepository.getReferenceById(restaurantId);

        RestaurantRule rule = new RestaurantRule();
        rule.setRestaurant(restaurant);
        rule.setRuleType(request.ruleType());
        rule.setName(request.name());
        rule.setEnabled(request.enabled());
        rule.setPriority(request.priority());
        rule.setConfigJson(request.configJson());

        return ruleMapper.toResponse(ruleRepository.save(rule));
    }

    @Transactional(readOnly = true)
    public List<RuleResponse> findAll(Long restaurantId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        return ruleRepository.findByRestaurantIdOrderByRuleTypeAscPriorityAscIdAsc(restaurantId).stream()
            .map(ruleMapper::toResponse)
            .toList();
    }

    @Transactional(readOnly = true)
    public List<RestaurantRule> findEnabledByRestaurantId(Long restaurantId) {
        return ruleRepository.findByRestaurantIdAndEnabledTrueOrderByPriorityAscIdAsc(restaurantId);
    }

    @Transactional(readOnly = true)
    public Optional<Integer> findEffectiveBufferMin(Long restaurantId, int partySize) {
        List<RestaurantRule> rules = ruleRepository.findByRestaurantIdAndRuleTypeAndEnabledTrueOrderByPriorityAscIdAsc(
            restaurantId, RuleType.CLEANING_BUFFER_MIN
        );
        if (rules.isEmpty()) {
            return Optional.empty();
        }
        for (RestaurantRule rule : rules) {
            if (matchesPartySize(rule, partySize)) {
                return Optional.ofNullable(readIntConfig(rule, "bufferMin"));
            }
        }
        return Optional.ofNullable(readIntConfig(rules.get(0), "bufferMin"));
    }

    @Transactional(readOnly = true)
    public Optional<Integer> findEffectiveDurationMin(Long restaurantId, int partySize) {
        List<RestaurantRule> rules = ruleRepository.findByRestaurantIdAndRuleTypeAndEnabledTrueOrderByPriorityAscIdAsc(
            restaurantId, RuleType.PARTY_SIZE_DURATION
        );
        if (rules.isEmpty()) {
            return Optional.empty();
        }
        for (RestaurantRule rule : rules) {
            if (matchesPartySize(rule, partySize)) {
                return Optional.ofNullable(readIntConfig(rule, "durationMin"));
            }
        }
        return Optional.ofNullable(readIntConfig(rules.get(rules.size() - 1), "durationMin"));
    }

    @Transactional
    public RuleResponse update(Long restaurantId, Long ruleId, UpdateRuleRequest request, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        RestaurantRule rule = findRuleOrThrow(restaurantId, ruleId);

        if (request.ruleType() != null) {
            rule.setRuleType(request.ruleType());
        }
        if (request.name() != null) {
            rule.setName(request.name());
        }
        applyIfPresent(request.enabled(), rule::setEnabled);
        if (request.priority() != null) {
            rule.setPriority(request.priority());
        }
        if (request.configJson() != null) {
            rule.setConfigJson(request.configJson());
        }

        return ruleMapper.toResponse(rule);
    }

    @Transactional
    public void delete(Long restaurantId, Long ruleId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        RestaurantRule rule = findRuleOrThrow(restaurantId, ruleId);
        rule.setEnabled(false);
    }

    private boolean matchesPartySize(RestaurantRule rule, int partySize) {
        if (rule.getConfigJson() == null) {
            return false;
        }
        try {
            var config = objectMapper.readTree(rule.getConfigJson());
            if (config.has("minParty") && config.has("maxParty")) {
                int min = config.get("minParty").asInt();
                int max = config.get("maxParty").asInt();
                return partySize >= min && partySize <= max;
            }
        } catch (Exception e) {
            return false;
        }
        return false;
    }

    private Integer readIntConfig(RestaurantRule rule, String key) {
        if (rule.getConfigJson() == null) {
            return null;
        }
        try {
            var config = objectMapper.readTree(rule.getConfigJson());
            if (config.has(key)) {
                return config.get(key).asInt();
            }
        } catch (Exception e) {
            return null;
        }
        return null;
    }

    private Restaurant findAccessibleRestaurantOrThrow(Long restaurantId, AuthenticatedUser authenticatedUser) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant not found"));
        }
        return restaurantRepository.findAccessibleByIdAndUserId(restaurantId, authenticatedUser.userId())
            .orElseThrow(() -> new NotFoundException("Restaurant not found"));
    }

    private RestaurantRule findRuleOrThrow(Long restaurantId, Long ruleId) {
        return ruleRepository.findByIdAndRestaurantId(ruleId, restaurantId)
            .orElseThrow(() -> new NotFoundException("Rule not found"));
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
            throw new AccessDeniedException("Only PLATFORM_ADMIN, RESTAURANT_OWNER or MANAGER can modify rules");
        }
    }

    private <T> void applyIfPresent(T value, Consumer<T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }
}
