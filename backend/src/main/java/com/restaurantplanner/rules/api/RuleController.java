package com.restaurantplanner.rules.api;

import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.rules.service.RuleService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/rules")
public class RuleController {

    private final RuleService ruleService;

    public RuleController(RuleService ruleService) {
        this.ruleService = ruleService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RuleResponse create(
        @PathVariable Long restaurantId,
        @Valid @RequestBody CreateRuleRequest request,
        Authentication authentication
    ) {
        return ruleService.create(restaurantId, request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping
    public List<RuleResponse> findAll(@PathVariable Long restaurantId, Authentication authentication) {
        return ruleService.findAll(restaurantId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PatchMapping("/{ruleId}")
    public RuleResponse update(
        @PathVariable Long restaurantId,
        @PathVariable Long ruleId,
        @Valid @RequestBody UpdateRuleRequest request,
        Authentication authentication
    ) {
        return ruleService.update(restaurantId, ruleId, request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @DeleteMapping("/{ruleId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable Long restaurantId,
        @PathVariable Long ruleId,
        Authentication authentication
    ) {
        ruleService.delete(restaurantId, ruleId, (AuthenticatedUser) authentication.getPrincipal());
    }
}
