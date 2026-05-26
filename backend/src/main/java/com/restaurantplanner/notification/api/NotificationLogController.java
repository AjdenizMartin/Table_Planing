package com.restaurantplanner.notification.api;

import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.notification.service.SmsNotificationService;
import java.util.List;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/notifications")
public class NotificationLogController {

    private final SmsNotificationService smsNotificationService;
    private final NotificationLogMapper notificationLogMapper;

    public NotificationLogController(
        SmsNotificationService smsNotificationService,
        NotificationLogMapper notificationLogMapper
    ) {
        this.smsNotificationService = smsNotificationService;
        this.notificationLogMapper = notificationLogMapper;
    }

    @GetMapping(value = "/logs")
    public List<NotificationLogResponse> findLogs(
        @PathVariable Long restaurantId,
        Authentication authentication
    ) {
        return smsNotificationService.findByRestaurantId(
                restaurantId,
                (AuthenticatedUser) authentication.getPrincipal()
            )
            .stream()
            .map(notificationLogMapper::toResponse)
            .toList();
    }

    @GetMapping(params = "deliveryLogs")
    public List<NotificationLogResponse> findLogsFromBaseEndpoint(
        @PathVariable Long restaurantId,
        @RequestParam boolean deliveryLogs,
        Authentication authentication
    ) {
        if (!deliveryLogs) {
            return List.of();
        }

        return findLogs(restaurantId, authentication);
    }
}
