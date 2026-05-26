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
            "Nueva reserva",
            "Reserva de " + event.partySize() + " comensales a las " + event.startTime() + " para " + event.customerName(),
            "Reservation", event.reservationId()
        );
    }

    private void handleConfirmed(ReservationNotificationEvent event) {
        notificationService.create(
            event.restaurantId(), null, NotificationType.RESERVATION_CONFIRMED,
            "Reserva confirmada",
            "Reserva de " + event.partySize() + " comensales confirmada para " + event.customerName(),
            "Reservation", event.reservationId()
        );

        if (event.customerEmail() != null && !event.customerEmail().isBlank()) {
            emailService.send(
                event.customerEmail(),
                "Reserva confirmada - " + event.date(),
                "Tu reserva para " + event.partySize() + " personas el " + event.date()
                    + " a las " + event.startTime() + " ha sido confirmada.\n\nGracias por tu preferencia.",
                event.restaurantId(), event.reservationId()
            );
        }
    }

    private void handleCancelled(ReservationNotificationEvent event) {
        notificationService.create(
            event.restaurantId(), null, NotificationType.RESERVATION_CANCELLED,
            "Reserva cancelada",
            "Reserva de " + event.customerName() + " (" + event.partySize() + " comensales) ha sido cancelada",
            "Reservation", event.reservationId()
        );

        if (event.customerEmail() != null && !event.customerEmail().isBlank()) {
            emailService.send(
                event.customerEmail(),
                "Reserva cancelada - " + event.date(),
                "Tu reserva para " + event.partySize() + " personas el " + event.date()
                    + " a las " + event.startTime() + " ha sido cancelada.\n\nSi tienes dudas, contactanos.",
                event.restaurantId(), event.reservationId()
            );
        }
    }

    private void handleSeated(ReservationNotificationEvent event) {
        notificationService.create(
            event.restaurantId(), null, NotificationType.RESERVATION_SEATED,
            "Comensales sentados",
            event.customerName() + " (" + event.partySize() + " comensales) se sento",
            "Reservation", event.reservationId()
        );
    }

    private void handleCompleted(ReservationNotificationEvent event) {
        notificationService.create(
            event.restaurantId(), null, NotificationType.RESERVATION_COMPLETED,
            "Reserva finalizada",
            event.customerName() + " (" + event.partySize() + " comensales) finalizo",
            "Reservation", event.reservationId()
        );
    }

    private void handleNoShow(ReservationNotificationEvent event) {
        notificationService.create(
            event.restaurantId(), null, NotificationType.RESERVATION_NO_SHOW,
            "No show",
            event.customerName() + " no se presento (" + event.partySize() + " comensales)",
            "Reservation", event.reservationId()
        );
    }
}
