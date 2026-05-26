package com.restaurantplanner.planning.api;

import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.planning.service.PlanningService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/planning")
public class PlanningController {

    private final PlanningService planningService;

    public PlanningController(PlanningService planningService) {
        this.planningService = planningService;
    }

    @GetMapping
    public PlanningDayResponse getPlanningDay(
        @PathVariable Long restaurantId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        Authentication authentication
    ) {
        return planningService.getPlanningDay(restaurantId, date, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PostMapping("/recalculate")
    public PlanningDayResponse recalculate(
        @PathVariable Long restaurantId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        Authentication authentication
    ) {
        return planningService.recalculate(restaurantId, date, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PostMapping("/move-reservation")
    public PlanningDayResponse moveReservation(
        @PathVariable Long restaurantId,
        @Valid @RequestBody MoveReservationRequest request,
        Authentication authentication
    ) {
        return planningService.moveReservation(restaurantId, request, (AuthenticatedUser) authentication.getPrincipal());
    }
}
