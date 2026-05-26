package com.restaurantplanner.diningroom.api;

import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.diningroom.service.DiningRoomService;
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
@RequestMapping("/api/restaurants/{restaurantId}/dining-rooms")
public class DiningRoomController {

    private final DiningRoomService diningRoomService;

    public DiningRoomController(DiningRoomService diningRoomService) {
        this.diningRoomService = diningRoomService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DiningRoomResponse create(
        @PathVariable Long restaurantId,
        @Valid @RequestBody CreateDiningRoomRequest request,
        Authentication authentication
    ) {
        return diningRoomService.create(restaurantId, request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping
    public List<DiningRoomResponse> findAll(@PathVariable Long restaurantId, Authentication authentication) {
        return diningRoomService.findAll(restaurantId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping("/{diningRoomId}")
    public DiningRoomResponse findById(
        @PathVariable Long restaurantId,
        @PathVariable Long diningRoomId,
        Authentication authentication
    ) {
        return diningRoomService.findById(restaurantId, diningRoomId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PatchMapping("/{diningRoomId}")
    public DiningRoomResponse update(
        @PathVariable Long restaurantId,
        @PathVariable Long diningRoomId,
        @Valid @RequestBody UpdateDiningRoomRequest request,
        Authentication authentication
    ) {
        return diningRoomService.update(restaurantId, diningRoomId, request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @DeleteMapping("/{diningRoomId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
        @PathVariable Long restaurantId,
        @PathVariable Long diningRoomId,
        Authentication authentication
    ) {
        diningRoomService.delete(restaurantId, diningRoomId, (AuthenticatedUser) authentication.getPrincipal());
    }
}

