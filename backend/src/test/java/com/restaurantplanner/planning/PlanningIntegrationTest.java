package com.restaurantplanner.planning;

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
import com.restaurantplanner.reservation.domain.ReservationAssignment;
import com.restaurantplanner.reservation.domain.ReservationAssignmentRepository;
import com.restaurantplanner.reservation.domain.ReservationChannel;
import com.restaurantplanner.reservation.domain.ReservationRepository;
import com.restaurantplanner.reservation.domain.ReservationStatus;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.restaurant.domain.RestaurantRepository;
import com.restaurantplanner.restaurant.domain.RestaurantStatus;
import com.restaurantplanner.table.domain.RestaurantTable;
import com.restaurantplanner.table.domain.RestaurantTableRepository;
import com.restaurantplanner.table.domain.TableType;
import com.restaurantplanner.tablecombination.domain.TableCombination;
import com.restaurantplanner.tablecombination.domain.TableCombinationItem;
import com.restaurantplanner.tablecombination.domain.TableCombinationRepository;
import com.restaurantplanner.user.domain.User;
import com.restaurantplanner.user.domain.UserRepository;
import com.restaurantplanner.user.domain.UserStatus;
import java.time.Instant;
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
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PlanningIntegrationTest {

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

    @BeforeEach
    void setUp() {
        refreshTokenRepository.deleteAll();
        roleAssignmentRepository.deleteAll();
        reservationAssignmentRepository.deleteAll();
        reservationRepository.deleteAll();
        tableCombinationRepository.deleteAll();
        restaurantTableRepository.deleteAll();
        diningRoomRepository.deleteAll();
        customerRepository.deleteAll();
        userRepository.deleteAll();
        restaurantRepository.deleteAll();
    }

    @Test
    void planningReturnsTablesAndAssignedReservationsForDay() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        RestaurantTable table = createTable(restaurant, room, "A1", 2, 4, true);
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer, ReservationStatus.CONFIRMED, LocalDate.of(2026, 5, 26), LocalTime.of(20, 0), 90, 15);
        createTableAssignment(reservation, table, "TABLE");
        String token = loginWithRole(restaurant, "waiter@example.com", Role.WAITER);

        mockMvc.perform(get("/api/restaurants/{restaurantId}/planning", restaurant.getId())
                .param("date", "2026-05-26")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.restaurant.id").value(restaurant.getId()))
            .andExpect(jsonPath("$.diningRooms.length()").value(1))
            .andExpect(jsonPath("$.diningRooms[0].tables.length()").value(1))
            .andExpect(jsonPath("$.diningRooms[0].tables[0].reservations.length()").value(1))
            .andExpect(jsonPath("$.assignedReservations.length()").value(1))
            .andExpect(jsonPath("$.timeBlocks.length()", greaterThan(10)));
    }

    @Test
    void planningDoesNotShowOtherRestaurantData() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        Restaurant otherRestaurant = createRestaurant("Other", "other");
        DiningRoom otherRoom = createDiningRoom(otherRestaurant, "Other Room", 1, true, true);
        RestaurantTable otherTable = createTable(otherRestaurant, otherRoom, "B1", 2, 4, true);
        Customer otherCustomer = createCustomer(otherRestaurant, "Eva", "Santos", "+34600999888");
        Reservation otherReservation = createReservation(otherRestaurant, otherCustomer, ReservationStatus.CONFIRMED, LocalDate.of(2026, 5, 26), LocalTime.of(20, 0), 60, 15);
        createTableAssignment(otherReservation, otherTable, "TABLE");
        String token = loginWithRole(restaurant, "waiter@example.com", Role.WAITER);

        mockMvc.perform(get("/api/restaurants/{restaurantId}/planning", restaurant.getId())
                .param("date", "2026-05-26")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assignedReservations.length()").value(0))
            .andExpect(jsonPath("$.diningRooms.length()").value(0));
    }

    @Test
    void moveReservationToFreeTableWorks() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        RestaurantTable tableA = createTable(restaurant, room, "A1", 2, 4, true);
        RestaurantTable tableB = createTable(restaurant, room, "A2", 2, 4, true);
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer, ReservationStatus.CONFIRMED, LocalDate.of(2026, 5, 26), LocalTime.of(20, 0), 90, 15);
        createTableAssignment(reservation, tableA, "TABLE");
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/planning/move-reservation", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reservationId": %d,
                      "tableId": %d
                    }
                    """.formatted(reservation.getId(), tableB.getId())))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.diningRooms[0].tables[1].reservations[0].reservationId").value(reservation.getId()));
    }

    @Test
    void moveReservationToOccupiedTableFails() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        RestaurantTable targetTable = createTable(restaurant, room, "A1", 2, 4, true);
        RestaurantTable currentTable = createTable(restaurant, room, "A2", 2, 4, true);
        Customer customerOne = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Customer customerTwo = createCustomer(restaurant, "Eva", "Santos", "+34600999888");
        Reservation movingReservation = createReservation(restaurant, customerOne, ReservationStatus.CONFIRMED, LocalDate.of(2026, 5, 26), LocalTime.of(20, 0), 90, 15);
        Reservation occupiedReservation = createReservation(restaurant, customerTwo, ReservationStatus.CONFIRMED, LocalDate.of(2026, 5, 26), LocalTime.of(20, 30), 60, 15);
        createTableAssignment(movingReservation, currentTable, "TABLE");
        createTableAssignment(occupiedReservation, targetTable, "TABLE");
        String token = loginWithRole(restaurant, "manager@example.com", Role.MANAGER);

        mockMvc.perform(post("/api/restaurants/{restaurantId}/planning/move-reservation", restaurant.getId())
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {
                      "reservationId": %d,
                      "tableId": %d
                    }
                    """.formatted(movingReservation.getId(), targetTable.getId())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.code").value("CONFLICT"));
    }

    @Test
    void unassignedReservationAppearsSeparated() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        createDiningRoom(restaurant, "Main Room", 1, true, true);
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        createReservation(restaurant, customer, ReservationStatus.PENDING, LocalDate.of(2026, 5, 26), LocalTime.of(20, 0), 90, 15);
        String token = loginWithRole(restaurant, "waiter@example.com", Role.WAITER);

        mockMvc.perform(get("/api/restaurants/{restaurantId}/planning", restaurant.getId())
                .param("date", "2026-05-26")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.unassignedReservations.length()").value(1))
            .andExpect(jsonPath("$.assignedReservations.length()").value(0));
    }

    @Test
    void cancelledReservationsDoNotOccupyTable() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        RestaurantTable table = createTable(restaurant, room, "A1", 2, 4, true);
        Customer customer = createCustomer(restaurant, "Ana", "Lopez", "+34600111222");
        Reservation reservation = createReservation(restaurant, customer, ReservationStatus.CANCELLED, LocalDate.of(2026, 5, 26), LocalTime.of(20, 0), 90, 15);
        reservation.setCancelledAt(Instant.now());
        reservationRepository.save(reservation);
        createTableAssignment(reservation, table, "TABLE");
        String token = loginWithRole(restaurant, "waiter@example.com", Role.WAITER);

        mockMvc.perform(get("/api/restaurants/{restaurantId}/planning", restaurant.getId())
                .param("date", "2026-05-26")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.assignedReservations.length()").value(0))
            .andExpect(jsonPath("$.diningRooms[0].tables[0].reservations.length()").value(0));
    }

    @Test
    void storageTableDoesNotAppearAsNormalDiningRoomTable() throws Exception {
        Restaurant restaurant = createRestaurant("Main", "main");
        DiningRoom room = createDiningRoom(restaurant, "Main Room", 1, true, true);
        createTable(restaurant, room, "A1", 2, 4, true);
        createStorageTable(restaurant, "STORE-1");
        String token = loginWithRole(restaurant, "waiter@example.com", Role.WAITER);

        mockMvc.perform(get("/api/restaurants/{restaurantId}/planning", restaurant.getId())
                .param("date", "2026-05-26")
                .header(HttpHeaders.AUTHORIZATION, bearer(token)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.diningRooms[0].tables.length()").value(1))
            .andExpect(jsonPath("$.diningRooms[0].tables[0].code").value("A1"));
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

    private DiningRoom createDiningRoom(Restaurant restaurant, String name, int priority, boolean accessible, boolean active) {
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

    private RestaurantTable createTable(Restaurant restaurant, DiningRoom diningRoom, String code, int minCapacity, int maxCapacity, boolean active) {
        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setDiningRoom(diningRoom);
        table.setTableType(TableType.FIXED);
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

    private RestaurantTable createStorageTable(Restaurant restaurant, String code) {
        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setDiningRoom(null);
        table.setTableType(TableType.STORAGE);
        table.setCode(code);
        table.setLabel("Storage " + code);
        table.setMinCapacity(2);
        table.setMaxCapacity(6);
        table.setShape("RECTANGLE");
        table.setX(0);
        table.setY(0);
        table.setWidth(120);
        table.setHeight(80);
        table.setActive(true);
        return restaurantTableRepository.save(table);
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
        LocalDate reservationDate,
        LocalTime startTime,
        int estimatedDurationMin,
        int cleaningBufferMin
    ) {
        Reservation reservation = new Reservation();
        reservation.setRestaurant(restaurant);
        reservation.setCustomer(customer);
        reservation.setChannel(ReservationChannel.MANUAL);
        reservation.setStatus(status);
        reservation.setPartySize(4);
        reservation.setReservationDate(reservationDate);
        reservation.setStartTime(startTime);
        reservation.setEndTime(startTime.plusMinutes(estimatedDurationMin));
        reservation.setEstimatedDurationMin(estimatedDurationMin);
        reservation.setCleaningBufferMin(cleaningBufferMin);
        reservation.setSpecialRequests(null);
        reservation.setAccessibilityRequired(false);
        return reservationRepository.save(reservation);
    }

    private ReservationAssignment createTableAssignment(Reservation reservation, RestaurantTable table, String assignmentType) {
        ReservationAssignment assignment = new ReservationAssignment();
        assignment.setReservation(reservation);
        assignment.setAssignmentType(assignmentType);
        assignment.setDiningRoom(table.getDiningRoom());
        assignment.setTable(table);
        assignment.setScore(0d);
        assignment.setExplanationJson("{\"summary\":\"assignment\"}");
        assignment.setAssignedAt(Instant.now());
        assignment.setActive(true);
        return reservationAssignmentRepository.save(assignment);
    }

    @SuppressWarnings("unused")
    private ReservationAssignment createCombinationAssignment(Reservation reservation, TableCombination combination) {
        ReservationAssignment assignment = new ReservationAssignment();
        assignment.setReservation(reservation);
        assignment.setAssignmentType("TABLE_COMBINATION");
        assignment.setTableCombination(combination);
        assignment.setScore(0d);
        assignment.setExplanationJson("{\"summary\":\"assignment\"}");
        assignment.setAssignedAt(Instant.now());
        assignment.setActive(true);
        return reservationAssignmentRepository.save(assignment);
    }

    @SuppressWarnings("unused")
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

    private String bearer(String token) {
        return "Bearer " + token;
    }
}
