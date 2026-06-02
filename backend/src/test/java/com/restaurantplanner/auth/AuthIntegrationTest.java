package com.restaurantplanner.auth;

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
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class AuthIntegrationTest {

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

    @Test
    void loginReturnsTokensAndAccessibleRestaurants() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        User user = createUser("owner@example.com", "secret123", "Owner");
        assignRole(user, restaurant, Role.RESTAURANT_OWNER);

        String response = mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "owner@example.com",
                      "password": "secret123"
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value("owner@example.com"))
            .andExpect(jsonPath("$.restaurants[0].id").value(restaurant.getId()))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("expiresIn").asLong()).isPositive();
        assertThat(refreshTokenRepository.findByToken(json.get("refreshToken").asText())).isPresent();
    }

    @Test
    void loginWithInvalidCredentialsReturnsUnauthorized() throws Exception {
        createUser("manager@example.com", "secret123", "Manager");

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "manager@example.com",
                      "password": "wrong-password"
                    }
                    """))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void protectedEndpointWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
    }

    @Test
    void userCannotAccessRestaurantWithoutRole() throws Exception {
        Restaurant assignedRestaurant = createRestaurant("Assigned", "assigned");
        Restaurant otherRestaurant = createRestaurant("Other", "other");
        User user = createUser("waiter@example.com", "secret123", "Waiter");
        assignRole(user, assignedRestaurant, Role.WAITER);

        String accessToken = loginAndExtractAccessToken("waiter@example.com", "secret123");

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .header("X-Restaurant-Id", otherRestaurant.getId()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    void platformAdminCanAccessAnyRestaurantUnderCurrentRule() throws Exception {
        Restaurant adminRestaurant = createRestaurant("Admin HQ", "admin-hq");
        Restaurant targetRestaurant = createRestaurant("Target", "target");
        User user = createUser("admin@example.com", "secret123", "Platform Admin");
        assignRole(user, adminRestaurant, Role.PLATFORM_ADMIN);

        String accessToken = loginAndExtractAccessToken("admin@example.com", "secret123");

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .header("X-Restaurant-Id", targetRestaurant.getId()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.activeRestaurantId").value(targetRestaurant.getId()));
    }

    @Test
    void registerCreatesUserAndRestaurantAndReturnsTokens() throws Exception {
        String response = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "newowner@example.com",
                      "password": "securePass123",
                      "name": "New Owner",
                      "restaurantName": "My New Restaurant"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").isNotEmpty())
            .andExpect(jsonPath("$.refreshToken").isNotEmpty())
            .andExpect(jsonPath("$.user.email").value("newowner@example.com"))
            .andExpect(jsonPath("$.user.name").value("New Owner"))
            .andExpect(jsonPath("$.restaurants[0].name").value("My New Restaurant"))
            .andExpect(jsonPath("$.restaurants[0].roles[0]").value(Role.RESTAURANT_OWNER.name()))
            .andReturn()
            .getResponse()
            .getContentAsString();

        JsonNode json = objectMapper.readTree(response);
        assertThat(json.get("expiresIn").asLong()).isPositive();
        assertThat(refreshTokenRepository.findByToken(json.get("refreshToken").asText())).isPresent();
    }

    @Test
    void registerWithDuplicateEmailReturnsConflict() throws Exception {
        createUser("duplicate@example.com", "secret123", "Existing");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "duplicate@example.com",
                      "password": "securePass123",
                      "name": "New Owner",
                      "restaurantName": "Some Restaurant"
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void registerWithGeneratedSlugWhenExists() throws Exception {
        createRestaurant("Duplicate Slug Restaurant", "my-restaurant");

        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "unique@example.com",
                      "password": "securePass123",
                      "name": "Unique Owner",
                      "restaurantName": "My Restaurant"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.restaurants[0].name").value("My Restaurant"))
            .andExpect(jsonPath("$.restaurants[0].slug").value(not("my-restaurant")));
    }

    @Test
    void registerWithInvalidDataReturnsBadRequest() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "email": "not-an-email",
                      "password": "short",
                      "name": "",
                      "restaurantName": ""
                    }
                    """))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
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
            .andReturn()
            .getResponse()
            .getContentAsString();

        return objectMapper.readTree(response).get("accessToken").asText();
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
