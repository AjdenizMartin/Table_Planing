package com.restaurantplanner.restaurant;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantplanner.auth.domain.RefreshTokenRepository;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignment;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.restaurant.domain.RestaurantStatus;
import com.restaurantplanner.user.domain.User;
import com.restaurantplanner.user.domain.UserRepository;
import com.restaurantplanner.user.domain.UserStatus;
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
import org.springframework.test.context.jdbc.Sql;
import org.springframework.test.context.jdbc.SqlConfig;
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
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
@SqlConfig(transactionMode = SqlConfig.TransactionMode.ISOLATED)
class RestaurantIntegrationTest {

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
    private EntityManager entityManager;

    @BeforeEach
    void setUp() {
        entityManager.clear();
    }

    @Test
    void platformAdminCanCreateRestaurantAndGetsOwnerAssignment() throws Exception {
        Restaurant adminBaseRestaurant = createRestaurant("Admin Base", "admin-base");
        User admin = createUser("admin@example.com", "secret123", "Admin");
        assignRole(admin, adminBaseRestaurant, Role.PLATFORM_ADMIN);

        String accessToken = loginAndExtractAccessToken("admin@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "New Restaurant",
                      "slug": "new-restaurant",
                      "timezone": "Europe/Dublin",
                      "phone": "+3531234567",
                      "status": "ACTIVE"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("New Restaurant"))
            .andExpect(jsonPath("$.slug").value("new-restaurant"))
            .andExpect(jsonPath("$.roles[0]").value("RESTAURANT_OWNER"));
    }

    @Test
    void duplicateSlugFailsWithConflict() throws Exception {
        Restaurant adminBaseRestaurant = createRestaurant("Admin Base", "admin-base");
        createRestaurant("Existing", "existing-slug");
        User admin = createUser("admin@example.com", "secret123", "Admin");
        assignRole(admin, adminBaseRestaurant, Role.PLATFORM_ADMIN);

        String accessToken = loginAndExtractAccessToken("admin@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Another Restaurant",
                      "slug": "existing-slug",
                      "timezone": "Europe/Dublin",
                      "phone": "+3531234567",
                      "status": "ACTIVE"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void userSeesOnlyAssociatedRestaurants() throws Exception {
        Restaurant restaurantOne = createRestaurant("One", "one");
        Restaurant restaurantTwo = createRestaurant("Two", "two");
        Restaurant hiddenRestaurant = createRestaurant("Hidden", "hidden");
        User manager = createUser("manager@example.com", "secret123", "Manager");
        assignRole(manager, restaurantOne, Role.MANAGER);
        assignRole(manager, restaurantTwo, Role.WAITER);

        String accessToken = loginAndExtractAccessToken("manager@example.com", "secret123");

        mockMvc.perform(get("/api/restaurants")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(2))
            .andExpect(jsonPath("$[0].id").value(restaurantOne.getId()))
            .andExpect(jsonPath("$[1].id").value(restaurantTwo.getId()));
    }

    @Test
    void userCannotReadForeignRestaurant() throws Exception {
        Restaurant ownRestaurant = createRestaurant("Own", "own");
        Restaurant foreignRestaurant = createRestaurant("Foreign", "foreign");
        User waiter = createUser("waiter@example.com", "secret123", "Waiter");
        assignRole(waiter, ownRestaurant, Role.WAITER);

        String accessToken = loginAndExtractAccessToken("waiter@example.com", "secret123");

        mockMvc.perform(get("/api/restaurants/{restaurantId}", foreignRestaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void userWithoutPermissionCannotModifyRestaurant() throws Exception {
        Restaurant restaurant = createRestaurant("Managed", "managed");
        User manager = createUser("manager@example.com", "secret123", "Manager");
        assignRole(manager, restaurant, Role.MANAGER);

        String accessToken = loginAndExtractAccessToken("manager@example.com", "secret123");

        mockMvc.perform(patch("/api/restaurants/{restaurantId}", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Updated Name"
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
