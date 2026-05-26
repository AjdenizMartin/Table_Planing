package com.restaurantplanner.table.api;

import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.table.service.RestaurantTableService;
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
@RequestMapping("/api/restaurants/{restaurantId}/tables")
public class RestaurantTableController {

    private final RestaurantTableService restaurantTableService;

    public RestaurantTableController(RestaurantTableService restaurantTableService) {
        this.restaurantTableService = restaurantTableService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RestaurantTableResponse create(
        @PathVariable Long restaurantId,
        @Valid @RequestBody CreateRestaurantTableRequest request,
        Authentication authentication
    ) {
        return restaurantTableService.create(restaurantId, request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping
    public List<RestaurantTableResponse> findAll(@PathVariable Long restaurantId, Authentication authentication) {
        return restaurantTableService.findAll(restaurantId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping("/{tableId}")
    public RestaurantTableResponse findById(
        @PathVariable Long restaurantId,
        @PathVariable Long tableId,
        Authentication authentication
    ) {
        return restaurantTableService.findById(restaurantId, tableId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PatchMapping("/{tableId}")
    public RestaurantTableResponse update(
        @PathVariable Long restaurantId,
        @PathVariable Long tableId,
        @Valid @RequestBody UpdateRestaurantTableRequest request,
        Authentication authentication
    ) {
        return restaurantTableService.update(restaurantId, tableId, request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PatchMapping("/{tableId}/layout")
    public RestaurantTableResponse updateLayout(
        @PathVariable Long restaurantId,
        @PathVariable Long tableId,
        @Valid @RequestBody UpdateRestaurantTableLayoutRequest request,
        Authentication authentication
    ) {
        return restaurantTableService.updateLayout(restaurantId, tableId, request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @DeleteMapping("/{tableId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable Long restaurantId,
        @PathVariable Long tableId,
        Authentication authentication
    ) {
        restaurantTableService.delete(restaurantId, tableId, (AuthenticatedUser) authentication.getPrincipal());
    }
}

