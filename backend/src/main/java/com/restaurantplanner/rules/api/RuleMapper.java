package com.restaurantplanner.rules.api;

import com.restaurantplanner.rules.domain.RestaurantRule;
import org.springframework.stereotype.Component;

@Component
public class RuleMapper {

    public RuleResponse toResponse(RestaurantRule rule) {
        return new RuleResponse(
            rule.getId(),
            rule.getRestaurant().getId(),
            rule.getRuleType(),
            rule.getName(),
            rule.isEnabled(),
            rule.getPriority(),
            rule.getConfigJson()
        );
    }
}
