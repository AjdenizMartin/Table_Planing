package com.restaurantplanner.storage;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignment;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.restaurant.domain.RestaurantStatus;
import com.restaurantplanner.storage.domain.StorageResource;
import com.restaurantplanner.storage.domain.StorageResourceRepository;
import com.restaurantplanner.storage.domain.StorageResourceType;
import com.restaurantplanner.user.domain.User;
import com.restaurantplanner.user.domain.UserRepository;
import com.restaurantplanner.user.domain.UserStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.jdbc.Sql;
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
@Sql(scripts = "/cleanup.sql", executionPhase = Sql.ExecutionPhase.BEFORE_TEST_METHOD)
class StorageResourceIntegrationTest {

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
    private PasswordEncoder passwordEncoder;

    @Autowired
    private StorageResourceRepository storageResourceRepository;

    @Test
    void createsActiveStorageResourceWithOperationalFields() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        String token = loginWithRole(restaurant, "owner@example.com", Role.RESTAURANT_OWNER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/storage-resources", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "resourceType": "EXTRA_CHAIR",
                      "name": "Extra storage chairs",
                      "quantity": 12,
                      "capacityPerUnit": 1,
                      "setupTimeMinutes": 5,
                      "active": true,
                      "notes": "Pasillo trasero"
                    }
                    """))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.resourceType").value("EXTRA_CHAIR"))
            .andExpect(jsonPath("$.quantity").value(12))
            .andExpect(jsonPath("$.capacityPerUnit").value(1))
            .andExpect(jsonPath("$.setupTimeMinutes").value(5))
            .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void rejectsNegativeStorageResourceValues() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        String token = loginWithRole(restaurant, "owner@example.com", Role.RESTAURANT_OWNER);

        assertCreateRejected(restaurant, token, -1, 0, 0);
        assertCreateRejected(restaurant, token, 1, -1, 0);
        assertCreateRejected(restaurant, token, 1, 0, -1);
    }

    @Test
    void rejectsBlankStorageResourceNameAndType() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        String token = loginWithRole(restaurant, "owner@example.com", Role.RESTAURANT_OWNER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/storage-resources", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "resourceType": " ",
                      "name": " ",
                      "quantity": 1,
                      "active": true
                    }
                    """))
            .andExpect(status().isBadRequest());
    }

    @Test
    void filtersStorageResourcesByTypeAndActiveState() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        createStorageResource(restaurant, StorageResourceType.EXTRA_CHAIR, "Extra chairs", 12);
        createStorageResource(restaurant, StorageResourceType.FOLDING_TABLE, "Folding tables", 3);
        StorageResource inactive = createStorageResource(restaurant, StorageResourceType.BENCH, "Banco", 1);
        inactive.setActive(false);
        storageResourceRepository.save(inactive);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(get("/api/restaurants/{restaurantId}/storage-resources", restaurant.getId())
                .queryParam("resourceType", "EXTRA_CHAIR")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Extra chairs"));

        mockMvc.perform(get("/api/restaurants/{restaurantId}/storage-resources", restaurant.getId())
                .queryParam("active", "false")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Banco"));

        mockMvc.perform(get("/api/restaurants/{restaurantId}/storage-resources", restaurant.getId())
                .queryParam("resourceType", "FOLDING_TABLE")
                .queryParam("active", "true")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Folding tables"));
    }

    @Test
    void updatesAndDeactivatesThenReactivatesStorageResource() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        StorageResource resource = createStorageResource(
            restaurant,
            StorageResourceType.EXTRA_CHAIR,
            "Old chairs",
            4
        );
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(patch("/api/restaurants/{restaurantId}/storage-resources/{resourceId}", restaurant.getId(), resource.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "resourceType": "FOLDING_TABLE",
                      "name": "Terrace folding tables",
                      "quantity": 6,
                      "capacityPerUnit": 4,
                      "setupTimeMinutes": 12,
                      "notes": "North storage area",
                      "active": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.resourceType").value("FOLDING_TABLE"))
            .andExpect(jsonPath("$.name").value("Terrace folding tables"))
            .andExpect(jsonPath("$.quantity").value(6))
            .andExpect(jsonPath("$.capacityPerUnit").value(4))
            .andExpect(jsonPath("$.setupTimeMinutes").value(12))
            .andExpect(jsonPath("$.notes").value("North storage area"))
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(patch("/api/restaurants/{restaurantId}/storage-resources/{resourceId}", restaurant.getId(), resource.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "active": true
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(patch("/api/restaurants/{restaurantId}/storage-resources/{resourceId}", restaurant.getId(), resource.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "active": false
                    }
                    """))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));

        mockMvc.perform(get("/api/restaurants/{restaurantId}/storage-resources/{resourceId}", restaurant.getId(), resource.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void availabilityCheckRejectsQuantityAboveAvailable() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        StorageResource resource = createStorageResource(restaurant, StorageResourceType.EXTRA_CHAIR, "Extra chairs", 4);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/storage-resources/{resourceId}/availability-check", restaurant.getId(), resource.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "requestedQuantity": 5
                    }
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"))
            .andExpect(jsonPath("$.details.availableQuantity").value(4));
    }

    @Test
    void storageResourcesAreScopedByRestaurant() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Restaurant otherRestaurant = createRestaurant("Other", "other");
        createStorageResource(otherRestaurant, StorageResourceType.STORAGE_TABLE, "Other venue storage table", 2);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(get("/api/restaurants/{restaurantId}/storage-resources", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));

        StorageResource foreignResource = storageResourceRepository.findByRestaurantIdOrderByResourceTypeAscNameAscIdAsc(otherRestaurant.getId()).get(0);
        mockMvc.perform(get("/api/restaurants/{restaurantId}/storage-resources/{resourceId}", restaurant.getId(), foreignResource.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));

        mockMvc.perform(patch("/api/restaurants/{restaurantId}/storage-resources/{resourceId}", restaurant.getId(), foreignResource.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "quantity": 99
                    }
                    """))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
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

    private StorageResource createStorageResource(
        Restaurant restaurant,
        StorageResourceType resourceType,
        String name,
        int quantity
    ) {
        StorageResource resource = new StorageResource();
        resource.setRestaurant(restaurant);
        resource.setResourceType(resourceType);
        resource.setName(name);
        resource.setQuantity(quantity);
        resource.setCapacityPerUnit(0);
        resource.setSetupTimeMinutes(0);
        resource.setActive(true);
        return storageResourceRepository.save(resource);
    }

    private void assertCreateRejected(
        Restaurant restaurant,
        String token,
        int quantity,
        int capacityPerUnit,
        int setupTimeMinutes
    ) throws Exception {
        mockMvc.perform(post("/api/restaurants/{restaurantId}/storage-resources", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "resourceType": "EXTRA_CHAIR",
                      "name": "Invalid resource",
                      "quantity": %d,
                      "capacityPerUnit": %d,
                      "setupTimeMinutes": %d,
                      "active": true
                    }
                    """.formatted(quantity, capacityPerUnit, setupTimeMinutes)))
            .andExpect(status().isBadRequest());
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
