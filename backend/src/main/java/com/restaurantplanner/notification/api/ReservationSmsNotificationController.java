package com.restaurantplanner.notification.api;

import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.notification.service.SmsNotificationService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/reservations/{reservationId}/notifications")
public class ReservationSmsNotificationController {

    private final SmsNotificationService smsNotificationService;
    private final NotificationLogMapper notificationLogMapper;

    public ReservationSmsNotificationController(
        SmsNotificationService smsNotificationService,
        NotificationLogMapper notificationLogMapper
    ) {
        this.smsNotificationService = smsNotificationService;
        this.notificationLogMapper = notificationLogMapper;
    }

    @PostMapping("/confirmation")
    public NotificationLogResponse sendConfirmation(
        @PathVariable Long restaurantId,
        @PathVariable Long reservationId,
        Authentication authentication
    ) {
        return notificationLogMapper.toResponse(
            smsNotificationService.sendReservationConfirmation(
                restaurantId,
                reservationId,
                (AuthenticatedUser) authentication.getPrincipal()
            )
        );
    }
}
