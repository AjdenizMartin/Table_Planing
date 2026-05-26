package com.restaurantplanner.tablecombination.api;

import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.tablecombination.service.TableCombinationService;
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
@RequestMapping("/api/restaurants/{restaurantId}/table-combinations")
public class TableCombinationController {

    private final TableCombinationService tableCombinationService;

    public TableCombinationController(TableCombinationService tableCombinationService) {
        this.tableCombinationService = tableCombinationService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TableCombinationResponse create(
        @PathVariable Long restaurantId,
        @Valid @RequestBody CreateTableCombinationRequest request,
        Authentication authentication
    ) {
        return tableCombinationService.create(restaurantId, request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping
    public List<TableCombinationResponse> findAll(@PathVariable Long restaurantId, Authentication authentication) {
        return tableCombinationService.findAll(restaurantId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping("/{combinationId}")
    public TableCombinationResponse findById(
        @PathVariable Long restaurantId,
        @PathVariable Long combinationId,
        Authentication authentication
    ) {
        return tableCombinationService.findById(
            restaurantId,
            combinationId,
            (AuthenticatedUser) authentication.getPrincipal()
        );
    }

    @PatchMapping("/{combinationId}")
    public TableCombinationResponse update(
        @PathVariable Long restaurantId,
        @PathVariable Long combinationId,
        @Valid @RequestBody UpdateTableCombinationRequest request,
        Authentication authentication
    ) {
        return tableCombinationService.update(
            restaurantId,
            combinationId,
            request,
            (AuthenticatedUser) authentication.getPrincipal()
        );
    }

    @DeleteMapping("/{combinationId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable Long restaurantId,
        @PathVariable Long combinationId,
        Authentication authentication
    ) {
        tableCombinationService.delete(restaurantId, combinationId, (AuthenticatedUser) authentication.getPrincipal());
    }
}
