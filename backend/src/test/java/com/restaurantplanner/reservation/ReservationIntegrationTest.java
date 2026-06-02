package com.restaurantplanner.reservation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantplanner.auth.domain.RefreshTokenRepository;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignment;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.customer.domain.Customer;
import com.restaurantplanner.customer.domain.CustomerRepository;
import com.restaurantplanner.diningroom.domain.DiningRoom;
import com.restaurantplanner.diningroom.domain.DiningRoomRepository;
import com.restaurantplanner.reservation.domain.Reservation;
import com.restaurantplanner.reservation.domain.ReservationAssignmentRepository;
import com.restaurantplanner.reservation.domain.ReservationChannel;
import com.restaurantplanner.reservation.domain.ReservationRepository;
import com.restaurantplanner.reservation.domain.ReservationStatus;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.restaurant.domain.RestaurantStatus;
import com.restaurantplanner.table.domain.RestaurantTable;
import com.restaurantplanner.table.domain.RestaurantTableRepository;
import com.restaurantplanner.user.domain.User;
import com.restaurantplanner.user.domain.UserRepository;
import com.restaurantplanner.user.domain.UserStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import jakarta.persistence.EntityManager;
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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class ReservationIntegrationTest {

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
    private DiningRoomRepository diningRoomRepository;

    @Autowired
    private RestaurantTableRepository restaurantTableRepository;

    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void clearEntityManager() {
        entityManager.clear();
    }

    @Test
    void createReservation() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        User manager = createUser("manager@example.com", "secret123", "Manager");
        assignRole(manager, restaurant, Role.MANAGER);
        String accessToken = loginAndExtractAccessToken("manager@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": %d,
                      "channel": "MANUAL",
                      "partySize": 4,
                      "reservationDate": "2026-05-26",
                      "startTime": "20:00:00",
                      "estimatedDurationMin": 90,
                      "cleaningBufferMin": 15,
                      "specialRequests": "Birthday table",
                      "accessibilityRequired": false
                    }
                    """.formatted(customer.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.status").value("PENDING"))
            .andExpect(jsonPath("$.endTime").value("21:30:00"));
    }

    @Test
    void failWhenCustomerBelongsToOtherRestaurant() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Restaurant otherRestaurant = createRestaurant("Other", "other");
        Customer foreignCustomer = createCustomer(otherRestaurant, "Eva", "Santos", "+34600555666");
        User owner = createUser("owner@example.com", "secret123", "Owner");
        assignRole(owner, restaurant, Role.RESTAURANT_OWNER);
        String accessToken = loginAndExtractAccessToken("owner@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": %d,
                      "partySize": 2,
                      "reservationDate": "2026-05-26",
                      "startTime": "20:00:00",
                      "estimatedDurationMin": 60,
                      "cleaningBufferMin": 10,
                      "accessibilityRequired": false
                    }
                    """.formatted(foreignCustomer.getId())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void confirmReservation() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom diningRoom = createDiningRoom(restaurant, "Main Room");
        createTable(restaurant, diningRoom, "T1", 2, 4);
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer, ReservationStatus.PENDING, LocalDate.of(2026, 5, 26));
        User manager = createUser("manager@example.com", "secret123", "Manager");
        assignRole(manager, restaurant, Role.MANAGER);
        String accessToken = loginAndExtractAccessToken("manager@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/confirm", restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CONFIRMED"))
            .andExpect(jsonPath("$.confirmedAt").exists());
    }

    @Test
    void cancelReservation() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer, ReservationStatus.CONFIRMED, LocalDate.of(2026, 5, 26));
        reservation.setConfirmedAt(java.time.Instant.now());
        reservationRepository.save(reservation);
        User owner = createUser("owner@example.com", "secret123", "Owner");
        assignRole(owner, restaurant, Role.RESTAURANT_OWNER);
        String accessToken = loginAndExtractAccessToken("owner@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/cancel", restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELLED"))
            .andExpect(jsonPath("$.cancelledAt").exists());
    }

    @Test
    void markNoShow() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer, ReservationStatus.PENDING, LocalDate.of(2026, 5, 26));
        User manager = createUser("manager@example.com", "secret123", "Manager");
        assignRole(manager, restaurant, Role.MANAGER);
        String accessToken = loginAndExtractAccessToken("manager@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/no-show", restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("NO_SHOW"));
    }

    @Test
    void rejectInvalidTransition() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer, ReservationStatus.CANCELLED, LocalDate.of(2026, 5, 26));
        User manager = createUser("manager@example.com", "secret123", "Manager");
        assignRole(manager, restaurant, Role.MANAGER);
        String accessToken = loginAndExtractAccessToken("manager@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/confirm", restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void listReservationsByDate() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        createReservation(restaurant, customer, ReservationStatus.PENDING, LocalDate.of(2026, 5, 26));
        createReservation(restaurant, customer, ReservationStatus.CONFIRMED, LocalDate.of(2026, 5, 27));
        User waiter = createUser("waiter@example.com", "secret123", "Waiter");
        assignRole(waiter, restaurant, Role.WAITER);
        String accessToken = loginAndExtractAccessToken("waiter@example.com", "secret123");

        mockMvc.perform(get("/api/restaurants/{restaurantId}/reservations", restaurant.getId())
                .param("date", "2026-05-26")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].reservationDate").value("2026-05-26"));
    }

    @Test
    void searchByCustomerName() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer ana = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Customer carlos = createCustomer(restaurant, "Carlos", "Ruiz", "+34600333444");
        createReservation(restaurant, ana, ReservationStatus.PENDING, LocalDate.of(2026, 5, 26));
        createReservation(restaurant, carlos, ReservationStatus.CONFIRMED, LocalDate.of(2026, 5, 27));
        User waiter = createUser("waiter@example.com", "secret123", "Waiter");
        assignRole(waiter, restaurant, Role.WAITER);
        String accessToken = loginAndExtractAccessToken("waiter@example.com", "secret123");

        mockMvc.perform(get("/api/restaurants/{restaurantId}/reservations/search", restaurant.getId())
                .param("customerQuery", "Ana")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].customerFirstName").value("Ana"));
    }

    @Test
    void searchByCustomerLastName() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer ana = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        createReservation(restaurant, ana, ReservationStatus.PENDING, LocalDate.of(2026, 5, 26));
        User waiter = createUser("waiter@example.com", "secret123", "Waiter");
        assignRole(waiter, restaurant, Role.WAITER);
        String accessToken = loginAndExtractAccessToken("waiter@example.com", "secret123");

        mockMvc.perform(get("/api/restaurants/{restaurantId}/reservations/search", restaurant.getId())
                .param("customerQuery", "Lopez")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void searchByStatus() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer ana = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        createReservation(restaurant, ana, ReservationStatus.PENDING, LocalDate.of(2026, 5, 26));
        createReservation(restaurant, ana, ReservationStatus.CONFIRMED, LocalDate.of(2026, 5, 27));
        User waiter = createUser("waiter@example.com", "secret123", "Waiter");
        assignRole(waiter, restaurant, Role.WAITER);
        String accessToken = loginAndExtractAccessToken("waiter@example.com", "secret123");

        mockMvc.perform(get("/api/restaurants/{restaurantId}/reservations/search", restaurant.getId())
                .param("status", "CONFIRMED")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    }

    @Test
    void searchByDateRange() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer ana = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        createReservation(restaurant, ana, ReservationStatus.PENDING, LocalDate.of(2026, 5, 26));
        createReservation(restaurant, ana, ReservationStatus.CONFIRMED, LocalDate.of(2026, 5, 28));
        User waiter = createUser("waiter@example.com", "secret123", "Waiter");
        assignRole(waiter, restaurant, Role.WAITER);
        String accessToken = loginAndExtractAccessToken("waiter@example.com", "secret123");

        mockMvc.perform(get("/api/restaurants/{restaurantId}/reservations/search", restaurant.getId())
                .param("dateFrom", "2026-05-27")
                .param("dateTo", "2026-05-29")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].reservationDate").value("2026-05-28"));
    }

    @Test
    void searchByPartySize() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer ana = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        createReservation(restaurant, ana, ReservationStatus.PENDING, LocalDate.of(2026, 5, 26));
        createReservation(restaurant, ana, ReservationStatus.CONFIRMED, LocalDate.of(2026, 5, 27));
        User waiter = createUser("waiter@example.com", "secret123", "Waiter");
        assignRole(waiter, restaurant, Role.WAITER);
        String accessToken = loginAndExtractAccessToken("waiter@example.com", "secret123");

        mockMvc.perform(get("/api/restaurants/{restaurantId}/reservations/search", restaurant.getId())
                .param("partySize", "4")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void searchCombinedFilters() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer ana = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Customer carlos = createCustomer(restaurant, "Carlos", "Ruiz", "+34600333444");
        createReservation(restaurant, ana, ReservationStatus.PENDING, LocalDate.of(2026, 5, 26));
        createReservation(restaurant, carlos, ReservationStatus.CONFIRMED, LocalDate.of(2026, 5, 27));
        User waiter = createUser("waiter@example.com", "secret123", "Waiter");
        assignRole(waiter, restaurant, Role.WAITER);
        String accessToken = loginAndExtractAccessToken("waiter@example.com", "secret123");

        mockMvc.perform(get("/api/restaurants/{restaurantId}/reservations/search", restaurant.getId())
                .param("customerQuery", "Ruiz")
                .param("status", "CONFIRMED")
                .param("dateFrom", "2026-05-26")
                .param("dateTo", "2026-05-28")
                .param("partySize", "4")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void searchWithNoFiltersReturnsAll() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer ana = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        createReservation(restaurant, ana, ReservationStatus.PENDING, LocalDate.of(2026, 5, 26));
        createReservation(restaurant, ana, ReservationStatus.CONFIRMED, LocalDate.of(2026, 5, 27));
        User waiter = createUser("waiter@example.com", "secret123", "Waiter");
        assignRole(waiter, restaurant, Role.WAITER);
        String accessToken = loginAndExtractAccessToken("waiter@example.com", "secret123");

        mockMvc.perform(get("/api/restaurants/{restaurantId}/reservations", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2));
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

    private Reservation createReservation(
        Restaurant restaurant,
        Customer customer,
        ReservationStatus status,
        LocalDate reservationDate
    ) {
        Reservation reservation = new Reservation();
        reservation.setRestaurant(restaurant);
        reservation.setCustomer(customer);
        reservation.setChannel(ReservationChannel.MANUAL);
        reservation.setStatus(status);
        reservation.setPartySize(4);
        reservation.setReservationDate(reservationDate);
        reservation.setStartTime(LocalTime.of(20, 0));
        reservation.setEndTime(LocalTime.of(21, 30));
        reservation.setEstimatedDurationMin(90);
        reservation.setCleaningBufferMin(15);
        reservation.setSpecialRequests("Window seat");
        reservation.setAccessibilityRequired(false);
        return reservationRepository.save(reservation);
    }

    private DiningRoom createDiningRoom(Restaurant restaurant, String name) {
        DiningRoom diningRoom = new DiningRoom();
        diningRoom.setRestaurant(restaurant);
        diningRoom.setName(name);
        diningRoom.setPriority(1);
        diningRoom.setAccessible(true);
        diningRoom.setActive(true);
        diningRoom.setLayoutWidth(1200);
        diningRoom.setLayoutHeight(800);
        return diningRoomRepository.save(diningRoom);
    }

    private RestaurantTable createTable(Restaurant restaurant, DiningRoom diningRoom, String code, int minCapacity, int maxCapacity) {
        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setDiningRoom(diningRoom);
        table.setCode(code);
        table.setLabel(code);
        table.setMinCapacity(minCapacity);
        table.setMaxCapacity(maxCapacity);
        table.setShape("RECTANGLE");
        table.setX(100);
        table.setY(100);
        table.setWidth(120);
        table.setHeight(80);
        table.setActive(true);
        return restaurantTableRepository.save(table);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
