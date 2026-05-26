package com.restaurantplanner.notification.service;

import com.restaurantplanner.notification.domain.Notification;
import com.restaurantplanner.notification.domain.NotificationRepository;
import com.restaurantplanner.notification.domain.NotificationType;
import com.restaurantplanner.realtime.RestaurantRealtimePublisher;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

    private static final Logger log = LoggerFactory.getLogger(NotificationService.class);

    private final NotificationRepository notificationRepository;
    private final RestaurantRealtimePublisher realtimePublisher;

    public NotificationService(NotificationRepository notificationRepository,
                               RestaurantRealtimePublisher realtimePublisher) {
        this.notificationRepository = notificationRepository;
        this.realtimePublisher = realtimePublisher;
    }

    @Transactional
    public Notification create(Long restaurantId, Long userId, NotificationType type, String title, String body,
                               String entityType, Long entityId) {
        Notification notification = new Notification(restaurantId, userId, type, title, body, entityType, entityId);
        Notification saved = notificationRepository.save(notification);
        realtimePublisher.publishNotification(restaurantId, saved);
        return saved;
    }

    @Transactional(readOnly = true)
    public List<Notification> findByRestaurantIdAndUserId(Long restaurantId, Long userId, int limit, boolean unreadOnly) {
        if (unreadOnly) {
            return notificationRepository.findByRestaurantIdAndUserIdAndIsReadFalseOrderByCreatedAtDesc(
                restaurantId, userId, PageRequest.of(0, limit));
        }
        return notificationRepository.findByRestaurantIdAndUserIdOrderByCreatedAtDesc(
            restaurantId, userId, PageRequest.of(0, limit));
    }

    @Transactional(readOnly = true)
    public int countUnread(Long restaurantId, Long userId) {
        return notificationRepository.countByRestaurantIdAndUserIdAndIsReadFalse(restaurantId, userId);
    }

    @Transactional
    public void markAsRead(Long notificationId, Long restaurantId, Long userId) {
        Notification notification = notificationRepository.findByIdAndRestaurantId(notificationId, restaurantId)
            .orElse(null);
        if (notification != null && !notification.isRead()) {
            notification.setRead(true);
        }
    }

    @Transactional
    public int markAllAsRead(Long restaurantId, Long userId) {
        List<Notification> unread = notificationRepository
            .findByRestaurantIdAndUserIdAndIsReadFalseOrderByCreatedAtDesc(restaurantId, userId, PageRequest.of(0, 1000));
        for (Notification n : unread) {
            n.setRead(true);
        }
        return unread.size();
    }
}
