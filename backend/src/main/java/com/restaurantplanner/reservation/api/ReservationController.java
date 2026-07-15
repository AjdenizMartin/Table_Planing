package com.restaurantplanner.reservation.api;

import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.optimization.api.AssignReservationResponse;
import com.restaurantplanner.optimization.api.AssignmentSelectionRequest;
import com.restaurantplanner.optimization.api.AssignmentHistoryItemResponse;
import com.restaurantplanner.optimization.api.AssignmentSuggestionsResponse;
import com.restaurantplanner.optimization.service.ReservationAssignmentService;
import com.restaurantplanner.reservation.service.ReservationService;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/reservations")
public class ReservationController {

    private final ReservationService reservationService;
    private final ReservationAssignmentService reservationAssignmentService;

    public ReservationController(
        ReservationService reservationService,
        ReservationAssignmentService reservationAssignmentService
    ) {
        this.reservationService = reservationService;
        this.reservationAssignmentService = reservationAssignmentService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReservationResponse create(
        @PathVariable Long restaurantId,
        @Valid @RequestBody CreateReservationRequest request,
        Authentication authentication
    ) {
        return reservationService.create(restaurantId, request, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping
    public List<ReservationResponse> findAll(
        @PathVariable Long restaurantId,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        Authentication authentication
    ) {
        return reservationService.findAll(restaurantId, date, (AuthenticatedUser) authentication.getPrincipal());
    }

    @GetMapping("/search")
    public List<ReservationResponse> search(
        @PathVariable Long restaurantId,
        @RequestParam(required = false) String customerQuery,
        @RequestParam(required = false) String status,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @RequestParam(required = false) Integer partySize,
        Authentication authentication
    ) {
        return reservationService.search(
            restaurantId, customerQuery, status, dateFrom, dateTo, partySize,
            (AuthenticatedUser) authentication.getPrincipal()
        );
    }

    @GetMapping("/{reservationId}")
    public ReservationResponse findById(
        @PathVariable Long restaurantId,
        @PathVariable Long reservationId,
        Authentication authentication
    ) {
        return reservationService.findById(restaurantId, reservationId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PatchMapping("/{reservationId}")
    public ReservationResponse update(
        @PathVariable Long restaurantId,
        @PathVariable Long reservationId,
        @Valid @RequestBody UpdateReservationRequest request,
        Authentication authentication
    ) {
        return reservationService.update(
            restaurantId,
            reservationId,
            request,
            (AuthenticatedUser) authentication.getPrincipal()
        );
    }

    @PostMapping("/{reservationId}/confirm")
    public ReservationResponse confirm(
        @PathVariable Long restaurantId,
        @PathVariable Long reservationId,
        Authentication authentication
    ) {
        return reservationService.confirm(restaurantId, reservationId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PostMapping("/{reservationId}/arrived")
    public ReservationResponse arrived(
        @PathVariable Long restaurantId,
        @PathVariable Long reservationId,
        Authentication authentication
    ) {
        return reservationService.arrived(restaurantId, reservationId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PostMapping("/{reservationId}/cancel")
    public ReservationResponse cancel(
        @PathVariable Long restaurantId,
        @PathVariable Long reservationId,
        Authentication authentication
    ) {
        return reservationService.cancel(restaurantId, reservationId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PostMapping("/{reservationId}/seat")
    public ReservationResponse seat(
        @PathVariable Long restaurantId,
        @PathVariable Long reservationId,
        Authentication authentication
    ) {
        return reservationService.seat(restaurantId, reservationId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PostMapping("/{reservationId}/complete")
    public ReservationResponse complete(
        @PathVariable Long restaurantId,
        @PathVariable Long reservationId,
        Authentication authentication
    ) {
        return reservationService.complete(restaurantId, reservationId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PostMapping("/{reservationId}/no-show")
    public ReservationResponse noShow(
        @PathVariable Long restaurantId,
        @PathVariable Long reservationId,
        Authentication authentication
    ) {
        return reservationService.noShow(restaurantId, reservationId, (AuthenticatedUser) authentication.getPrincipal());
    }

    @PostMapping("/{reservationId}/assign")
    public AssignReservationResponse assign(
        @PathVariable Long restaurantId,
        @PathVariable Long reservationId,
        Authentication authentication
    ) {
        return reservationAssignmentService.assign(
            restaurantId,
            reservationId,
            (AuthenticatedUser) authentication.getPrincipal()
        );
    }

    @GetMapping("/{reservationId}/assignment-suggestions")
    public AssignmentSuggestionsResponse assignmentSuggestions(
        @PathVariable Long restaurantId,
        @PathVariable Long reservationId,
        Authentication authentication
    ) {
        return reservationAssignmentService.suggest(
            restaurantId,
            reservationId,
            (AuthenticatedUser) authentication.getPrincipal()
        );
    }

    @PostMapping("/{reservationId}/assignment-selection")
    public AssignReservationResponse selectAssignment(
        @PathVariable Long restaurantId,
        @PathVariable Long reservationId,
        @Valid @RequestBody AssignmentSelectionRequest request,
        Authentication authentication
    ) {
        return reservationAssignmentService.select(
            restaurantId,
            reservationId,
            request,
            (AuthenticatedUser) authentication.getPrincipal()
        );
    }

    @GetMapping("/{reservationId}/assignment-history")
    public List<AssignmentHistoryItemResponse> assignmentHistory(
        @PathVariable Long restaurantId,
        @PathVariable Long reservationId,
        Authentication authentication
    ) {
        return reservationAssignmentService.history(
            restaurantId,
            reservationId,
            (AuthenticatedUser) authentication.getPrincipal()
        );
    }
}
