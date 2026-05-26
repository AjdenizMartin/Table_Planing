package com.restaurantplanner.notification.api;

import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.notification.service.NotificationService;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/restaurants/{restaurantId}/notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationMapper notificationMapper;

    public NotificationController(NotificationService notificationService, NotificationMapper notificationMapper) {
        this.notificationService = notificationService;
        this.notificationMapper = notificationMapper;
    }

    @GetMapping
    public List<NotificationResponse> findAll(
        @PathVariable Long restaurantId,
        @RequestParam(defaultValue = "false") boolean unreadOnly,
        @RequestParam(defaultValue = "50") int limit,
        Authentication authentication
    ) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        return notificationService.findByRestaurantIdAndUserId(restaurantId, user.userId(), limit, unreadOnly)
            .stream()
            .map(notificationMapper::toResponse)
            .toList();
    }

    @GetMapping("/unread-count")
    public Map<String, Integer> unreadCount(@PathVariable Long restaurantId, Authentication authentication) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        return Map.of("count", notificationService.countUnread(restaurantId, user.userId()));
    }

    @PatchMapping("/{notificationId}/read")
    public void markAsRead(
        @PathVariable Long restaurantId,
        @PathVariable Long notificationId,
        Authentication authentication
    ) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        notificationService.markAsRead(notificationId, restaurantId, user.userId());
    }

    @PatchMapping("/read-all")
    public Map<String, Integer> markAllAsRead(@PathVariable Long restaurantId, Authentication authentication) {
        var user = (AuthenticatedUser) authentication.getPrincipal();
        return Map.of("marked", notificationService.markAllAsRead(restaurantId, user.userId()));
    }
}
