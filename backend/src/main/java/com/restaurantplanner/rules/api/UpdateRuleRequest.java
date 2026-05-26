package com.restaurantplanner.rules.api;

import com.restaurantplanner.rules.domain.RuleType;

public record UpdateRuleRequest(
    RuleType ruleType,
    String name,
    Boolean enabled,
    Integer priority,
    String configJson
) {
}
