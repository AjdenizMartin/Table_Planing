package com.restaurantplanner.rules.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RuleRepository extends JpaRepository<RestaurantRule, Long> {

    List<RestaurantRule> findByRestaurantIdAndEnabledTrueOrderByPriorityAscIdAsc(Long restaurantId);

    List<RestaurantRule> findByRestaurantIdOrderByRuleTypeAscPriorityAscIdAsc(Long restaurantId);

    Optional<RestaurantRule> findByIdAndRestaurantId(Long id, Long restaurantId);

    List<RestaurantRule> findByRestaurantIdAndRuleTypeAndEnabledTrueOrderByPriorityAscIdAsc(Long restaurantId, RuleType ruleType);
}
