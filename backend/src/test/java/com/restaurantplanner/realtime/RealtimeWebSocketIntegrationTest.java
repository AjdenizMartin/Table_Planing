package com.restaurantplanner.realtime;

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
import java.lang.reflect.Type;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompFrameHandler;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@AutoConfigureMockMvc
@Testcontainers
class RealtimeWebSocketIntegrationTest {

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

    @LocalServerPort
    private int port;

    private WebSocketStompClient stompClient;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        reservationAssignmentRepository.deleteAll();
        reservationRepository.deleteAll();
        restaurantTableRepository.deleteAll();
        diningRoomRepository.deleteAll();
        customerRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();

        stompClient = new WebSocketStompClient(new StandardWebSocketClient());
        stompClient.setMessageConverter(new MappingJackson2MessageConverter());
    }

    @AfterEach
    void tearDown() {
        if (stompClient != null) {
            stompClient.stop();
        }
    }

    @Test
    void emitsEventWhenReservationIsCreated() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        BlockingQueue<RestaurantRealtimeEvent> queue = subscribe(
            token,
            "/topic/restaurants/" + restaurant.getId() + "/reservations"
        );

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
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
            .andExpect(status().isCreated());

        RestaurantRealtimeEvent event = queue.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.type()).isEqualTo("reservation.created");
        assertThat(event.restaurantId()).isEqualTo(restaurant.getId());
        assertThat(event.reservationId()).isNotNull();
        assertThat(event.date()).isEqualTo(LocalDate.of(2026, 5, 26));
    }

    @Test
    void emitsEventWhenReservationIsAssigned() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        createTable(restaurant, room, "A1", 2, 4, true);
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(
            restaurant,
            customer,
            ReservationStatus.PENDING,
            LocalDate.of(2026, 5, 26)
        );
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        BlockingQueue<RestaurantRealtimeEvent> queue = subscribe(
            token,
            "/topic/restaurants/" + restaurant.getId() + "/reservations"
        );

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/assign", restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk());

        RestaurantRealtimeEvent event = queue.poll(5, TimeUnit.SECONDS);
        assertThat(event).isNotNull();
        assertThat(event.type()).isEqualTo("reservation.assigned");
        assertThat(event.restaurantId()).isEqualTo(restaurant.getId());
        assertThat(event.reservationId()).isEqualTo(reservation.getId());
    }

    @Test
    void doesNotEmitEventsToDifferentRestaurantTopic() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Restaurant otherRestaurant = createRestaurant("Other", "other");
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        String originToken = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);
        String otherToken = loginWithRole(otherRestaurant, "other-manager@example.com", Role.MANAGER);

        BlockingQueue<RestaurantRealtimeEvent> queue = subscribe(
            otherToken,
            "/topic/restaurants/" + otherRestaurant.getId() + "/reservations"
        );

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(originToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "customerId": %d,
                      "channel": "MANUAL",
                      "partySize": 2,
                      "reservationDate": "2026-05-26",
                      "startTime": "19:30:00",
                      "estimatedDurationMin": 60,
                      "cleaningBufferMin": 15,
                      "accessibilityRequired": false
                    }
                    """.formatted(customer.getId())))
            .andExpect(status().isCreated());

        RestaurantRealtimeEvent event = queue.poll(2, TimeUnit.SECONDS);
        assertThat(event).isNull();
    }

    private BlockingQueue<RestaurantRealtimeEvent> subscribe(String token, String destination) throws Exception {
        BlockingQueue<RestaurantRealtimeEvent> queue = new LinkedBlockingQueue<>();
        StompSession session = connect(token);
        session.subscribe(destination, new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return RestaurantRealtimeEvent.class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.offer((RestaurantRealtimeEvent) payload);
            }
        });
        return queue;
    }

    private StompSession connect(String token) throws Exception {
        CompletableFuture<StompSession> future = stompClient.connectAsync(
            "ws://localhost:" + port + "/ws?access_token=" + token,
            new StompSessionHandlerAdapter() {
            }
        );
        return future.get(5, TimeUnit.SECONDS);
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

    private DiningRoom createDiningRoom(
        Restaurant restaurant,
        String name,
        int priority,
        boolean accessible,
        boolean active
    ) {
        DiningRoom diningRoom = new DiningRoom();
        diningRoom.setRestaurant(restaurant);
        diningRoom.setName(name);
        diningRoom.setPriority(priority);
        diningRoom.setAccessible(accessible);
        diningRoom.setActive(active);
        diningRoom.setLayoutWidth(1200);
        diningRoom.setLayoutHeight(800);
        return diningRoomRepository.save(diningRoom);
    }

    private RestaurantTable createTable(
        Restaurant restaurant,
        DiningRoom diningRoom,
        String code,
        int minCapacity,
        int maxCapacity,
        boolean active
    ) {
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
        table.setActive(active);
        return restaurantTableRepository.save(table);
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
