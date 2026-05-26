package com.restaurantplanner.table;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantplanner.auth.domain.RefreshTokenRepository;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignment;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.diningroom.domain.DiningRoom;
import com.restaurantplanner.diningroom.domain.DiningRoomRepository;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.restaurant.domain.RestaurantStatus;
import com.restaurantplanner.table.domain.RestaurantTable;
import com.restaurantplanner.table.domain.RestaurantTableRepository;
import com.restaurantplanner.user.domain.User;
import com.restaurantplanner.user.domain.UserRepository;
import com.restaurantplanner.user.domain.UserStatus;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class RestaurantTableIntegrationTest {

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
    private DiningRoomRepository diningRoomRepository;

    @Autowired
    private RestaurantTableRepository restaurantTableRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        restaurantTableRepository.deleteAll();
        diningRoomRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    void createValidTable() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom diningRoom = createDiningRoom(restaurant, "Main Room");
        User owner = createUser("owner@example.com", "secret123", "Owner");
        assignRole(owner, restaurant, Role.RESTAURANT_OWNER);

        String accessToken = loginAndExtractAccessToken("owner@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/tables", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "diningRoomId": %d,
                      "code": "T1",
                      "label": "Window table",
                      "minCapacity": 2,
                      "maxCapacity": 4,
                      "shape": "RECTANGLE",
                      "x": 100,
                      "y": 200,
                      "width": 120,
                      "height": 80,
                      "active": true
                    }
                    """.formatted(diningRoom.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("T1"))
            .andExpect(jsonPath("$.diningRoomId").value(diningRoom.getId()))
            .andExpect(jsonPath("$.maxCapacity").value(4));
    }

    @Test
    void failWhenMinCapacityGreaterThanMaxCapacity() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom diningRoom = createDiningRoom(restaurant, "Main Room");
        User owner = createUser("owner@example.com", "secret123", "Owner");
        assignRole(owner, restaurant, Role.RESTAURANT_OWNER);

        String accessToken = loginAndExtractAccessToken("owner@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/tables", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "diningRoomId": %d,
                      "code": "T1",
                      "label": "Invalid table",
                      "minCapacity": 6,
                      "maxCapacity": 4,
                      "shape": "RECTANGLE",
                      "x": 100,
                      "y": 200,
                      "width": 120,
                      "height": 80,
                      "active": true
                    }
                    """.formatted(diningRoom.getId())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void failWhenDiningRoomBelongsToOtherRestaurant() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Restaurant otherRestaurant = createRestaurant("Other", "other");
        DiningRoom foreignDiningRoom = createDiningRoom(otherRestaurant, "Foreign Room");
        User owner = createUser("owner@example.com", "secret123", "Owner");
        assignRole(owner, restaurant, Role.RESTAURANT_OWNER);

        String accessToken = loginAndExtractAccessToken("owner@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/tables", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "diningRoomId": %d,
                      "code": "T1",
                      "label": "Invalid foreign room",
                      "minCapacity": 2,
                      "maxCapacity": 4,
                      "shape": "RECTANGLE",
                      "x": 100,
                      "y": 200,
                      "width": 120,
                      "height": 80,
                      "active": true
                    }
                    """.formatted(foreignDiningRoom.getId())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void failWhenCodeDuplicatedInSameRestaurant() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom diningRoom = createDiningRoom(restaurant, "Main Room");
        createTable(restaurant, diningRoom, "T1");
        User manager = createUser("manager@example.com", "secret123", "Manager");
        assignRole(manager, restaurant, Role.MANAGER);

        String accessToken = loginAndExtractAccessToken("manager@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/tables", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "diningRoomId": %d,
                      "code": "T1",
                      "label": "Duplicate code",
                      "minCapacity": 2,
                      "maxCapacity": 4,
                      "shape": "RECTANGLE",
                      "x": 100,
                      "y": 200,
                      "width": 120,
                      "height": 80,
                      "active": true
                    }
                    """.formatted(diningRoom.getId())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void allowSameCodeInDifferentRestaurants() throws Exception {
        Restaurant restaurantOne = createRestaurant("One", "one");
        Restaurant restaurantTwo = createRestaurant("Two", "two");
        DiningRoom roomOne = createDiningRoom(restaurantOne, "Room One");
        DiningRoom roomTwo = createDiningRoom(restaurantTwo, "Room Two");
        createTable(restaurantOne, roomOne, "T1");

        User owner = createUser("owner@example.com", "secret123", "Owner");
        assignRole(owner, restaurantTwo, Role.RESTAURANT_OWNER);

        String accessToken = loginAndExtractAccessToken("owner@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/tables", restaurantTwo.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "diningRoomId": %d,
                      "code": "T1",
                      "label": "Same code other restaurant",
                      "minCapacity": 2,
                      "maxCapacity": 4,
                      "shape": "RECTANGLE",
                      "x": 100,
                      "y": 200,
                      "width": 120,
                      "height": 80,
                      "active": true
                    }
                    """.formatted(roomTwo.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.code").value("T1"));
    }

    @Test
    void updateLayout() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom diningRoom = createDiningRoom(restaurant, "Main Room");
        RestaurantTable table = createTable(restaurant, diningRoom, "T1");
        User manager = createUser("manager@example.com", "secret123", "Manager");
        assignRole(manager, restaurant, Role.MANAGER);

        String accessToken = loginAndExtractAccessToken("manager@example.com", "secret123");

        mockMvc.perform(patch("/api/restaurants/{restaurantId}/tables/{tableId}/layout", restaurant.getId(), table.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "x": 500,
                      "y": 600,
                      "width": 160,
                      "height": 90
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.x").value(500))
            .andExpect(jsonPath("$.y").value(600))
            .andExpect(jsonPath("$.width").value(160))
            .andExpect(jsonPath("$.height").value(90));
    }

    @Test
    void waiterCannotModifyTables() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom diningRoom = createDiningRoom(restaurant, "Main Room");
        RestaurantTable table = createTable(restaurant, diningRoom, "T1");
        User waiter = createUser("waiter@example.com", "secret123", "Waiter");
        assignRole(waiter, restaurant, Role.WAITER);

        String accessToken = loginAndExtractAccessToken("waiter@example.com", "secret123");

        mockMvc.perform(patch("/api/restaurants/{restaurantId}/tables/{tableId}", restaurant.getId(), table.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "label": "Updated"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
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

    private RestaurantTable createTable(Restaurant restaurant, DiningRoom diningRoom, String code) {
        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setDiningRoom(diningRoom);
        table.setCode(code);
        table.setLabel("Table " + code);
        table.setMinCapacity(2);
        table.setMaxCapacity(4);
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
