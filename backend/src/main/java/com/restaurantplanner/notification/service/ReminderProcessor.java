package com.restaurantplanner.notification.service;

import com.restaurantplanner.notification.domain.ScheduledNotification;
import com.restaurantplanner.notification.domain.ScheduledNotificationRepository;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ReminderProcessor {

    private static final Logger log = LoggerFactory.getLogger(ReminderProcessor.class);

    private final ScheduledNotificationRepository scheduledNotificationRepository;
    private final EmailService emailService;

    public ReminderProcessor(ScheduledNotificationRepository scheduledNotificationRepository,
                             EmailService emailService) {
        this.scheduledNotificationRepository = scheduledNotificationRepository;
        this.emailService = emailService;
    }

    @Scheduled(fixedRateString = "${app.notification.reminder-interval-ms:300000}")
    @Transactional
    public void processPendingReminders() {
        var pending = scheduledNotificationRepository.findBySentAtIsNullAndScheduledAtBefore(Instant.now());

        for (ScheduledNotification scheduled : pending) {
            try {
                emailService.send(
                    scheduled.getRecipientEmail(),
                    scheduled.getSubject(),
                    scheduled.getBody(),
                    scheduled.getRestaurantId(),
                    scheduled.getReservationId()
                );
                scheduled.setSentAt(Instant.now());
            } catch (Exception e) {
                log.warn("Failed to process reminder {}: {}", scheduled.getId(), e.getMessage());
                scheduled.setError(e.getMessage());
            }
        }
    }
}
