package com.restaurantplanner.notification.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    List<Notification> findByRestaurantIdAndUserIdOrderByCreatedAtDesc(Long restaurantId, Long userId, Pageable pageable);

    List<Notification> findByRestaurantIdAndUserIdAndIsReadFalseOrderByCreatedAtDesc(Long restaurantId, Long userId, Pageable pageable);

    int countByRestaurantIdAndUserIdAndIsReadFalse(Long restaurantId, Long userId);

    Optional<Notification> findByIdAndRestaurantId(Long id, Long restaurantId);
}
