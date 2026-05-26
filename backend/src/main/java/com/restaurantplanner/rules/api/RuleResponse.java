package com.restaurantplanner.rules.api;

import com.restaurantplanner.rules.domain.RuleType;

public record RuleResponse(
    Long id,
    Long restaurantId,
    RuleType ruleType,
    String name,
    boolean enabled,
    Integer priority,
    String configJson
) {
}
