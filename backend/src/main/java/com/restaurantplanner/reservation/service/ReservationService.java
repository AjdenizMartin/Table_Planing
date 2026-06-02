package com.restaurantplanner.reservation.service;

import com.restaurantplanner.audit.AuditService;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.notification.event.ReservationNotificationEvent;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.common.api.ConflictException;
import com.restaurantplanner.common.api.NotFoundException;
import com.restaurantplanner.optimization.api.AssignReservationResponse;
import com.restaurantplanner.optimization.service.ReservationAssignmentService;
import com.restaurantplanner.customer.domain.Customer;
import com.restaurantplanner.customer.domain.CustomerRepository;
import com.restaurantplanner.reservation.api.CreateReservationRequest;
import com.restaurantplanner.reservation.api.ReservationMapper;
import com.restaurantplanner.reservation.api.ReservationResponse;
import com.restaurantplanner.reservation.api.UpdateReservationRequest;
import com.restaurantplanner.reservation.domain.Reservation;
import com.restaurantplanner.reservation.domain.ReservationChannel;
import com.restaurantplanner.reservation.domain.ReservationAssignmentRepository;
import com.restaurantplanner.reservation.domain.ReservationRepository;
import com.restaurantplanner.reservation.domain.ReservationStatus;
import com.restaurantplanner.realtime.RestaurantRealtimePublisher;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class ReservationService {

    private final ReservationRepository reservationRepository;
    private final ReservationAssignmentRepository reservationAssignmentRepository;
    private final RestaurantRepository restaurantRepository;
    private final CustomerRepository customerRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final ReservationMapper reservationMapper;
    private final RestaurantRealtimePublisher realtimePublisher;
    private final AuditService auditService;
    private final ApplicationEventPublisher eventPublisher;
    private final ReservationAssignmentService reservationAssignmentService;

    public ReservationService(
        ReservationRepository reservationRepository,
        ReservationAssignmentRepository reservationAssignmentRepository,
        RestaurantRepository restaurantRepository,
        CustomerRepository customerRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        ReservationMapper reservationMapper,
        RestaurantRealtimePublisher realtimePublisher,
        AuditService auditService,
        ApplicationEventPublisher eventPublisher,
        ReservationAssignmentService reservationAssignmentService
    ) {
        this.reservationRepository = reservationRepository;
        this.reservationAssignmentRepository = reservationAssignmentRepository;
        this.restaurantRepository = restaurantRepository;
        this.customerRepository = customerRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.reservationMapper = reservationMapper;
        this.realtimePublisher = realtimePublisher;
        this.auditService = auditService;
        this.eventPublisher = eventPublisher;
        this.reservationAssignmentService = reservationAssignmentService;
    }

    @Transactional
    public ReservationResponse create(
        Long restaurantId,
        CreateReservationRequest request,
        AuthenticatedUser authenticatedUser
    ) {
        Restaurant restaurant = findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        Customer customer = findCustomerForRestaurantOrThrow(request.customerId(), restaurantId);
        ReservationWindow reservationWindow = validateAndBuildWindow(
            request.startTime(),
            request.endTime(),
            request.estimatedDurationMin(),
            request.cleaningBufferMin()
        );

        Reservation reservation = new Reservation();
        reservation.setRestaurant(restaurant);
        reservation.setCustomer(customer);
        reservation.setChannel(request.channel() == null ? ReservationChannel.MANUAL : request.channel());
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setPartySize(request.partySize());
        reservation.setReservationDate(request.reservationDate());
        reservation.setStartTime(request.startTime());
        reservation.setEndTime(reservationWindow.endTime());
        reservation.setEstimatedDurationMin(reservationWindow.estimatedDurationMin());
        reservation.setCleaningBufferMin(request.cleaningBufferMin());
        reservation.setSpecialRequests(normalizeOptional(request.specialRequests()));
        reservation.setAccessibilityRequired(request.accessibilityRequired());

        Reservation saved = reservationRepository.save(reservation);
        auditService.record(restaurantId, "Reservation", saved.getId(), "reservation.created", authenticatedUser.userId(), toMetadata("Reservation created", request.partySize(), request.startTime()));
        eventPublisher.publishEvent(new ReservationNotificationEvent(
            "reservation.created", restaurantId, saved.getId(),
            customer.getId(), customer.getEmail(), customer.getFirstName() + " " + customer.getLastName(),
            request.partySize(), saved.getReservationDate(), saved.getStartTime(),
            authenticatedUser.userId()
        ));
        realtimePublisher.publishReservationEvent(
            "reservation.created",
            restaurantId,
            saved.getId(),
            saved.getReservationDate(),
            "Reservation created"
        );
        return reservationMapper.toResponse(saved);
    }

    @Transactional(readOnly = true)
    public List<ReservationResponse> findAll(Long restaurantId, LocalDate date, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);

        List<Reservation> reservations = date == null
            ? reservationRepository.findByRestaurantIdOrderByReservationDateAscStartTimeAscIdAsc(restaurantId)
            : reservationRepository.findByRestaurantIdAndReservationDateOrderByStartTimeAscIdAsc(restaurantId, date);

        return reservations.stream().map(reservationMapper::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public ReservationResponse findById(Long restaurantId, Long reservationId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        return reservationMapper.toResponse(findReservationOrThrow(restaurantId, reservationId));
    }

    @Transactional
    public ReservationResponse update(
        Long restaurantId,
        Long reservationId,
        UpdateReservationRequest request,
        AuthenticatedUser authenticatedUser
    ) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        Reservation reservation = findReservationOrThrow(restaurantId, reservationId);
        if (isTerminalStatus(reservation.getStatus())) {
            throw new ConflictException("Terminal reservations cannot be modified");
        }

        if (request.customerId() != null) {
            reservation.setCustomer(findCustomerForRestaurantOrThrow(request.customerId(), restaurantId));
        }

        applyIfPresent(request.channel(), reservation::setChannel);
        applyIfPresent(request.partySize(), reservation::setPartySize);
        applyIfPresent(request.reservationDate(), reservation::setReservationDate);
        applyIfPresent(request.specialRequests(), value -> reservation.setSpecialRequests(normalizeOptional(value)));
        applyIfPresent(request.accessibilityRequired(), reservation::setAccessibilityRequired);

        if (request.startTime() != null || request.endTime() != null || request.estimatedDurationMin() != null || request.cleaningBufferMin() != null) {
            LocalTime startTime = request.startTime() != null ? request.startTime() : reservation.getStartTime();
            Integer estimatedDurationMin = request.estimatedDurationMin() != null
                ? request.estimatedDurationMin()
                : reservation.getEstimatedDurationMin();
            Integer cleaningBufferMin = request.cleaningBufferMin() != null
                ? request.cleaningBufferMin()
                : reservation.getCleaningBufferMin();

            ReservationWindow reservationWindow = validateAndBuildWindow(
                startTime,
                request.endTime(),
                estimatedDurationMin,
                cleaningBufferMin
            );

            reservation.setStartTime(startTime);
            reservation.setEndTime(reservationWindow.endTime());
            reservation.setEstimatedDurationMin(reservationWindow.estimatedDurationMin());
            reservation.setCleaningBufferMin(cleaningBufferMin);
        }

        auditService.record(restaurantId, "Reservation", reservation.getId(), "reservation.updated", authenticatedUser.userId(), null);
        realtimePublisher.publishReservationEvent(
            "reservation.updated",
            restaurantId,
            reservation.getId(),
            reservation.getReservationDate(),
            "Reservation updated"
        );
        return reservationMapper.toResponse(reservation);
    }

    @Transactional
    public ReservationResponse confirm(Long restaurantId, Long reservationId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        Reservation reservation = findReservationOrThrow(restaurantId, reservationId);
        ensureTransitionAllowed(reservation.getStatus(), ReservationStatus.CONFIRMED);
        ensureAssignedBeforeConfirming(restaurantId, reservation, authenticatedUser);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        if (reservation.getConfirmedAt() == null) {
            reservation.setConfirmedAt(Instant.now());
        }
        auditService.record(restaurantId, "Reservation", reservation.getId(), "reservation.confirmed", authenticatedUser.userId(), null);
        eventPublisher.publishEvent(new ReservationNotificationEvent(
            "reservation.confirmed", restaurantId, reservation.getId(),
            reservation.getCustomer().getId(), reservation.getCustomer().getEmail(),
            reservation.getCustomer().getFirstName() + " " + reservation.getCustomer().getLastName(),
            reservation.getPartySize(), reservation.getReservationDate(), reservation.getStartTime(),
            authenticatedUser.userId()
        ));
        realtimePublisher.publishReservationEvent(
            "reservation.confirmed",
            restaurantId,
            reservation.getId(),
            reservation.getReservationDate(),
            "Reservation confirmed"
        );
        return reservationMapper.toResponse(reservation);
    }

    @Transactional
    public ReservationResponse cancel(Long restaurantId, Long reservationId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        Reservation reservation = findReservationOrThrow(restaurantId, reservationId);
        ensureTransitionAllowed(reservation.getStatus(), ReservationStatus.CANCELLED);
        reservation.setStatus(ReservationStatus.CANCELLED);
        reservation.setCancelledAt(Instant.now());
        auditService.record(restaurantId, "Reservation", reservation.getId(), "reservation.cancelled", authenticatedUser.userId(), null);
        eventPublisher.publishEvent(new ReservationNotificationEvent(
            "reservation.cancelled", restaurantId, reservation.getId(),
            reservation.getCustomer().getId(), reservation.getCustomer().getEmail(),
            reservation.getCustomer().getFirstName() + " " + reservation.getCustomer().getLastName(),
            reservation.getPartySize(), reservation.getReservationDate(), reservation.getStartTime(),
            authenticatedUser.userId()
        ));
        realtimePublisher.publishReservationEvent(
            "reservation.cancelled",
            restaurantId,
            reservation.getId(),
            reservation.getReservationDate(),
            "Reservation cancelled"
        );
        return reservationMapper.toResponse(reservation);
    }

    @Transactional
    public ReservationResponse seat(Long restaurantId, Long reservationId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireSeatOrCompletePermission(authenticatedUser, restaurantId);

        Reservation reservation = findReservationOrThrow(restaurantId, reservationId);
        ensureTransitionAllowed(reservation.getStatus(), ReservationStatus.SEATED);
        reservation.setStatus(ReservationStatus.SEATED);
        if (reservation.getConfirmedAt() == null) {
            reservation.setConfirmedAt(Instant.now());
        }
        auditService.record(restaurantId, "Reservation", reservation.getId(), "reservation.seated", authenticatedUser.userId(), null);
        eventPublisher.publishEvent(new ReservationNotificationEvent(
            "reservation.seated", restaurantId, reservation.getId(),
            reservation.getCustomer().getId(), reservation.getCustomer().getEmail(),
            reservation.getCustomer().getFirstName() + " " + reservation.getCustomer().getLastName(),
            reservation.getPartySize(), reservation.getReservationDate(), reservation.getStartTime(),
            authenticatedUser.userId()
        ));
        realtimePublisher.publishReservationEvent(
            "reservation.updated",
            restaurantId,
            reservation.getId(),
            reservation.getReservationDate(),
            "Reservation seated"
        );
        return reservationMapper.toResponse(reservation);
    }

    @Transactional
    public ReservationResponse complete(Long restaurantId, Long reservationId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireSeatOrCompletePermission(authenticatedUser, restaurantId);

        Reservation reservation = findReservationOrThrow(restaurantId, reservationId);
        ensureTransitionAllowed(reservation.getStatus(), ReservationStatus.COMPLETED);
        reservation.setStatus(ReservationStatus.COMPLETED);
        auditService.record(restaurantId, "Reservation", reservation.getId(), "reservation.completed", authenticatedUser.userId(), null);
        eventPublisher.publishEvent(new ReservationNotificationEvent(
            "reservation.completed", restaurantId, reservation.getId(),
            reservation.getCustomer().getId(), reservation.getCustomer().getEmail(),
            reservation.getCustomer().getFirstName() + " " + reservation.getCustomer().getLastName(),
            reservation.getPartySize(), reservation.getReservationDate(), reservation.getStartTime(),
            authenticatedUser.userId()
        ));
        realtimePublisher.publishReservationEvent(
            "reservation.updated",
            restaurantId,
            reservation.getId(),
            reservation.getReservationDate(),
            "Reservation completed"
        );
        return reservationMapper.toResponse(reservation);
    }

    @Transactional
    public ReservationResponse noShow(Long restaurantId, Long reservationId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        Reservation reservation = findReservationOrThrow(restaurantId, reservationId);
        ensureTransitionAllowed(reservation.getStatus(), ReservationStatus.NO_SHOW);
        reservation.setStatus(ReservationStatus.NO_SHOW);
        auditService.record(restaurantId, "Reservation", reservation.getId(), "reservation.no_show", authenticatedUser.userId(), null);
        eventPublisher.publishEvent(new ReservationNotificationEvent(
            "reservation.no_show", restaurantId, reservation.getId(),
            reservation.getCustomer().getId(), reservation.getCustomer().getEmail(),
            reservation.getCustomer().getFirstName() + " " + reservation.getCustomer().getLastName(),
            reservation.getPartySize(), reservation.getReservationDate(), reservation.getStartTime(),
            authenticatedUser.userId()
        ));
        realtimePublisher.publishReservationEvent(
            "reservation.no_show",
            restaurantId,
            reservation.getId(),
            reservation.getReservationDate(),
            "Reservation marked as no-show"
        );
        return reservationMapper.toResponse(reservation);
    }

    private String toMetadata(String description, int partySize, LocalTime startTime) {
        try {
            Map<String, Object> meta = new LinkedHashMap<>();
            meta.put("description", description);
            meta.put("partySize", partySize);
            meta.put("startTime", startTime.toString());
            return new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(meta);
        } catch (Exception e) {
            return null;
        }
    }

    private Restaurant findAccessibleRestaurantOrThrow(Long restaurantId, AuthenticatedUser authenticatedUser) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant not found"));
        }

        return restaurantRepository.findAccessibleByIdAndUserId(restaurantId, authenticatedUser.userId())
            .orElseThrow(() -> new NotFoundException("Restaurant not found"));
    }

    private Customer findCustomerForRestaurantOrThrow(Long customerId, Long restaurantId) {
        return customerRepository.findByIdAndRestaurantId(customerId, restaurantId)
            .orElseThrow(() -> new NotFoundException("Customer not found for restaurant"));
    }

    private Reservation findReservationOrThrow(Long restaurantId, Long reservationId) {
        return reservationRepository.findByIdAndRestaurantId(reservationId, restaurantId)
            .orElseThrow(() -> new NotFoundException("Reservation not found"));
    }

    private void requireOwnerManagerOrAdmin(AuthenticatedUser authenticatedUser, Long restaurantId) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return;
        }

        boolean canManage = roleAssignmentRepository.findByUserId(authenticatedUser.userId()).stream()
            .anyMatch(assignment ->
                Objects.equals(assignment.getRestaurant().getId(), restaurantId)
                    && (assignment.getRole() == Role.RESTAURANT_OWNER || assignment.getRole() == Role.MANAGER)
            );

        if (!canManage) {
            throw new AccessDeniedException("Only PLATFORM_ADMIN, RESTAURANT_OWNER or MANAGER can modify reservations");
        }
    }

    private void requireSeatOrCompletePermission(AuthenticatedUser authenticatedUser, Long restaurantId) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return;
        }

        boolean canOperate = roleAssignmentRepository.findByUserId(authenticatedUser.userId()).stream()
            .anyMatch(assignment ->
                Objects.equals(assignment.getRestaurant().getId(), restaurantId)
                    && (
                        assignment.getRole() == Role.RESTAURANT_OWNER
                            || assignment.getRole() == Role.MANAGER
                            || assignment.getRole() == Role.WAITER
                    )
            );

        if (!canOperate) {
            throw new AccessDeniedException(
                "Only PLATFORM_ADMIN, RESTAURANT_OWNER, MANAGER or WAITER can seat or complete reservations"
            );
        }
    }

    private ReservationWindow validateAndBuildWindow(
        LocalTime startTime,
        LocalTime providedEndTime,
        Integer estimatedDurationMin,
        Integer cleaningBufferMin
    ) {
        if (startTime == null) {
            throw new IllegalArgumentException("startTime is required");
        }
        if (estimatedDurationMin == null || estimatedDurationMin <= 0) {
            throw new IllegalArgumentException("estimatedDurationMin must be greater than 0");
        }
        if (cleaningBufferMin == null || cleaningBufferMin < 0) {
            throw new IllegalArgumentException("cleaningBufferMin must be greater than or equal to 0");
        }

        LocalTime computedEndTime = startTime.plusMinutes(estimatedDurationMin.longValue());
        if (!computedEndTime.isAfter(startTime)) {
            throw new IllegalArgumentException("Reservation cannot cross midnight");
        }

        if (providedEndTime != null) {
            if (!providedEndTime.isAfter(startTime)) {
                throw new IllegalArgumentException("endTime must be after startTime");
            }
            if (!providedEndTime.equals(computedEndTime)) {
                throw new IllegalArgumentException("endTime must match startTime plus estimatedDurationMin");
            }
        }

        return new ReservationWindow(computedEndTime, estimatedDurationMin);
    }

    private void ensureTransitionAllowed(ReservationStatus currentStatus, ReservationStatus targetStatus) {
        boolean allowed = switch (targetStatus) {
            case CONFIRMED -> currentStatus == ReservationStatus.PENDING;
            case CANCELLED -> currentStatus == ReservationStatus.PENDING || currentStatus == ReservationStatus.CONFIRMED;
            case SEATED -> currentStatus == ReservationStatus.PENDING || currentStatus == ReservationStatus.CONFIRMED;
            case COMPLETED -> currentStatus == ReservationStatus.SEATED;
            case NO_SHOW -> currentStatus == ReservationStatus.PENDING || currentStatus == ReservationStatus.CONFIRMED;
            default -> false;
        };

        if (!allowed) {
            throw new ConflictException("Invalid reservation status transition");
        }
    }

    private void ensureAssignedBeforeConfirming(
        Long restaurantId,
        Reservation reservation,
        AuthenticatedUser authenticatedUser
    ) {
        if (!reservationAssignmentRepository.findByReservationIdAndActiveTrue(reservation.getId()).isEmpty()) {
            return;
        }

        AssignReservationResponse assignmentResponse = reservationAssignmentService.assign(
            restaurantId,
            reservation.getId(),
            authenticatedUser
        );

        if (assignmentResponse.assigned()) {
            return;
        }

        Map<String, Object> details = new LinkedHashMap<>();
        details.put("reservationId", reservation.getId());
        details.put("reasons", assignmentResponse.reasons());
        details.put("recommendedStartTime", assignmentResponse.recommendedStartTime());
        details.put("recommendationSummary", assignmentResponse.recommendationSummary());

        throw new ConflictException(
            "No se puede confirmar esta reserva porque no hay mesa disponible a la hora solicitada.",
            details
        );
    }

    private boolean isTerminalStatus(ReservationStatus status) {
        return status == ReservationStatus.CANCELLED || status == ReservationStatus.COMPLETED || status == ReservationStatus.NO_SHOW;
    }

    private String normalizeOptional(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private <T> void applyIfPresent(T value, Consumer<T> consumer) {
        if (value != null) {
            consumer.accept(value);
        }
    }

    private record ReservationWindow(LocalTime endTime, Integer estimatedDurationMin) {
    }
}
