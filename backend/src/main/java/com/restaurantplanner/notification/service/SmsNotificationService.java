package com.restaurantplanner.notification.service;

import com.restaurantplanner.audit.AuditService;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.common.api.NotFoundException;
import com.restaurantplanner.customer.domain.Customer;
import com.restaurantplanner.notification.config.SmsNotificationProperties;
import com.restaurantplanner.notification.domain.NotificationChannel;
import com.restaurantplanner.notification.domain.NotificationDeliveryStatus;
import com.restaurantplanner.notification.domain.NotificationLog;
import com.restaurantplanner.notification.domain.NotificationLogRepository;
import com.restaurantplanner.notification.domain.NotificationTemplateCode;
import com.restaurantplanner.reservation.domain.Reservation;
import com.restaurantplanner.reservation.domain.ReservationRepository;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Objects;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class SmsNotificationService {

    private final RestaurantRepository restaurantRepository;
    private final ReservationRepository reservationRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final NotificationLogRepository notificationLogRepository;
    private final NotificationProvider notificationProvider;
    private final SmsNotificationProperties properties;
    private final AuditService auditService;

    public SmsNotificationService(
        RestaurantRepository restaurantRepository,
        ReservationRepository reservationRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        NotificationLogRepository notificationLogRepository,
        NotificationProvider notificationProvider,
        SmsNotificationProperties properties,
        AuditService auditService
    ) {
        this.restaurantRepository = restaurantRepository;
        this.reservationRepository = reservationRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.notificationLogRepository = notificationLogRepository;
        this.notificationProvider = notificationProvider;
        this.properties = properties;
        this.auditService = auditService;
    }

    @Transactional
    public NotificationLog sendReservationConfirmation(
        Long restaurantId,
        Long reservationId,
        AuthenticatedUser authenticatedUser
    ) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        Reservation reservation = reservationRepository.findByIdAndRestaurantId(reservationId, restaurantId)
            .orElseThrow(() -> new NotFoundException("Reservation not found"));
        Customer customer = reservation.getCustomer();

        if (!StringUtils.hasText(customer.getPhone())) {
            NotificationLog failedLog = createFailureLog(
                reservation,
                NotificationTemplateCode.RESERVATION_CONFIRMATION,
                "Customer does not have a phone number"
            );
            auditService.record(
                restaurantId,
                "NotificationLog",
                failedLog.getId(),
                "sms.confirmation.failed",
                authenticatedUser.userId(),
                "{\"reason\":\"missing_phone\"}"
            );
            return failedLog;
        }

        if (!properties.isEnabled()) {
            NotificationLog failedLog = createFailureLog(
                reservation,
                NotificationTemplateCode.RESERVATION_CONFIRMATION,
                "SMS notifications are disabled"
            );
            auditService.record(
                restaurantId,
                "NotificationLog",
                failedLog.getId(),
                "sms.confirmation.failed",
                authenticatedUser.userId(),
                "{\"reason\":\"sms_disabled\"}"
            );
            return failedLog;
        }

        String message = buildReservationConfirmationMessage(reservation);
        try {
            NotificationProviderResult providerResult = notificationProvider.sendSms(
                new NotificationSendCommand(
                    restaurantId,
                    reservation.getId(),
                    customer.getId(),
                    customer.getPhone(),
                    message,
                    NotificationTemplateCode.RESERVATION_CONFIRMATION
                )
            );

            NotificationLog notificationLog = new NotificationLog();
            notificationLog.setRestaurant(reservation.getRestaurant());
            notificationLog.setReservation(reservation);
            notificationLog.setCustomer(customer);
            notificationLog.setChannel(NotificationChannel.SMS);
            notificationLog.setTemplateCode(NotificationTemplateCode.RESERVATION_CONFIRMATION);
            notificationLog.setStatus(NotificationDeliveryStatus.SENT);
            notificationLog.setProviderMessageId(providerResult.providerMessageId());
            notificationLog.setSentAt(Instant.now());
            NotificationLog saved = notificationLogRepository.save(notificationLog);

            auditService.record(
                restaurantId,
                "NotificationLog",
                saved.getId(),
                "sms.confirmation.sent",
                authenticatedUser.userId(),
                "{\"reservationId\":" + reservation.getId() + "}"
            );
            return saved;
        } catch (Exception exception) {
            NotificationLog failedLog = createFailureLog(
                reservation,
                NotificationTemplateCode.RESERVATION_CONFIRMATION,
                exception.getMessage()
            );
            auditService.record(
                restaurantId,
                "NotificationLog",
                failedLog.getId(),
                "sms.confirmation.failed",
                authenticatedUser.userId(),
                "{\"reservationId\":" + reservation.getId() + "}"
            );
            return failedLog;
        }
    }

    @Transactional(readOnly = true)
    public List<NotificationLog> findByRestaurantId(Long restaurantId, AuthenticatedUser authenticatedUser) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);
        return notificationLogRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurantId);
    }

    private NotificationLog createFailureLog(
        Reservation reservation,
        NotificationTemplateCode templateCode,
        String errorMessage
    ) {
        NotificationLog notificationLog = new NotificationLog();
        notificationLog.setRestaurant(reservation.getRestaurant());
        notificationLog.setReservation(reservation);
        notificationLog.setCustomer(reservation.getCustomer());
        notificationLog.setChannel(NotificationChannel.SMS);
        notificationLog.setTemplateCode(templateCode);
        notificationLog.setStatus(NotificationDeliveryStatus.FAILED);
        notificationLog.setErrorMessage(errorMessage);
        return notificationLogRepository.save(notificationLog);
    }

    private Restaurant findAccessibleRestaurantOrThrow(Long restaurantId, AuthenticatedUser authenticatedUser) {
        if (authenticatedUser.hasRole(Role.PLATFORM_ADMIN)) {
            return restaurantRepository.findById(restaurantId)
                .orElseThrow(() -> new NotFoundException("Restaurant not found"));
        }

        return restaurantRepository.findAccessibleByIdAndUserId(restaurantId, authenticatedUser.userId())
            .orElseThrow(() -> new NotFoundException("Restaurant not found"));
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
            throw new AccessDeniedException("Only PLATFORM_ADMIN, RESTAURANT_OWNER or MANAGER can send SMS notifications");
        }
    }

    @Transactional
    public NotificationLog sendReservationReminder(
        Long restaurantId,
        Long reservationId,
        AuthenticatedUser authenticatedUser
    ) {
        findAccessibleRestaurantOrThrow(restaurantId, authenticatedUser);
        requireOwnerManagerOrAdmin(authenticatedUser, restaurantId);

        Reservation reservation = reservationRepository.findByIdAndRestaurantId(reservationId, restaurantId)
            .orElseThrow(() -> new NotFoundException("Reservation not found"));
        Customer customer = reservation.getCustomer();

        if (!StringUtils.hasText(customer.getPhone())) {
            NotificationLog failedLog = createFailureLog(
                reservation,
                NotificationTemplateCode.RESERVATION_REMINDER,
                "Customer does not have a phone number"
            );
            auditService.record(
                restaurantId,
                "NotificationLog",
                failedLog.getId(),
                "sms.reminder.failed",
                authenticatedUser.userId(),
                "{\"reason\":\"missing_phone\"}"
            );
            return failedLog;
        }

        if (!properties.isEnabled()) {
            NotificationLog failedLog = createFailureLog(
                reservation,
                NotificationTemplateCode.RESERVATION_REMINDER,
                "SMS notifications are disabled"
            );
            auditService.record(
                restaurantId,
                "NotificationLog",
                failedLog.getId(),
                "sms.reminder.failed",
                authenticatedUser.userId(),
                "{\"reason\":\"sms_disabled\"}"
            );
            return failedLog;
        }

        String message = buildReservationReminderMessage(reservation);
        try {
            NotificationProviderResult providerResult = notificationProvider.sendSms(
                new NotificationSendCommand(
                    restaurantId,
                    reservation.getId(),
                    customer.getId(),
                    customer.getPhone(),
                    message,
                    NotificationTemplateCode.RESERVATION_REMINDER
                )
            );

            NotificationLog notificationLog = new NotificationLog();
            notificationLog.setRestaurant(reservation.getRestaurant());
            notificationLog.setReservation(reservation);
            notificationLog.setCustomer(customer);
            notificationLog.setChannel(NotificationChannel.SMS);
            notificationLog.setTemplateCode(NotificationTemplateCode.RESERVATION_REMINDER);
            notificationLog.setStatus(NotificationDeliveryStatus.SENT);
            notificationLog.setProviderMessageId(providerResult.providerMessageId());
            notificationLog.setSentAt(Instant.now());
            NotificationLog saved = notificationLogRepository.save(notificationLog);

            auditService.record(
                restaurantId,
                "NotificationLog",
                saved.getId(),
                "sms.reminder.sent",
                authenticatedUser.userId(),
                "{\"reservationId\":" + reservation.getId() + "}"
            );
            return saved;
        } catch (Exception exception) {
            NotificationLog failedLog = createFailureLog(
                reservation,
                NotificationTemplateCode.RESERVATION_REMINDER,
                exception.getMessage()
            );
            auditService.record(
                restaurantId,
                "NotificationLog",
                failedLog.getId(),
                "sms.reminder.failed",
                authenticatedUser.userId(),
                "{\"reservationId\":" + reservation.getId() + "}"
            );
            return failedLog;
        }
    }

    private String buildReservationConfirmationMessage(Reservation reservation) {
        String customerName = buildCustomerName(reservation.getCustomer());
        return "Hello " + customerName
            + ", your reservation for " + reservation.getPartySize() + " guests on "
            + reservation.getReservationDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            + " at " + reservation.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            + " has been recorded. Thank you.";
    }

    private String buildReservationReminderMessage(Reservation reservation) {
        String customerName = buildCustomerName(reservation.getCustomer());
        return "Reminder: " + customerName
            + ", you have a reservation for " + reservation.getPartySize() + " guests on "
            + reservation.getReservationDate().format(DateTimeFormatter.ISO_LOCAL_DATE)
            + " at " + reservation.getStartTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            + ". We look forward to welcoming you.";
    }

    private String buildCustomerName(Customer customer) {
        String firstName = customer.getFirstName() == null ? "" : customer.getFirstName().trim();
        String lastName = customer.getLastName() == null ? "" : customer.getLastName().trim();
        String fullName = (firstName + " " + lastName).trim();
        if (fullName.isEmpty()) {
            return "guest";
        }
        return fullName;
    }
}
