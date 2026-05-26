package com.restaurantplanner.rules.api;

import com.restaurantplanner.rules.domain.RuleType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

public record CreateRuleRequest(
    @NotNull RuleType ruleType,
    @NotBlank String name,
    boolean enabled,
    @NotNull @PositiveOrZero Integer priority,
    String configJson
) {
}
