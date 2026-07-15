package com.restaurantplanner.optimization;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.restaurantplanner.auth.domain.RefreshTokenRepository;
import com.restaurantplanner.auth.domain.Role;
import com.restaurantplanner.auth.domain.RoleAssignment;
import com.restaurantplanner.auth.domain.RoleAssignmentRepository;
import com.restaurantplanner.auth.security.AuthenticatedUser;
import com.restaurantplanner.common.api.ConflictException;
import com.restaurantplanner.customer.domain.Customer;
import com.restaurantplanner.customer.domain.CustomerRepository;
import com.restaurantplanner.diningroom.domain.DiningRoom;
import com.restaurantplanner.diningroom.domain.DiningRoomRepository;
import com.restaurantplanner.optimization.api.AssignReservationResponse;
import com.restaurantplanner.optimization.api.AssignmentSelectionRequest;
import com.restaurantplanner.optimization.service.ReservationAssignmentService;
import com.restaurantplanner.reservation.domain.Reservation;
import com.restaurantplanner.reservation.domain.ReservationAssignment;
import com.restaurantplanner.reservation.domain.ReservationAssignmentRepository;
import com.restaurantplanner.reservation.domain.ReservationChannel;
import com.restaurantplanner.reservation.domain.ReservationRepository;
import com.restaurantplanner.reservation.domain.ReservationStatus;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.restaurant.domain.RestaurantStatus;
import com.restaurantplanner.storage.domain.StorageResource;
import com.restaurantplanner.storage.domain.StorageResourceRepository;
import com.restaurantplanner.storage.domain.StorageResourceType;
import com.restaurantplanner.table.domain.RestaurantTable;
import com.restaurantplanner.table.domain.RestaurantTableRepository;
import com.restaurantplanner.tablecombination.domain.TableCombination;
import com.restaurantplanner.tablecombination.domain.TableCombinationItem;
import com.restaurantplanner.tablecombination.domain.TableCombinationRepository;
import com.restaurantplanner.tablecombination.domain.TableCombinationResourceRequirement;
import com.restaurantplanner.tablecombination.domain.CombinationType;
import com.restaurantplanner.tablecombination.domain.OperationalCostLevel;
import com.restaurantplanner.user.domain.User;
import com.restaurantplanner.user.domain.UserRepository;
import com.restaurantplanner.user.domain.UserStatus;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class ReservationAssignmentIntegrationTest {

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
    private DiningRoomRepository diningRoomRepository;

    @Autowired
    private RestaurantTableRepository restaurantTableRepository;

    @Autowired
    private TableCombinationRepository tableCombinationRepository;

    @Autowired
    private ReservationRepository reservationRepository;

    @Autowired
    private ReservationAssignmentRepository reservationAssignmentRepository;

    @Autowired
    private StorageResourceRepository storageResourceRepository;

    @Autowired
    private ReservationAssignmentService reservationAssignmentService;

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        reservationAssignmentRepository.deleteAll();
        reservationRepository.deleteAll();
        tableCombinationRepository.deleteAll();
        storageResourceRepository.deleteAll();
        restaurantTableRepository.deleteAll();
        diningRoomRepository.deleteAll();
        customerRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    void reservationOfTwoUsesTableOfTwoBeforeTableOfSix() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        RestaurantTable table2 = createTable(restaurant, room, "T2", 2, 2, true);
        createTable(restaurant, room, "T6", 2, 6, true);
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer, 2, LocalDate.of(2026, 5, 26), LocalTime.of(20, 0), 90, 15, false);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/assign", restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assigned").value(true))
            .andExpect(jsonPath("$.tableId").value(table2.getId()))
            .andExpect(jsonPath("$.assignmentType").value("TABLE"));
    }

    @Test
    void doesNotAssignTableWithOverlap() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        RestaurantTable busyTable = createTable(restaurant, room, "T1", 2, 4, true);
        RestaurantTable freeTable = createTable(restaurant, room, "T2", 2, 4, true);
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Customer existingCustomer = createCustomer(restaurant, "Eva", "Santos", "+34600999888");
        Reservation existing = createReservation(restaurant, existingCustomer, 2, LocalDate.of(2026, 5, 26), LocalTime.of(20, 0), 90, 15, false);
        createAssignment(existing, busyTable);
        Reservation target = createReservation(restaurant, customer, 2, LocalDate.of(2026, 5, 26), LocalTime.of(20, 30), 60, 15, false);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/assign", restaurant.getId(), target.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tableId").value(freeTable.getId()));
    }

    @Test
    void respectsCleaningBuffer() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        RestaurantTable busyTable = createTable(restaurant, room, "T1", 2, 4, true);
        RestaurantTable freeTable = createTable(restaurant, room, "T2", 2, 4, true);
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Customer existingCustomer = createCustomer(restaurant, "Eva", "Santos", "+34600999888");
        Reservation existing = createReservation(restaurant, existingCustomer, 2, LocalDate.of(2026, 5, 26), LocalTime.of(19, 0), 60, 30, false);
        createAssignment(existing, busyTable);
        Reservation target = createReservation(restaurant, customer, 2, LocalDate.of(2026, 5, 26), LocalTime.of(20, 15), 60, 15, false);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/assign", restaurant.getId(), target.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tableId").value(freeTable.getId()));
    }

    @Test
    void respectsAccessibility() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom stairsRoom = createDiningRoom(restaurant, "Upper Room", 1, false, true);
        DiningRoom accessibleRoom = createDiningRoom(restaurant, "Main Room", 2, true, true);
        createTable(restaurant, stairsRoom, "T1", 2, 4, true);
        RestaurantTable accessibleTable = createTable(restaurant, accessibleRoom, "T2", 2, 4, true);
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer, 2, LocalDate.of(2026, 5, 26), LocalTime.of(20, 0), 60, 15, true);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/assign", restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tableId").value(accessibleTable.getId()));
    }

    @Test
    void penalizesNonPriorityRoom() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom mainRoom = createDiningRoom(restaurant, "Main Room", 1, true, true);
        DiningRoom secondaryRoom = createDiningRoom(restaurant, "Side Room", 2, true, true);
        RestaurantTable mainTable = createTable(restaurant, mainRoom, "A1", 2, 4, true);
        createTable(restaurant, secondaryRoom, "B1", 2, 4, true);
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer, 2, LocalDate.of(2026, 5, 26), LocalTime.of(20, 0), 60, 15, false);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/assign", restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tableId").value(mainTable.getId()));
    }

    @Test
    void usesCombinationOnlyIfNecessary() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        RestaurantTable table2a = createTable(restaurant, room, "T2A", 2, 2, true);
        RestaurantTable table2b = createTable(restaurant, room, "T2B", 2, 2, true);
        RestaurantTable table4 = createTable(restaurant, room, "T4", 2, 4, true);
        TableCombination combination = createCombination(restaurant, "Pair", true, table2a, table2b);
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer, 4, LocalDate.of(2026, 5, 26), LocalTime.of(20, 0), 60, 15, false);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/assign", restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assignmentType").value("TABLE"))
            .andExpect(jsonPath("$.tableId").value(table4.getId()))
            .andExpect(jsonPath("$.tableCombinationId").value(nullValue()));

        reservationAssignmentRepository.deleteAll();
        reservationRepository.deleteAll();

        Reservation reservationNeedsCombination = createReservation(restaurant, customer, 4, LocalDate.of(2026, 5, 26), LocalTime.of(21, 30), 60, 15, false);
        restaurantTableRepository.delete(table4);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/assign", restaurant.getId(), reservationNeedsCombination.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assignmentType").value("TABLE_COMBINATION"))
            .andExpect(jsonPath("$.tableCombinationId").value(combination.getId()));
    }

    @Test
    void savesExplanation() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        createTable(restaurant, room, "T2", 2, 2, true);
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer, 2, LocalDate.of(2026, 5, 26), LocalTime.of(20, 0), 60, 15, false);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        String responseBody = mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/assign", restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.summary").exists())
            .andExpect(jsonPath("$.explanationJson").exists())
            .andReturn()
            .getResponse()
            .getContentAsString();

        AssignReservationResponse response = objectMapper.readValue(responseBody, AssignReservationResponse.class);
        ReservationAssignment saved = reservationAssignmentRepository.findById(response.assignmentId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertNotNull(saved.getExplanationJson());
        org.junit.jupiter.api.Assertions.assertTrue(saved.getExplanationJson().contains("\"summary\""));
    }

    @Test
    void doesNotAssignResourcesFromOtherRestaurant() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Restaurant otherRestaurant = createRestaurant("Other", "other");
        DiningRoom localRoom = createDiningRoom(restaurant, "Main Room", 1, true, true);
        DiningRoom otherRoom = createDiningRoom(otherRestaurant, "Other Room", 1, true, true);
        RestaurantTable localTable = createTable(restaurant, localRoom, "A1", 2, 4, true);
        createTable(otherRestaurant, otherRoom, "B1", 2, 4, true);
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer, 2, LocalDate.of(2026, 5, 26), LocalTime.of(20, 0), 60, 15, false);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/assign", restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tableId").value(localTable.getId()));
    }

    @Test
    void tieBreakIsDeterministic() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        RestaurantTable tableA = createTable(restaurant, room, "A1", 2, 4, true);
        createTable(restaurant, room, "B1", 2, 4, true);
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer, 2, LocalDate.of(2026, 5, 26), LocalTime.of(20, 0), 60, 15, false);
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/reservations/{reservationId}/assign", restaurant.getId(), reservation.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.tableId").value(tableA.getId()));
    }

    @Test
    void advancedSuggestionDoesNotMutateAndAutomaticAssignmentIgnoresIt() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        RestaurantTable tableA = createTable(restaurant, room, "A1", 2, 2, true);
        RestaurantTable tableB = createTable(restaurant, room, "A2", 2, 2, true);
        StorageResource chairs = createStorageResource(restaurant, "Extra chairs", 1, 2);
        TableCombination advanced = createAdvancedCombination(
            restaurant,
            "Six seat setup",
            chairs,
            1,
            tableA,
            tableB
        );
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(
            restaurant,
            customer,
            6,
            LocalDate.of(2026, 8, 26),
            LocalTime.of(20, 0),
            60,
            15,
            false
        );
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(get(
                "/api/restaurants/{restaurantId}/reservations/{reservationId}/assignment-suggestions",
                restaurant.getId(),
                reservation.getId()
            ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.suggestions.length()").value(1))
            .andExpect(jsonPath("$.suggestions[0].candidateId").value(advanced.getId()))
            .andExpect(jsonPath("$.suggestions[0].advanced").value(true))
            .andExpect(jsonPath("$.suggestions[0].resources[0].requiredQuantity").value(1));

        org.junit.jupiter.api.Assertions.assertEquals(0, reservationAssignmentRepository.count());

        mockMvc.perform(post(
                "/api/restaurants/{restaurantId}/reservations/{reservationId}/assign",
                restaurant.getId(),
                reservation.getId()
            ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assigned").value(false));

        org.junit.jupiter.api.Assertions.assertEquals(0, reservationAssignmentRepository.count());
    }

    @Test
    void selectingAdvancedSuggestionPersistsResourcesAndHistory() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        RestaurantTable tableA = createTable(restaurant, room, "A1", 2, 2, true);
        RestaurantTable tableB = createTable(restaurant, room, "A2", 2, 2, true);
        StorageResource chairs = createStorageResource(restaurant, "Extra chairs", 2, 1);
        TableCombination advanced = createAdvancedCombination(
            restaurant,
            "Six seat setup",
            chairs,
            2,
            tableA,
            tableB
        );
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(
            restaurant,
            customer,
            6,
            LocalDate.of(2026, 8, 26),
            LocalTime.of(20, 0),
            60,
            15,
            false
        );
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post(
                "/api/restaurants/{restaurantId}/reservations/{reservationId}/assignment-selection",
                restaurant.getId(),
                reservation.getId()
            )
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"candidateType":"TABLE_COMBINATION","candidateId":%d}
                    """.formatted(advanced.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assigned").value(true))
            .andExpect(jsonPath("$.operationalCostLevel").value("MEDIUM"))
            .andExpect(jsonPath("$.setupTimeMinutes").value(20))
            .andExpect(jsonPath("$.resources[0].quantity").value(2));

        mockMvc.perform(get(
                "/api/restaurants/{restaurantId}/reservations/{reservationId}/assignment-history",
                restaurant.getId(),
                reservation.getId()
            ).header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].active").value(true))
            .andExpect(jsonPath("$[0].resources[0].resourceName").value("Extra chairs"));

        mockMvc.perform(patch(
                "/api/restaurants/{restaurantId}/storage-resources/{resourceId}",
                restaurant.getId(),
                chairs.getId()
            )
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"quantity":1}
                    """))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.details.committedPeak").value(2));

        mockMvc.perform(patch(
                "/api/restaurants/{restaurantId}/storage-resources/{resourceId}",
                restaurant.getId(),
                chairs.getId()
            )
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"active":false}
                    """))
            .andExpect(status().isConflict());
    }

    @Test
    void selectionRevalidatesOverlappingInventory() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        RestaurantTable tableA1 = createTable(restaurant, room, "A1", 2, 2, true);
        RestaurantTable tableA2 = createTable(restaurant, room, "A2", 2, 2, true);
        RestaurantTable tableB1 = createTable(restaurant, room, "B1", 2, 2, true);
        RestaurantTable tableB2 = createTable(restaurant, room, "B2", 2, 2, true);
        StorageResource extension = createStorageResource(restaurant, "Extension", 1, 2);
        TableCombination firstCombination = createAdvancedCombination(
            restaurant, "First setup", extension, 1, tableA1, tableA2
        );
        TableCombination secondCombination = createAdvancedCombination(
            restaurant, "Second setup", extension, 1, tableB1, tableB2
        );
        Customer firstCustomer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Customer secondCustomer = createCustomer(restaurant, "Eva", "Santos", "+34600999888");
        Reservation first = createReservation(
            restaurant, firstCustomer, 6, LocalDate.of(2026, 8, 26), LocalTime.of(20, 0), 60, 15, false
        );
        Reservation second = createReservation(
            restaurant, secondCustomer, 6, LocalDate.of(2026, 8, 26), LocalTime.of(20, 15), 60, 15, false
        );
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        selectCombination(restaurant, first, firstCombination, token, status().isOk());
        selectCombination(restaurant, second, secondCombination, token, status().isConflict());
    }

    @Test
    void concurrentSelectionsCannotOversellInventory() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        RestaurantTable tableA1 = createTable(restaurant, room, "A1", 2, 2, true);
        RestaurantTable tableA2 = createTable(restaurant, room, "A2", 2, 2, true);
        RestaurantTable tableB1 = createTable(restaurant, room, "B1", 2, 2, true);
        RestaurantTable tableB2 = createTable(restaurant, room, "B2", 2, 2, true);
        StorageResource extension = createStorageResource(restaurant, "Extension", 1, 2);
        TableCombination firstCombination = createAdvancedCombination(
            restaurant, "First setup", extension, 1, tableA1, tableA2
        );
        TableCombination secondCombination = createAdvancedCombination(
            restaurant, "Second setup", extension, 1, tableB1, tableB2
        );
        Customer firstCustomer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Customer secondCustomer = createCustomer(restaurant, "Eva", "Santos", "+34600999888");
        Reservation first = createReservation(
            restaurant, firstCustomer, 6, LocalDate.of(2026, 8, 26), LocalTime.of(20, 0), 60, 15, false
        );
        Reservation second = createReservation(
            restaurant, secondCustomer, 6, LocalDate.of(2026, 8, 26), LocalTime.of(20, 15), 60, 15, false
        );
        User manager = createUser("manager@example.com", "secret123", "Manager");
        assignRole(manager, restaurant, Role.MANAGER);
        AuthenticatedUser authenticatedUser = new AuthenticatedUser(
            manager.getId(),
            manager.getEmail(),
            manager.getName(),
            Set.of(Role.MANAGER),
            Set.of(restaurant.getId())
        );

        CountDownLatch start = new CountDownLatch(1);
        try (ExecutorService executor = Executors.newFixedThreadPool(2)) {
            Future<String> firstResult = executor.submit(() -> selectConcurrently(
                start, restaurant, first, firstCombination, authenticatedUser
            ));
            Future<String> secondResult = executor.submit(() -> selectConcurrently(
                start, restaurant, second, secondCombination, authenticatedUser
            ));

            start.countDown();
            List<String> results = List.of(
                firstResult.get(15, TimeUnit.SECONDS),
                secondResult.get(15, TimeUnit.SECONDS)
            );
            org.junit.jupiter.api.Assertions.assertEquals(1, results.stream().filter("assigned"::equals).count());
            org.junit.jupiter.api.Assertions.assertEquals(1, results.stream().filter("conflict"::equals).count());
        }

        List<ReservationAssignment> activeAssignments = reservationAssignmentRepository
            .findByActiveTrueAndReservationRestaurantIdAndReservationReservationDateAndReservationStatusIn(
                restaurant.getId(),
                first.getReservationDate(),
                Set.of(ReservationStatus.PENDING)
            );
        org.junit.jupiter.api.Assertions.assertEquals(1, activeAssignments.size());
        org.junit.jupiter.api.Assertions.assertEquals(1, activeAssignments.get(0).getResources().size());
    }

    private String selectConcurrently(
        CountDownLatch start,
        Restaurant restaurant,
        Reservation reservation,
        TableCombination combination,
        AuthenticatedUser authenticatedUser
    ) throws InterruptedException {
        start.await();
        try {
            reservationAssignmentService.select(
                restaurant.getId(),
                reservation.getId(),
                new AssignmentSelectionRequest("TABLE_COMBINATION", combination.getId()),
                authenticatedUser
            );
            return "assigned";
        } catch (ConflictException exception) {
            return "conflict";
        }
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
        table.setLabel("Table " + code);
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

    private TableCombination createCombination(Restaurant restaurant, String name, boolean active, RestaurantTable... tables) {
        TableCombination combination = new TableCombination();
        combination.setRestaurant(restaurant);
        combination.setName(name);
        combination.setMinCapacity(2);
        combination.setMaxCapacity(java.util.Arrays.stream(tables).mapToInt(RestaurantTable::getMaxCapacity).sum());
        combination.setActive(active);

        for (int index = 0; index < tables.length; index++) {
            TableCombinationItem item = new TableCombinationItem();
            item.setTableCombination(combination);
            item.setTable(tables[index]);
            item.setOrderIndex(index);
            combination.getItems().add(item);
        }

        return tableCombinationRepository.save(combination);
    }

    private StorageResource createStorageResource(
        Restaurant restaurant,
        String name,
        int quantity,
        int capacityPerUnit
    ) {
        StorageResource resource = new StorageResource();
        resource.setRestaurant(restaurant);
        resource.setResourceType(StorageResourceType.EXTRA_CHAIR);
        resource.setName(name);
        resource.setQuantity(quantity);
        resource.setCapacityPerUnit(capacityPerUnit);
        resource.setSetupTimeMinutes(5);
        resource.setActive(true);
        return storageResourceRepository.save(resource);
    }

    private TableCombination createAdvancedCombination(
        Restaurant restaurant,
        String name,
        StorageResource resource,
        int quantity,
        RestaurantTable... tables
    ) {
        TableCombination combination = new TableCombination();
        combination.setRestaurant(restaurant);
        combination.setName(name);
        combination.setMinCapacity(2);
        combination.setMaxCapacity(
            java.util.Arrays.stream(tables).mapToInt(RestaurantTable::getMaxCapacity).sum()
                + quantity * resource.getCapacityPerUnit()
        );
        combination.setActive(true);
        combination.setCombinationType(CombinationType.ADVANCED);
        combination.setOperationalCostLevel(OperationalCostLevel.MEDIUM);
        combination.setSetupTimeMinutes(20);

        for (int index = 0; index < tables.length; index++) {
            TableCombinationItem item = new TableCombinationItem();
            item.setTableCombination(combination);
            item.setTable(tables[index]);
            item.setOrderIndex(index);
            combination.getItems().add(item);
        }

        TableCombinationResourceRequirement requirement = new TableCombinationResourceRequirement();
        requirement.setRestaurant(restaurant);
        requirement.setTableCombination(combination);
        requirement.setStorageResource(resource);
        requirement.setQuantity(quantity);
        combination.getResourceRequirements().add(requirement);
        return tableCombinationRepository.save(combination);
    }

    private void selectCombination(
        Restaurant restaurant,
        Reservation reservation,
        TableCombination combination,
        String token,
        org.springframework.test.web.servlet.ResultMatcher expectedStatus
    ) throws Exception {
        mockMvc.perform(post(
                "/api/restaurants/{restaurantId}/reservations/{reservationId}/assignment-selection",
                restaurant.getId(),
                reservation.getId()
            )
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"candidateType":"TABLE_COMBINATION","candidateId":%d}
                    """.formatted(combination.getId())))
            .andExpect(expectedStatus);
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
        int partySize,
        LocalDate reservationDate,
        LocalTime startTime,
        int estimatedDurationMin,
        int cleaningBufferMin,
        boolean accessibilityRequired
    ) {
        Reservation reservation = new Reservation();
        reservation.setRestaurant(restaurant);
        reservation.setCustomer(customer);
        reservation.setChannel(ReservationChannel.MANUAL);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setPartySize(partySize);
        reservation.setReservationDate(reservationDate);
        reservation.setStartTime(startTime);
        reservation.setEndTime(startTime.plusMinutes(estimatedDurationMin));
        reservation.setEstimatedDurationMin(estimatedDurationMin);
        reservation.setCleaningBufferMin(cleaningBufferMin);
        reservation.setSpecialRequests(null);
        reservation.setAccessibilityRequired(accessibilityRequired);
        return reservationRepository.save(reservation);
    }

    private ReservationAssignment createAssignment(Reservation reservation, RestaurantTable table) {
        ReservationAssignment assignment = new ReservationAssignment();
        assignment.setReservation(reservation);
        assignment.setAssignmentType("TABLE");
        assignment.setDiningRoom(table.getDiningRoom());
        assignment.setTable(table);
        assignment.setScore(0d);
        assignment.setExplanationJson("{\"summary\":\"existing\"}");
        assignment.setAssignedAt(java.time.Instant.now());
        assignment.setActive(true);
        return reservationAssignmentRepository.save(assignment);
    }

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
