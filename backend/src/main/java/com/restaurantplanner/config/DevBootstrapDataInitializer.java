package com.restaurantplanner.config;

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
import com.restaurantplanner.tablecombination.domain.TableCombination;
import com.restaurantplanner.tablecombination.domain.TableCombinationItem;
import com.restaurantplanner.tablecombination.domain.TableCombinationRepository;
import com.restaurantplanner.user.domain.User;
import com.restaurantplanner.user.domain.UserRepository;
import com.restaurantplanner.user.domain.UserStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@Profile("dev")
public class DevBootstrapDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DevBootstrapDataInitializer.class);
    private static final int DEFAULT_LAYOUT_WIDTH = 1400;
    private static final int DEFAULT_LAYOUT_HEIGHT = 900;

    private final UserRepository userRepository;
    private final RestaurantRepository restaurantRepository;
    private final RoleAssignmentRepository roleAssignmentRepository;
    private final DiningRoomRepository diningRoomRepository;
    private final RestaurantTableRepository tableRepository;
    private final TableCombinationRepository tableCombinationRepository;
    private final CustomerRepository customerRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationAssignmentRepository reservationAssignmentRepository;
    private final PasswordEncoder passwordEncoder;
    private final String demoName;
    private final String demoEmail;
    private final String demoPassword;
    private final String restaurantName;
    private final String restaurantSlug;
    private final String restaurantTimezone;

    public DevBootstrapDataInitializer(
        UserRepository userRepository,
        RestaurantRepository restaurantRepository,
        RoleAssignmentRepository roleAssignmentRepository,
        DiningRoomRepository diningRoomRepository,
        RestaurantTableRepository tableRepository,
        TableCombinationRepository tableCombinationRepository,
        CustomerRepository customerRepository,
        ReservationRepository reservationRepository,
        ReservationAssignmentRepository reservationAssignmentRepository,
        PasswordEncoder passwordEncoder,
        @Value("${app.bootstrap.admin.name:Demo Owner}") String demoName,
        @Value("${app.bootstrap.admin.email:demo@restaurant.com}") String demoEmail,
        @Value("${app.bootstrap.admin.password:Demo1234!}") String demoPassword,
        @Value("${app.bootstrap.restaurant.name:Demo Restaurant}") String restaurantName,
        @Value("${app.bootstrap.restaurant.slug:demo-restaurant}") String restaurantSlug,
        @Value("${app.bootstrap.restaurant.timezone:Europe/Madrid}") String restaurantTimezone
    ) {
        this.userRepository = userRepository;
        this.restaurantRepository = restaurantRepository;
        this.roleAssignmentRepository = roleAssignmentRepository;
        this.diningRoomRepository = diningRoomRepository;
        this.tableRepository = tableRepository;
        this.tableCombinationRepository = tableCombinationRepository;
        this.customerRepository = customerRepository;
        this.reservationRepository = reservationRepository;
        this.reservationAssignmentRepository = reservationAssignmentRepository;
        this.passwordEncoder = passwordEncoder;
        this.demoName = demoName;
        this.demoEmail = demoEmail;
        this.demoPassword = demoPassword;
        this.restaurantName = restaurantName;
        this.restaurantSlug = restaurantSlug;
        this.restaurantTimezone = restaurantTimezone;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        Restaurant restaurant = findOrCreateRestaurant();
        User demoUser = findOrCreateDemoUser();
        ensureOwnerRole(demoUser, restaurant);

        Map<String, DiningRoom> diningRooms = ensureDiningRooms(restaurant);
        Map<String, RestaurantTable> tables = ensureTables(restaurant, diningRooms);
        ensureCombinations(restaurant, tables);
        Map<String, Customer> customers = ensureCustomers(restaurant);
        ensureTodayReservations(restaurant, demoUser, tables, customers);

        log.info("Demo data ready for dev profile: email='{}' password='{}'", demoEmail, demoPassword);
    }

    private Restaurant findOrCreateRestaurant() {
        String normalizedSlug = restaurantSlug.trim().toLowerCase(Locale.ROOT);
        return restaurantRepository.findBySlugIgnoreCase(normalizedSlug).orElseGet(() -> {
            Restaurant restaurant = new Restaurant();
            restaurant.setName(restaurantName.trim());
            restaurant.setSlug(normalizedSlug);
            restaurant.setTimezone(restaurantTimezone.trim());
            restaurant.setStatus(RestaurantStatus.ACTIVE);
            return restaurantRepository.save(restaurant);
        });
    }

    private User findOrCreateDemoUser() {
        return userRepository.findByEmailIgnoreCase(normalizeEmail(demoEmail)).orElseGet(() -> {
            User user = new User();
            user.setName(demoName.trim());
            user.setEmail(normalizeEmail(demoEmail));
            user.setPasswordHash(passwordEncoder.encode(demoPassword));
            user.setStatus(UserStatus.ACTIVE);
            return userRepository.save(user);
        });
    }

    private void ensureOwnerRole(User user, Restaurant restaurant) {
        boolean alreadyAssigned = roleAssignmentRepository.findByUserId(user.getId()).stream()
            .anyMatch(roleAssignment ->
                roleAssignment.getRestaurant() != null
                    && roleAssignment.getRestaurant().getId().equals(restaurant.getId())
                    && roleAssignment.getRole() == Role.RESTAURANT_OWNER
            );

        if (alreadyAssigned) {
            return;
        }

        RoleAssignment roleAssignment = new RoleAssignment();
        roleAssignment.setUser(user);
        roleAssignment.setRestaurant(restaurant);
        roleAssignment.setRole(Role.RESTAURANT_OWNER);
        roleAssignmentRepository.save(roleAssignment);
    }

    private Map<String, DiningRoom> ensureDiningRooms(Restaurant restaurant) {
        List<DiningRoom> existingRooms = diningRoomRepository.findByRestaurantIdOrderByPriorityAscIdAsc(restaurant.getId());
        Map<String, DiningRoom> roomsByName = new HashMap<>();
        for (DiningRoom room : existingRooms) {
            roomsByName.put(normalizeKey(room.getName()), room);
        }

        roomsByName.computeIfAbsent(normalizeKey("Main Dining Room"), ignored -> createDiningRoom(restaurant, "Main Dining Room", 1, true));
        roomsByName.computeIfAbsent(normalizeKey("Side Dining Room"), ignored -> createDiningRoom(restaurant, "Side Dining Room", 2, true));
        roomsByName.computeIfAbsent(normalizeKey("Upper Dining Room"), ignored -> createDiningRoom(restaurant, "Upper Dining Room", 3, false));
        return roomsByName;
    }

    private DiningRoom createDiningRoom(Restaurant restaurant, String name, int priority, boolean accessible) {
        DiningRoom diningRoom = new DiningRoom();
        diningRoom.setRestaurant(restaurant);
        diningRoom.setName(name);
        diningRoom.setPriority(priority);
        diningRoom.setAccessible(accessible);
        diningRoom.setActive(true);
        diningRoom.setLayoutWidth(DEFAULT_LAYOUT_WIDTH);
        diningRoom.setLayoutHeight(DEFAULT_LAYOUT_HEIGHT);
        return diningRoomRepository.save(diningRoom);
    }

    private Map<String, RestaurantTable> ensureTables(Restaurant restaurant, Map<String, DiningRoom> rooms) {
        List<RestaurantTable> existingTables = tableRepository.findByRestaurantIdOrderByDiningRoomIdAscCodeAsc(restaurant.getId());
        Map<String, RestaurantTable> tablesByCode = new HashMap<>();
        for (RestaurantTable table : existingTables) {
            tablesByCode.put(normalizeKey(table.getCode()), table);
        }

        DiningRoom main = rooms.get(normalizeKey("Main Dining Room"));
        DiningRoom side = rooms.get(normalizeKey("Side Dining Room"));
        DiningRoom upper = rooms.get(normalizeKey("Upper Dining Room"));

        tablesByCode.computeIfAbsent(normalizeKey("T1"), ignored -> createTable(restaurant, main, "T1", "Table 1", 1, 2, 100, 120));
        tablesByCode.computeIfAbsent(normalizeKey("T2"), ignored -> createTable(restaurant, main, "T2", "Table 2", 1, 2, 320, 120));
        tablesByCode.computeIfAbsent(normalizeKey("T3"), ignored -> createTable(restaurant, main, "T3", "Table 3", 2, 4, 100, 340));
        tablesByCode.computeIfAbsent(normalizeKey("T4"), ignored -> createTable(restaurant, main, "T4", "Table 4", 2, 4, 360, 340));
        tablesByCode.computeIfAbsent(normalizeKey("T5"), ignored -> createTable(restaurant, side, "T5", "Table 5", 4, 6, 160, 180));
        tablesByCode.computeIfAbsent(normalizeKey("T6"), ignored -> createTable(restaurant, upper, "T6", "Table 6", 1, 2, 140, 160));
        tablesByCode.computeIfAbsent(normalizeKey("T7"), ignored -> createTable(restaurant, upper, "T7", "Table 7", 2, 4, 380, 160));
        return tablesByCode;
    }

    private RestaurantTable createTable(
        Restaurant restaurant,
        DiningRoom diningRoom,
        String code,
        String label,
        int minCapacity,
        int maxCapacity,
        int x,
        int y
    ) {
        RestaurantTable table = new RestaurantTable();
        table.setRestaurant(restaurant);
        table.setDiningRoom(diningRoom);
        table.setCode(code);
        table.setLabel(label);
        table.setMinCapacity(minCapacity);
        table.setMaxCapacity(maxCapacity);
        table.setShape("RECTANGLE");
        table.setX(x);
        table.setY(y);
        table.setWidth(maxCapacity >= 6 ? 180 : 140);
        table.setHeight(100);
        table.setActive(true);
        return tableRepository.save(table);
    }

    private void ensureCombinations(Restaurant restaurant, Map<String, RestaurantTable> tables) {
        List<TableCombination> combinations = tableCombinationRepository.findByRestaurantIdAndActiveTrueOrderByNameAscIdAsc(restaurant.getId());
        ensureCombination(restaurant, combinations, "Table 1 + Table 2", 2, 4, tables.get(normalizeKey("T1")), tables.get(normalizeKey("T2")));
        ensureCombination(restaurant, combinations, "Table 3 + Table 4", 4, 8, tables.get(normalizeKey("T3")), tables.get(normalizeKey("T4")));
    }

    private void ensureCombination(
        Restaurant restaurant,
        List<TableCombination> existingCombinations,
        String name,
        int minCapacity,
        int maxCapacity,
        RestaurantTable firstTable,
        RestaurantTable secondTable
    ) {
        boolean exists = existingCombinations.stream().anyMatch(combination -> combination.getName().equalsIgnoreCase(name));
        if (exists || firstTable == null || secondTable == null) {
            return;
        }

        TableCombination combination = new TableCombination();
        combination.setRestaurant(restaurant);
        combination.setName(name);
        combination.setMinCapacity(minCapacity);
        combination.setMaxCapacity(maxCapacity);
        combination.setActive(true);
        combination.getItems().add(createCombinationItem(combination, firstTable, 1));
        combination.getItems().add(createCombinationItem(combination, secondTable, 2));
        tableCombinationRepository.save(combination);
    }

    private TableCombinationItem createCombinationItem(TableCombination combination, RestaurantTable table, int orderIndex) {
        TableCombinationItem item = new TableCombinationItem();
        item.setTableCombination(combination);
        item.setTable(table);
        item.setOrderIndex(orderIndex);
        return item;
    }

    private Map<String, Customer> ensureCustomers(Restaurant restaurant) {
        List<Customer> existingCustomers = customerRepository.findByRestaurantIdOrderByLastNameAscFirstNameAscIdAsc(restaurant.getId());
        Map<String, Customer> customersByName = new HashMap<>();
        for (Customer customer : existingCustomers) {
            customersByName.put(normalizeKey(customer.getFirstName() + " " + customer.getLastName()), customer);
        }

        customersByName.computeIfAbsent(normalizeKey("John Smith"), ignored -> createCustomer(restaurant, "John", "Smith", "+353870000001"));
        customersByName.computeIfAbsent(normalizeKey("Maria Garcia"), ignored -> createCustomer(restaurant, "Maria", "Garcia", "+353870000002"));
        customersByName.computeIfAbsent(normalizeKey("David Murphy"), ignored -> createCustomer(restaurant, "David", "Murphy", "+353870000003"));
        return customersByName;
    }

    private Customer createCustomer(Restaurant restaurant, String firstName, String lastName, String phone) {
        Customer customer = new Customer();
        customer.setRestaurant(restaurant);
        customer.setFirstName(firstName);
        customer.setLastName(lastName);
        customer.setPhone(phone);
        customer.setNotes("Demo customer");
        customer.setTagsJson("[]");
        return customerRepository.save(customer);
    }

    private void ensureTodayReservations(
        Restaurant restaurant,
        User demoUser,
        Map<String, RestaurantTable> tables,
        Map<String, Customer> customers
    ) {
        LocalDate today = LocalDate.now();
        List<Reservation> todayReservations = reservationRepository
            .findByRestaurantIdAndReservationDateOrderByStartTimeAscIdAsc(restaurant.getId(), today);

        Reservation first = ensureReservation(
            restaurant,
            todayReservations,
            customers.get(normalizeKey("John Smith")),
            2,
            today,
            LocalTime.of(18, 0),
            ReservationStatus.CONFIRMED
        );
        Reservation second = ensureReservation(
            restaurant,
            todayReservations,
            customers.get(normalizeKey("Maria Garcia")),
            4,
            today,
            LocalTime.of(19, 0),
            ReservationStatus.CONFIRMED
        );
        Reservation third = ensureReservation(
            restaurant,
            todayReservations,
            customers.get(normalizeKey("David Murphy")),
            6,
            today,
            LocalTime.of(20, 0),
            ReservationStatus.CONFIRMED
        );
        ensureReservation(
            restaurant,
            todayReservations,
            customers.get(normalizeKey("John Smith")),
            2,
            today,
            LocalTime.of(20, 30),
            ReservationStatus.PENDING
        );
        ensureReservation(
            restaurant,
            todayReservations,
            customers.get(normalizeKey("Maria Garcia")),
            4,
            today,
            LocalTime.of(21, 0),
            ReservationStatus.PENDING
        );

        assignIfUnassigned(first, tables.get(normalizeKey("T1")), demoUser, 96.0);
        assignIfUnassigned(second, tables.get(normalizeKey("T3")), demoUser, 92.0);
        assignIfUnassigned(third, tables.get(normalizeKey("T5")), demoUser, 88.0);
    }

    private Reservation ensureReservation(
        Restaurant restaurant,
        List<Reservation> existingReservations,
        Customer customer,
        int partySize,
        LocalDate date,
        LocalTime startTime,
        ReservationStatus status
    ) {
        Optional<Reservation> existing = existingReservations.stream()
            .filter(reservation -> reservation.getStartTime().equals(startTime))
            .filter(reservation -> reservation.getPartySize() == partySize)
            .findFirst();

        if (existing.isPresent()) {
            return existing.get();
        }

        Reservation reservation = new Reservation();
        reservation.setRestaurant(restaurant);
        reservation.setCustomer(customer);
        reservation.setChannel(ReservationChannel.MANUAL);
        reservation.setStatus(status);
        reservation.setPartySize(partySize);
        reservation.setReservationDate(date);
        reservation.setStartTime(startTime);
        reservation.setEstimatedDurationMin(90);
        reservation.setCleaningBufferMin(15);
        reservation.setEndTime(startTime.plusMinutes(90));
        reservation.setAccessibilityRequired(false);
        reservation.setSpecialRequests("Demo reservation");
        if (status == ReservationStatus.CONFIRMED) {
            reservation.setConfirmedAt(Instant.now());
        }
        Reservation savedReservation = reservationRepository.save(reservation);
        existingReservations.add(savedReservation);
        return savedReservation;
    }

    private void assignIfUnassigned(Reservation reservation, RestaurantTable table, User demoUser, double score) {
        if (reservation == null || table == null || !reservationAssignmentRepository.findByReservationIdAndActiveTrue(reservation.getId()).isEmpty()) {
            return;
        }

        ReservationAssignment assignment = new ReservationAssignment();
        assignment.setReservation(reservation);
        assignment.setAssignmentType("TABLE");
        assignment.setDiningRoom(table.getDiningRoom());
        assignment.setTable(table);
        assignment.setScore(score);
        assignment.setExplanationJson("""
            {"source":"dev-bootstrap","reason":"Demo assignment seeded for local end-to-end testing."}
            """.trim());
        assignment.setAssignedBy(demoUser);
        assignment.setAssignedAt(Instant.now());
        assignment.setActive(true);
        reservationAssignmentRepository.save(assignment);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeKey(String value) {
        return value.trim().toLowerCase(Locale.ROOT);
    }
}
