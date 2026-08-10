package com.restaurantplanner.notification.service;

import com.restaurantplanner.notification.domain.NotificationType;
import com.restaurantplanner.notification.event.ReservationNotificationEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
public class ReservationNotificationEventListener {

    private static final Logger log = LoggerFactory.getLogger(ReservationNotificationEventListener.class);

    private final NotificationService notificationService;
    private final EmailService emailService;

    public ReservationNotificationEventListener(NotificationService notificationService,
                                                EmailService emailService) {
        this.notificationService = notificationService;
        this.emailService = emailService;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onReservationEvent(ReservationNotificationEvent event) {
        try {
            switch (event.eventType()) {
                case "reservation.created" -> handleCreated(event);
                case "reservation.confirmed" -> handleConfirmed(event);
                case "reservation.cancelled" -> handleCancelled(event);
                case "reservation.seated" -> handleSeated(event);
                case "reservation.completed" -> handleCompleted(event);
                case "reservation.no_show" -> handleNoShow(event);
                default -> log.debug("Unhandled notification event: {}", event.eventType());
            }
        } catch (Exception e) {
            log.warn("Error processing notification event {}: {}", event.eventType(), e.getMessage());
        }
    }

    private void handleCreated(ReservationNotificationEvent event) {
        notificationService.create(
            event.restaurantId(), null, NotificationType.RESERVATION_CREATED,
            "New reservation",
            "Reservation for " + event.partySize() + " guests at " + event.startTime() + " for " + event.customerName(),
            "Reservation", event.reservationId()
        );
    }

    private void handleConfirmed(ReservationNotificationEvent event) {
        notificationService.create(
            event.restaurantId(), null, NotificationType.RESERVATION_CONFIRMED,
            "Reservation confirmed",
            "Reservation for " + event.partySize() + " guests confirmed for " + event.customerName(),
            "Reservation", event.reservationId()
        );

        if (event.customerEmail() != null && !event.customerEmail().isBlank()) {
            emailService.send(
                event.customerEmail(),
                "Reservation confirmed - " + event.date(),
                "Your reservation for " + event.partySize() + " guests on " + event.date()
                    + " at " + event.startTime() + " has been confirmed.\n\nThank you for choosing us.",
                event.restaurantId(), event.reservationId()
            );
        }
    }

    private void handleCancelled(ReservationNotificationEvent event) {
        notificationService.create(
            event.restaurantId(), null, NotificationType.RESERVATION_CANCELLED,
            "Reservation cancelled",
            "Reservation for " + event.customerName() + " (" + event.partySize() + " guests) has been cancelled",
            "Reservation", event.reservationId()
        );

        if (event.customerEmail() != null && !event.customerEmail().isBlank()) {
            emailService.send(
                event.customerEmail(),
                "Reservation cancelled - " + event.date(),
                "Your reservation for " + event.partySize() + " guests on " + event.date()
                    + " at " + event.startTime() + " has been cancelled.\n\nPlease contact us if you have any questions.",
                event.restaurantId(), event.reservationId()
            );
        }
    }

    private void handleSeated(ReservationNotificationEvent event) {
        notificationService.create(
            event.restaurantId(), null, NotificationType.RESERVATION_SEATED,
            "Guests seated",
            event.customerName() + " (" + event.partySize() + " guests) has been seated",
            "Reservation", event.reservationId()
        );
    }

    private void handleCompleted(ReservationNotificationEvent event) {
        notificationService.create(
            event.restaurantId(), null, NotificationType.RESERVATION_COMPLETED,
            "Reservation completed",
            event.customerName() + " (" + event.partySize() + " guests) has completed service",
            "Reservation", event.reservationId()
        );
    }

    private void handleNoShow(ReservationNotificationEvent event) {
        notificationService.create(
            event.restaurantId(), null, NotificationType.RESERVATION_NO_SHOW,
            "No show",
            event.customerName() + " did not arrive (" + event.partySize() + " guests)",
            "Reservation", event.reservationId()
        );
    }
}
