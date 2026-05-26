package com.restaurantplanner.tablecombination;

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
import com.restaurantplanner.tablecombination.domain.TableCombinationRepository;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TableCombinationIntegrationTest {

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

    @Autowired
    private TableCombinationRepository tableCombinationRepository;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        tableCombinationRepository.deleteAll();
        restaurantTableRepository.deleteAll();
        diningRoomRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    void createValidCombination() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom diningRoom = createDiningRoom(restaurant, "Main Room");
        RestaurantTable tableOne = createTable(restaurant, diningRoom, "T1", 2, 2);
        RestaurantTable tableTwo = createTable(restaurant, diningRoom, "T2", 2, 4);
        User owner = createUser("owner@example.com", "secret123", "Owner");
        assignRole(owner, restaurant, Role.RESTAURANT_OWNER);

        String accessToken = loginAndExtractAccessToken("owner@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/table-combinations", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Window pair",
                      "minCapacity": 2,
                      "maxCapacity": 6,
                      "active": true,
                      "tableIds": [%d, %d]
                    }
                    """.formatted(tableOne.getId(), tableTwo.getId())))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.name").value("Window pair"))
            .andExpect(jsonPath("$.items.length()").value(2))
            .andExpect(jsonPath("$.items[0].tableId").value(tableOne.getId()))
            .andExpect(jsonPath("$.items[1].tableId").value(tableTwo.getId()));
    }

    @Test
    void failWhenCombinationIncludesTableFromOtherRestaurant() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Restaurant otherRestaurant = createRestaurant("Other", "other");
        DiningRoom mainRoom = createDiningRoom(restaurant, "Main Room");
        DiningRoom otherRoom = createDiningRoom(otherRestaurant, "Other Room");
        RestaurantTable localTable = createTable(restaurant, mainRoom, "T1", 2, 2);
        RestaurantTable foreignTable = createTable(otherRestaurant, otherRoom, "T9", 2, 4);
        User manager = createUser("manager@example.com", "secret123", "Manager");
        assignRole(manager, restaurant, Role.MANAGER);

        String accessToken = loginAndExtractAccessToken("manager@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/table-combinations", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Invalid pair",
                      "minCapacity": 2,
                      "maxCapacity": 4,
                      "active": true,
                      "tableIds": [%d, %d]
                    }
                    """.formatted(localTable.getId(), foreignTable.getId())))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.code").value("NOT_FOUND"));
    }

    @Test
    void failWhenCombinationRepeatsSameTable() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom diningRoom = createDiningRoom(restaurant, "Main Room");
        RestaurantTable table = createTable(restaurant, diningRoom, "T1", 2, 4);
        User owner = createUser("owner@example.com", "secret123", "Owner");
        assignRole(owner, restaurant, Role.RESTAURANT_OWNER);

        String accessToken = loginAndExtractAccessToken("owner@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/table-combinations", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Duplicate pair",
                      "minCapacity": 2,
                      "maxCapacity": 4,
                      "active": true,
                      "tableIds": [%d, %d]
                    }
                    """.formatted(table.getId(), table.getId())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void failWhenCombinationHasLessThanTwoTables() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom diningRoom = createDiningRoom(restaurant, "Main Room");
        RestaurantTable table = createTable(restaurant, diningRoom, "T1", 2, 4);
        User owner = createUser("owner@example.com", "secret123", "Owner");
        assignRole(owner, restaurant, Role.RESTAURANT_OWNER);

        String accessToken = loginAndExtractAccessToken("owner@example.com", "secret123");

        mockMvc.perform(post("/api/restaurants/{restaurantId}/table-combinations", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "name": "Single table",
                      "minCapacity": 2,
                      "maxCapacity": 4,
                      "active": true,
                      "tableIds": [%d]
                    }
                    """.formatted(table.getId())))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void listOnlyActiveCombinations() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom diningRoom = createDiningRoom(restaurant, "Main Room");
        RestaurantTable tableOne = createTable(restaurant, diningRoom, "T1", 2, 2);
        RestaurantTable tableTwo = createTable(restaurant, diningRoom, "T2", 2, 4);
        RestaurantTable tableThree = createTable(restaurant, diningRoom, "T3", 2, 4);
        User waiter = createUser("waiter@example.com", "secret123", "Waiter");
        assignRole(waiter, restaurant, Role.WAITER);

        createCombination(restaurant, "Active pair", true, tableOne, tableTwo);
        createCombination(restaurant, "Inactive pair", false, tableTwo, tableThree);

        String accessToken = loginAndExtractAccessToken("waiter@example.com", "secret123");

        mockMvc.perform(get("/api/restaurants/{restaurantId}/table-combinations", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(1))
            .andExpect(jsonPath("$[0].name").value("Active pair"));
    }

    @Test
    void deactivateCombination() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom diningRoom = createDiningRoom(restaurant, "Main Room");
        RestaurantTable tableOne = createTable(restaurant, diningRoom, "T1", 2, 2);
        RestaurantTable tableTwo = createTable(restaurant, diningRoom, "T2", 2, 4);
        User manager = createUser("manager@example.com", "secret123", "Manager");
        assignRole(manager, restaurant, Role.MANAGER);

        var combination = createCombination(restaurant, "Manager pair", true, tableOne, tableTwo);
        String accessToken = loginAndExtractAccessToken("manager@example.com", "secret123");

        mockMvc.perform(delete("/api/restaurants/{restaurantId}/table-combinations/{combinationId}", restaurant.getId(), combination.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isNoContent());

        mockMvc.perform(get("/api/restaurants/{restaurantId}/table-combinations", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(accessToken)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.length()").value(0));
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

    private RestaurantTable createTable(
        Restaurant restaurant,
        DiningRoom diningRoom,
        String code,
        int minCapacity,
        int maxCapacity
    ) {
        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setDiningRoom(diningRoom);
        table.setCode(code);
        table.setLabel("Table " + code);
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

    private com.restaurantplanner.tablecombination.domain.TableCombination createCombination(
        Restaurant restaurant,
        String name,
        boolean active,
        RestaurantTable... tables
    ) {
        var combination = new com.restaurantplanner.tablecombination.domain.TableCombination();
        combination.setRestaurant(restaurant);
        combination.setName(name);
        combination.setMinCapacity(2);
        combination.setMaxCapacity(tables[0].getMaxCapacity() + tables[1].getMaxCapacity());
        combination.setActive(active);

        for (int index = 0; index < tables.length; index++) {
            var item = new com.restaurantplanner.tablecombination.domain.TableCombinationItem();
            item.setTableCombination(combination);
            item.setTable(tables[index]);
            item.setOrderIndex(index);
            combination.getItems().add(item);
        }

        return tableCombinationRepository.save(combination);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
