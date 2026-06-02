package com.restaurantplanner.notification;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.restaurantplanner.audit.AuditService;
import com.restaurantplanner.audit.domain.AuditLogRepository;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.customer.domain.Customer;
import com.restaurantplanner.notification.config.SmsNotificationProperties;
import com.restaurantplanner.notification.domain.NotificationDeliveryStatus;
import com.restaurantplanner.notification.domain.NotificationLog;
import com.restaurantplanner.notification.domain.NotificationLogRepository;
import com.restaurantplanner.notification.service.NotificationProvider;
import com.restaurantplanner.notification.service.NotificationProviderResult;
import com.restaurantplanner.notification.service.NotificationSendCommand;
import com.restaurantplanner.notification.service.SmsNotificationService;
import com.restaurantplanner.reservation.domain.Reservation;
import com.restaurantplanner.reservation.domain.ReservationChannel;
import com.restaurantplanner.reservation.domain.ReservationRepository;
import com.restaurantplanner.reservation.domain.ReservationStatus;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SmsNotificationServiceTest {

    private RestaurantRepository restaurantRepository;
    private ReservationRepository reservationRepository;
    private NotificationLogRepository logRepository;
    private NotificationProvider provider;
    private SmsNotificationProperties properties;
    private SmsNotificationService service;

    @BeforeEach
    void setUp() {
        restaurantRepository = org.mockito.Mockito.mock(RestaurantRepository.class);
        reservationRepository = org.mockito.Mockito.mock(ReservationRepository.class);
        logRepository = org.mockito.Mockito.mock(NotificationLogRepository.class);
        provider = org.mockito.Mockito.mock(NotificationProvider.class);
        properties = new SmsNotificationProperties();
        service = new SmsNotificationService(
            restaurantRepository,
            reservationRepository,
            org.mockito.Mockito.mock(RoleAssignmentRepository.class),
            logRepository,
            provider,
            properties,
            new AuditService(org.mockito.Mockito.mock(AuditLogRepository.class))
        );
    }

    @Test
    void sendReservationConfirmationUsesProviderAndStoresSentLog() {
        Reservation reservation = reservation("+34600111222");
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(reservation.getRestaurant()));
        when(reservationRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(reservation));
        when(provider.sendSms(any(NotificationSendCommand.class))).thenReturn(new NotificationProviderResult("fake-sms-1"));
        when(logRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationLog log = service.sendReservationConfirmation(1L, 10L, admin());

        assertEquals(NotificationDeliveryStatus.SENT, log.getStatus());
        assertEquals("fake-sms-1", log.getProviderMessageId());
        assertNotNull(log.getSentAt());
        verify(provider).sendSms(any(NotificationSendCommand.class));
    }

    @Test
    void disabledSmsCreatesFailedLogWithoutCallingProvider() {
        properties.setEnabled(false);
        Reservation reservation = reservation("+34600111222");
        when(restaurantRepository.findById(1L)).thenReturn(Optional.of(reservation.getRestaurant()));
        when(reservationRepository.findByIdAndRestaurantId(10L, 1L)).thenReturn(Optional.of(reservation));
        when(logRepository.save(any(NotificationLog.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationLog log = service.sendReservationConfirmation(1L, 10L, admin());

        assertEquals(NotificationDeliveryStatus.FAILED, log.getStatus());
        assertEquals("SMS notifications are disabled", log.getErrorMessage());
        verify(provider, never()).sendSms(any(NotificationSendCommand.class));
    }

    private AuthenticatedUser admin() {
        return new AuthenticatedUser(99L, "admin@example.com", "Admin", Set.of(Role.PLATFORM_ADMIN), Set.of());
    }

    private Reservation reservation(String phone) {
        Restaurant restaurant = new Restaurant();
        restaurant.setId(1L);
        restaurant.setName("Main");
        restaurant.setTimezone("Europe/Madrid");

        Customer customer = new Customer();
        customer.setId(2L);
        customer.setRestaurant(restaurant);
        customer.setFirstName("Ana");
        customer.setLastName("Lopez");
        customer.setPhone(phone);

        Reservation reservation = new Reservation();
        reservation.setId(10L);
        reservation.setRestaurant(restaurant);
        reservation.setCustomer(customer);
        reservation.setChannel(ReservationChannel.MANUAL);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setPartySize(2);
        reservation.setReservationDate(LocalDate.of(2026, 6, 2));
        reservation.setStartTime(LocalTime.of(20, 0));
        reservation.setEndTime(LocalTime.of(21, 30));
        reservation.setEstimatedDurationMin(90);
        reservation.setCleaningBufferMin(15);
        return reservation;
    }
}
