package com.restaurantplanner.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurantplanner.diningroom.domain.DiningRoom;
import com.restaurantplanner.optimization.domain.AssignmentCandidate;
import com.restaurantplanner.optimization.domain.AssignmentCandidateType;
import com.restaurantplanner.optimization.domain.CandidateAvailability;
import com.restaurantplanner.optimization.service.AvailabilityChecker;
import com.restaurantplanner.reservation.domain.Reservation;
import com.restaurantplanner.reservation.domain.ReservationAssignment;
import com.restaurantplanner.reservation.domain.ReservationChannel;
import com.restaurantplanner.reservation.domain.ReservationStatus;
import com.restaurantplanner.table.domain.RestaurantTable;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AvailabilityCheckerTest {

    private AvailabilityChecker checker;
    private DiningRoom mainRoom;
    private DiningRoom accessibleRoom;
    private DiningRoom inaccessibleRoom;

    @BeforeEach
    void setUp() {
        checker = new AvailabilityChecker();
        mainRoom = createRoom("Main", 1, true, true);
        accessibleRoom = createRoom("Accessible", 1, true, true);
        inaccessibleRoom = createRoom("Upper", 2, false, true);
    }

    @Test
    void sufficientCapacityIsAvailable() {
        Reservation reservation = createReservation(2, LocalTime.of(20, 0), 90, 15, false);
        AssignmentCandidate candidate = createTableCandidate(mainRoom, "T2", 2, 2);

        CandidateAvailability availability = checker.evaluate(candidate, reservation, List.of());
        assertTrue(availability.available());
        assertTrue(availability.rejectionReasons().isEmpty());
    }

    @Test
    void insufficientCapacityFails() {
        Reservation reservation = createReservation(6, LocalTime.of(20, 0), 90, 15, false);
        AssignmentCandidate candidate = createTableCandidate(mainRoom, "T2", 2, 2);

        CandidateAvailability availability = checker.evaluate(candidate, reservation, List.of());
        assertFalse(availability.available());
        assertTrue(availability.rejectionReasons().contains("insufficient_capacity"));
    }

    @Test
    void belowMinCapacityFails() {
        Reservation reservation = createReservation(2, LocalTime.of(20, 0), 90, 15, false);
        AssignmentCandidate candidate = createTableCandidateWithMin(mainRoom, "T6", 4, 6);

        CandidateAvailability availability = checker.evaluate(candidate, reservation, List.of());
        assertFalse(availability.available());
        assertTrue(availability.rejectionReasons().contains("below_min_capacity"));
    }

    @Test
    void inactiveTableFails() {
        DiningRoom room = createRoom("Main", 1, true, true);
        Reservation reservation = createReservation(2, LocalTime.of(20, 0), 90, 15, false);
        RestaurantTable table = createTableRaw(room, "T2", 2, 2);
        table.setActive(false);
        AssignmentCandidate candidate = new AssignmentCandidate(
            AssignmentCandidateType.TABLE, table, null, List.of(table), 2, 2, "T2"
        );

        CandidateAvailability availability = checker.evaluate(candidate, reservation, List.of());
        assertFalse(availability.available());
        assertTrue(availability.rejectionReasons().contains("inactive_table"));
    }

    @Test
    void inactiveDiningRoomFails() {
        DiningRoom inactiveRoom = createRoom("Closed", 1, true, false);
        Reservation reservation = createReservation(2, LocalTime.of(20, 0), 90, 15, false);
        AssignmentCandidate candidate = createTableCandidate(inactiveRoom, "T2", 2, 2);

        CandidateAvailability availability = checker.evaluate(candidate, reservation, List.of());
        assertFalse(availability.available());
        assertTrue(availability.rejectionReasons().contains("inactive_dining_room"));
    }

    @Test
    void accessibilityRequiredFailsOnInaccessibleRoom() {
        Reservation reservation = createReservation(2, LocalTime.of(20, 0), 90, 15, true);
        AssignmentCandidate candidate = createTableCandidate(inaccessibleRoom, "T2", 2, 2);

        CandidateAvailability availability = checker.evaluate(candidate, reservation, List.of());
        assertFalse(availability.available());
        assertTrue(availability.rejectionReasons().contains("accessibility_mismatch"));
    }

    @Test
    void accessibilityRequiredPassesOnAccessibleRoom() {
        Reservation reservation = createReservation(2, LocalTime.of(20, 0), 90, 15, true);
        AssignmentCandidate candidate = createTableCandidate(accessibleRoom, "T2", 2, 2);

        CandidateAvailability availability = checker.evaluate(candidate, reservation, List.of());
        assertTrue(availability.available());
    }

    @Test
    void overlapDetected() {
        Reservation existingReservation = createReservation(2, LocalTime.of(20, 0), 90, 30, false);
        Reservation targetReservation = createReservation(2, LocalTime.of(20, 30), 60, 15, false);
        AssignmentCandidate candidate = createTableCandidate(mainRoom, "T1", 2, 4);

        RestaurantTable table = createTableRaw(mainRoom, "T1", 2, 4);
        ReservationAssignment existingAssignment = createAssignment(existingReservation, table);

        CandidateAvailability availability = checker.evaluate(candidate, targetReservation, List.of(existingAssignment));
        assertFalse(availability.available());
        assertTrue(availability.rejectionReasons().contains("time_overlap"));
    }

    @Test
    void noOverlapWhenBuffersAreRespected() {
        Reservation existingReservation = createReservation(2, LocalTime.of(19, 0), 60, 30, false);
        Reservation targetReservation = createReservation(2, LocalTime.of(20, 30), 60, 15, false);
        AssignmentCandidate candidate = createTableCandidate(mainRoom, "T1", 2, 4);

        RestaurantTable table = createTableRaw(mainRoom, "T1", 2, 4);
        ReservationAssignment existingAssignment = createAssignment(existingReservation, table);

        CandidateAvailability availability = checker.evaluate(candidate, targetReservation, List.of(existingAssignment));
        assertTrue(availability.available());
        assertFalse(availability.rejectionReasons().contains("time_overlap"));
    }

    @Test
    void combinationWithInactiveTableFails() {
        DiningRoom room = createRoom("Main", 1, true, true);
        RestaurantTable activeTable = createTableRaw(room, "T2A", 2, 2);
        RestaurantTable inactiveTable = createTableRaw(room, "T2B", 2, 2);
        inactiveTable.setActive(false);

        Reservation reservation = createReservation(4, LocalTime.of(20, 0), 90, 15, false);
        AssignmentCandidate combination = new AssignmentCandidate(
            AssignmentCandidateType.TABLE_COMBINATION, null, null, List.of(activeTable, inactiveTable), 4, 4, "Pair"
        );

        CandidateAvailability availability = checker.evaluate(combination, reservation, List.of());
        assertFalse(availability.available());
        assertTrue(availability.rejectionReasons().contains("inactive_table"));
    }

    private DiningRoom createRoom(String name, int priority, boolean accessible, boolean active) {
        DiningRoom room = new DiningRoom();
        room.setId((long) name.hashCode());
        room.setName(name);
        room.setPriority(priority);
        room.setAccessible(accessible);
        room.setActive(active);
        return room;
    }

    private RestaurantTable createTableRaw(DiningRoom room, String code, int minCapacity, int maxCapacity) {
        RestaurantTable table = new RestaurantTable();
        table.setId((long) code.hashCode());
        table.setCode(code);
        table.setMinCapacity(minCapacity);
        table.setMaxCapacity(maxCapacity);
        table.setActive(true);
        table.setDiningRoom(room);
        table.setShape("RECTANGLE");
        table.setX(0);
        table.setY(0);
        table.setWidth(100);
        table.setHeight(100);
        return table;
    }

    private AssignmentCandidate createTableCandidate(DiningRoom room, String code, int minCapacity, int maxCapacity) {
        RestaurantTable table = createTableRaw(room, code, minCapacity, maxCapacity);
        return new AssignmentCandidate(AssignmentCandidateType.TABLE, table, null, List.of(table), minCapacity, maxCapacity, code);
    }

    private AssignmentCandidate createTableCandidateWithMin(DiningRoom room, String code, int minCapacity, int maxCapacity) {
        RestaurantTable table = createTableRaw(room, code, minCapacity, maxCapacity);
        return new AssignmentCandidate(AssignmentCandidateType.TABLE, table, null, List.of(table), minCapacity, maxCapacity, code);
    }

    private Reservation createReservation(int partySize, LocalTime startTime, int durationMin, int cleaningBuffer, boolean accessibilityRequired) {
        Reservation reservation = new Reservation();
        reservation.setId((long) (startTime.hashCode() + partySize));
        reservation.setPartySize(partySize);
        reservation.setStartTime(startTime);
        reservation.setEndTime(startTime.plusMinutes(durationMin));
        reservation.setEstimatedDurationMin(durationMin);
        reservation.setCleaningBufferMin(cleaningBuffer);
        reservation.setAccessibilityRequired(accessibilityRequired);
        reservation.setStatus(ReservationStatus.PENDING);
        reservation.setChannel(ReservationChannel.MANUAL);
        reservation.setReservationDate(LocalDate.of(2026, 5, 26));
        return reservation;
    }

    private ReservationAssignment createAssignment(Reservation reservation, RestaurantTable table) {
        ReservationAssignment assignment = new ReservationAssignment();
        assignment.setReservation(reservation);
        assignment.setTable(table);
        assignment.setAssignmentType("TABLE");
        assignment.setActive(true);
        assignment.setAssignedAt(Instant.now());
        return assignment;
    }
}
