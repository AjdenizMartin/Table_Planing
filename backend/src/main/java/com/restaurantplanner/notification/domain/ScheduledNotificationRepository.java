package com.restaurantplanner.notification.domain;

import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ScheduledNotificationRepository extends JpaRepository<ScheduledNotification, Long> {

    List<ScheduledNotification> findBySentAtIsNullAndScheduledAtBefore(Instant now);
}
