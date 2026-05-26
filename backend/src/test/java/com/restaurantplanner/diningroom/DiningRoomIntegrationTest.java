package com.restaurantplanner.diningroom;

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

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class DiningRoomIntegrationTest {

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

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        diningRoomRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    void ownerCanCreateDiningRoom() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        User owner = createUser("owner@example.com", "secret123", "Owner");
        assignRole(owner, restaurant, Role.RESTAURANT_OWNER);

        String accessToken = loginAndExtractAccessToken("owner@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/dining-rooms", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Main Room",
                      "priority": 1,
                      "accessible": true,
                      "active": true,
                      "layoutWidth": 1200,
                      "layoutHeight": 800
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.restaurantId").value(restaurant.getId()))
            .andExpect(jsonPath("$.name").value("Main Room"))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void listDiningRoomsByRestaurant() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        User waiter = createUser("waiter@example.com", "secret123", "Waiter");
        assignRole(waiter, restaurant, Role.WAITER);
        createDiningRoom(restaurant, "Room A", 1, true, true);
        createDiningRoom(restaurant, "Room B", 2, false, true);

        String accessToken = loginAndExtractAccessToken("waiter@example.com", "secret123");

        mockMvc.perform(get("/api/restaurants/{restaurantId}/dining-rooms", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].name").value("Room A"))
            .andExpect(jsonPath("$[1].name").value("Room B"));
    }

    @Test
    void cannotAccessDiningRoomOfOtherRestaurant() throws Exception {
        Restaurant ownRestaurant = createRestaurant("Own", "own");
        Restaurant otherRestaurant = createRestaurant("Other", "other");
        User waiter = createUser("waiter@example.com", "secret123", "Waiter");
        assignRole(waiter, ownRestaurant, Role.WAITER);
        DiningRoom diningRoom = createDiningRoom(otherRestaurant, "Foreign Room", 1, true, true);

        String accessToken = loginAndExtractAccessToken("waiter@example.com", "secret123");

        mockMvc.perform(get("/api/restaurants/{restaurantId}/dining-rooms/{diningRoomId}", otherRestaurant.getId(), diningRoom.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void waiterCannotModifyDiningRoom() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        User waiter = createUser("waiter@example.com", "secret123", "Waiter");
        assignRole(waiter, restaurant, Role.WAITER);
        DiningRoom diningRoom = createDiningRoom(restaurant, "Room A", 1, true, true);

        String accessToken = loginAndExtractAccessToken("waiter@example.com", "secret123");

        mockMvc.perform(patch("/api/restaurants/{restaurantId}/dining-rooms/{diningRoomId}", restaurant.getId(), diningRoom.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Updated Room"
                    }
                    """))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void deleteDeactivatesDiningRoom() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        User manager = createUser("manager@example.com", "secret123", "Manager");
        assignRole(manager, restaurant, Role.MANAGER);
        DiningRoom diningRoom = createDiningRoom(restaurant, "Room A", 1, true, true);

        String accessToken = loginAndExtractAccessToken("manager@example.com", "secret123");

        mockMvc.perform(delete("/api/restaurants/{restaurantId}/dining-rooms/{diningRoomId}", restaurant.getId(), diningRoom.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/restaurants/{restaurantId}/dining-rooms/{diningRoomId}", restaurant.getId(), diningRoom.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
