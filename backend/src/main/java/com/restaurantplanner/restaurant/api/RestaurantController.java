package com.restaurantplanner.restaurant.api;

import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.restaurant.service.RestaurantService;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants")
public class RestaurantController {

    private final RestaurantService restaurantService;

    public RestaurantController(RestaurantService restaurantService) {
        this.restaurantService = restaurantService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RestaurantResponse create(
        @Valid @RequestBody CreateRestaurantRequest request,
        Authentication authentication
    ) {
        return restaurantService.create(request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping
    public List<RestaurantResponse> findAll(Authentication authentication) {
        return restaurantService.findAll((AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping("/{restaurantId}")
    public RestaurantResponse findById(@PathVariable Long restaurantId, Authentication authentication) {
        return restaurantService.findById(restaurantId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PatchMapping("/{restaurantId}")
    public RestaurantResponse update(
        @PathVariable Long restaurantId,
        @Valid @RequestBody UpdateRestaurantRequest request,
        Authentication authentication
    ) {
        return restaurantService.update(restaurantId, request, (AuthenticatedUser) authentication.getPrincipal());
    }
}
