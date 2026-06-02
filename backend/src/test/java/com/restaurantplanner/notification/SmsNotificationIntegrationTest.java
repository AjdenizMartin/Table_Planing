package com.restaurantplanner.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantplanner.auth.domain.RefreshTokenRepository;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignment;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.customer.domain.Customer;
import com.restaurantplanner.customer.domain.CustomerRepository;
import com.restaurantplanner.notification.domain.NotificationLogRepository;
import com.restaurantplanner.reservation.domain.Reservation;
import com.restaurantplanner.reservation.domain.ReservationAssignmentRepository;
import com.restaurantplanner.reservation.domain.ReservationChannel;
import com.restaurantplanner.reservation.domain.ReservationRepository;
import com.restaurantplanner.reservation.domain.ReservationStatus;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.restaurant.domain.RestaurantStatus;
import com.restaurantplanner.user.domain.User;
import com.restaurantplanner.user.domain.UserRepository;
import com.restaurantplanner.user.domain.UserStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.springframework.test.context.jdbc.Sql;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "app.notification.sms.enabled=true",
    "app.notification.sms.provider=fake"
})
@AutoConfigureMockMvc
@Testcontainers
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class SmsNotificationIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RestaurantRepository restaurantRepository;

    @Autowired
    private RoleAssignmentRepository roleAssignmentRepository;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private CustomerRepository customerRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationAssignmentRepository reservationAssignmentRepository;

    @Autowired
    private NotificationLogRepository notificationLogRepository;

    @Test
    void sendsSmsWithFakeProvider() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/notifications/confirmation",
                restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.channel").value("SMS"))
            .andExpect(jsonPath("$.templateCode").value("RESERVATION_CONFIRMATION"))
            .andExpect(jsonPath("$.status").value("SENT"))
            .andExpect(jsonPath("$.providerMessageId").value(org.hamcrest.Matchers.startsWith("fake-sms-")));
    }

    @Test
    void registersNotificationLog() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/notifications/confirmation",
                restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk());

        assertThat(notificationLogRepository.findByRestaurantIdOrderByCreatedAtDesc(restaurant.getId()))
            .hasSize(1)
            .first()
            .satisfies(log -> {
                assertThat(log.getReservation().getId()).isEqualTo(reservation.getId());
                assertThat(log.getCustomer().getId()).isEqualTo(customer.getId());
                assertThat(log.getSentAt()).isNotNull();
            });
    }

    @Test
    void providerFailureIsRecorded() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "fail-number");
        Reservation reservation = createReservation(restaurant, customer);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/notifications/confirmation",
                restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.errorMessage").value("Fake SMS provider forced failure"));
    }

    @Test
    void doesNotSendNotificationForReservationOfAnotherRestaurant() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Restaurant otherRestaurant = createRestaurant("Other", "other");
        Customer customer = createCustomer(otherRestaurant, "Eva", "Santos", "+34600555666");
        Reservation reservation = createReservation(otherRestaurant, customer);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/notifications/confirmation",
                restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isNotFound());
    }

    @Test
    void doesNotSendWhenCustomerHasNoPhone() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", null);
        Reservation reservation = createReservation(restaurant, customer);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/notifications/confirmation",
                restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("FAILED"))
            .andExpect(jsonPath("$.errorMessage").value("Customer does not have a phone number"));
    }

    @Test
    void listsNotificationLogs() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/notifications/confirmation",
                restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/restaurants/{restaurantId}/notifications/logs", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].channel").value("SMS"));
    }

    private String loginWithRole(Restaurant restaurant, String email, Role role) throws Exception {
        User user = createUser(email, "secret123", role.name());
        assignRole(user, restaurant, role);
        return loginAndExtractAccessToken(email, "secret123");
    }

    private String loginAndExtractAccessToken(String email, String password) throws Exception {
        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "%s",
                      "password": "%s"
                    }
                    """.formatted(email, password)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        return json.get("accessToken").asText();
    }

    private Restaurant createRestaurant(String name, String slug) {
        Restaurant restaurant = new Restaurant();
        restaurant.setName(name);
        restaurant.setSlug(slug);
        restaurant.setTimezone("Europe/Dublin");
        restaurant.setPhone("+353000000");
        restaurant.setStatus(RestaurantStatus.ACTIVE);
        restaurant.setSettingsJson("{}");
        return restaurantRepository.save(restaurant);
    }

    private User createUser(String email, String password, String name) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(password));
        user.setName(name);
        user.setStatus(UserStatus.ACTIVE);
        return userRepository.save(user);
    }

    private void assignRole(User user, Restaurant restaurant, Role role) {
        RoleAssignment assignment = new RoleAssignment();
        assignment.setUser(user);
        assignment.setRestaurant(restaurant);
        assignment.setRole(role);
        roleAssignmentRepository.save(assignment);
    }

    private Customer createCustomer(Restaurant restaurant, String firstName, String lastName, String phone) {
        Customer customer = new Customer();
        customer.setRestaurant(restaurant);
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setPhone(phone);
        customer.setEmail(firstName.toLowerCase() + "@example.com");
        customer.setNotes("Existing customer");
        customer.setTagsJson("[]");
        return customerRepository.save(customer);
    }

    private Reservation createReservation(Restaurant restaurant, Customer customer) {
        Reservation reservation = new Reservation();
        reservation.setRestaurant(restaurant);
        reservation.setCustomer(customer);
        reservation.setChannel(ReservationChannel.MANUAL);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setPartySize(4);
        reservation.setReservationDate(LocalDate.of(2026, 5, 26));
        reservation.setStartTime(LocalTime.of(20, 0));
        reservation.setEndTime(LocalTime.of(21, 30));
        reservation.setEstimatedDurationMin(90);
        reservation.setCleaningBufferMin(15);
        reservation.setSpecialRequests("Window seat");
        reservation.setAccessibilityRequired(false);
        return reservationRepository.save(reservation);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
