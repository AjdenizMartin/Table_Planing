package com.restaurantplanner.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.rules.api.RuleMapper;
import com.restaurantplanner.rules.domain.RestaurantRule;
import com.restaurantplanner.rules.domain.RuleRepository;
import com.restaurantplanner.rules.domain.RuleType;
import com.restaurantplanner.rules.service.RuleService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class RuleServiceTest {

    private RuleRepository ruleRepository;
    private RuleService service;

    @BeforeEach
    void setUp() {
        ruleRepository = org.mockito.Mockito.mock(RuleRepository.class);
        service = new RuleService(
            ruleRepository,
            org.mockito.Mockito.mock(RestaurantRepository.class),
            org.mockito.Mockito.mock(RoleAssignmentRepository.class),
            new RuleMapper(),
            new ObjectMapper()
        );
    }

    @Test
    void findEffectiveBufferMinUsesFirstMatchingPartySizeRule() {
        RestaurantRule smallParties = rule(RuleType.CLEANING_BUFFER_MIN, 1, "{\"minParty\":1,\"maxParty\":2,\"bufferMin\":10}");
        RestaurantRule largeParties = rule(RuleType.CLEANING_BUFFER_MIN, 2, "{\"minParty\":3,\"maxParty\":8,\"bufferMin\":20}");
        when(ruleRepository.findByRestaurantIdAndRuleTypeAndEnabledTrueOrderByPriorityAscIdAsc(1L, RuleType.CLEANING_BUFFER_MIN))
            .thenReturn(List.of(smallParties, largeParties));

        assertEquals(20, service.findEffectiveBufferMin(1L, 4).orElseThrow());
    }

    @Test
    void invalidRuleJsonDoesNotBreakDurationLookup() {
        RestaurantRule broken = rule(RuleType.PARTY_SIZE_DURATION, 1, "not-json");
        when(ruleRepository.findByRestaurantIdAndRuleTypeAndEnabledTrueOrderByPriorityAscIdAsc(1L, RuleType.PARTY_SIZE_DURATION))
            .thenReturn(List.of(broken));

        assertTrue(service.findEffectiveDurationMin(1L, 4).isEmpty());
    }

    private RestaurantRule rule(RuleType type, int priority, String configJson) {
        RestaurantRule rule = new RestaurantRule();
        rule.setRuleType(type);
        rule.setPriority(priority);
        rule.setEnabled(true);
        rule.setName(type.name());
        rule.setConfigJson(configJson);
        return rule;
    }
}
