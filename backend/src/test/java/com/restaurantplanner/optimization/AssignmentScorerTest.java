package com.restaurantplanner.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.restaurantplanner.diningroom.domain.DiningRoom;
import com.restaurantplanner.optimization.domain.AssignmentCandidate;
import com.restaurantplanner.optimization.domain.AssignmentCandidateType;
import com.restaurantplanner.optimization.domain.CandidateAvailability;
import com.restaurantplanner.optimization.domain.ScoredCandidate;
import com.restaurantplanner.optimization.service.AssignmentScorer;
import com.restaurantplanner.reservation.domain.Reservation;
import com.restaurantplanner.reservation.domain.ReservationChannel;
import com.restaurantplanner.reservation.domain.ReservationStatus;
import com.restaurantplanner.table.domain.RestaurantTable;
import com.restaurantplanner.tablecombination.domain.CombinationType;
import com.restaurantplanner.tablecombination.domain.OperationalCostLevel;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AssignmentScorerTest {

    private AssignmentScorer scorer;
    private Reservation twoPersonReservation;
    private Reservation fourPersonReservation;
    private Reservation sixPersonReservation;
    private Reservation accessibilityReservation;

    @BeforeEach
    void setUp() {
        scorer = new AssignmentScorer();
        twoPersonReservation = createReservation(2, LocalTime.of(20, 0), 90, 15, false);
        fourPersonReservation = createReservation(4, LocalTime.of(20, 0), 90, 15, false);
        sixPersonReservation = createReservation(6, LocalTime.of(20, 0), 90, 15, false);
        accessibilityReservation = createReservation(2, LocalTime.of(20, 0), 90, 15, true);
    }

    @Test
    void capacityFitRewardsCloseCapacity() {
        DiningRoom room = createRoom("Main", 1, true, true);
        AssignmentCandidate table2 = createTableCandidate(room, "T2", 2, 2, 1);
        AssignmentCandidate table6 = createTableCandidate(room, "T6", 2, 6, 2);

        ScoredCandidate scored2 = scorer.score(table2, twoPersonReservation, available(), List.of(table2, table6));
        ScoredCandidate scored6 = scorer.score(table6, twoPersonReservation, available(), List.of(table2, table6));

        assertTrue(scored2.bonuses().getOrDefault("capacity_fit", 0.0) > scored6.bonuses().getOrDefault("capacity_fit", 0.0),
            "Table with closer capacity should have higher capacity_fit");
    }

    @Test
    void roomPriorityRewardsMainRoom() {
        DiningRoom mainRoom = createRoom("Main", 1, true, true);
        DiningRoom sideRoom = createRoom("Side", 2, true, true);
        AssignmentCandidate mainTable = createTableCandidate(mainRoom, "A1", 2, 4, 1);
        AssignmentCandidate sideTable = createTableCandidate(sideRoom, "B1", 2, 4, 2);

        ScoredCandidate mainScored = scorer.score(mainTable, twoPersonReservation, available(), List.of(mainTable, sideTable));
        ScoredCandidate sideScored = scorer.score(sideTable, twoPersonReservation, available(), List.of(mainTable, sideTable));

        assertTrue(mainScored.bonuses().getOrDefault("room_priority", 0.0) > sideScored.bonuses().getOrDefault("room_priority", 0.0),
            "Main room should have higher room_priority bonus");
    }

    @Test
    void wastedCapacityPenaltyIncreasesWithWaste() {
        DiningRoom room = createRoom("Main", 1, true, true);
        AssignmentCandidate table4 = createTableCandidate(room, "T4", 2, 4, 1);
        AssignmentCandidate table6 = createTableCandidate(room, "T6", 2, 6, 2);

        ScoredCandidate scored4 = scorer.score(table4, twoPersonReservation, available(), List.of(table4, table6));
        ScoredCandidate scored6 = scorer.score(table6, twoPersonReservation, available(), List.of(table4, table6));

        assertTrue(scored6.penalties().getOrDefault("wasted_capacity_penalty", 0.0) > scored4.penalties().getOrDefault("wasted_capacity_penalty", 0.0),
            "Larger table should have higher wasted capacity penalty");
    }

    @Test
    void deadGapPenaltyAppliedForSmallGaps() {
        DiningRoom room = createRoom("Main", 1, true, true);
        AssignmentCandidate table = createTableCandidate(room, "T2", 2, 2, 1);

        CandidateAvailability withSmallGap = new CandidateAvailability(true, List.of(), 15, 15);
        CandidateAvailability withNoGap = new CandidateAvailability(true, List.of(), 120, 120);

        ScoredCandidate smallGapScored = scorer.score(table, twoPersonReservation, withSmallGap, List.of(table));
        ScoredCandidate noGapScored = scorer.score(table, twoPersonReservation, withNoGap, List.of(table));

        assertTrue(smallGapScored.penalties().getOrDefault("dead_gap_penalty", 0.0) > noGapScored.penalties().getOrDefault("dead_gap_penalty", 0.0),
            "Small gaps should incur a dead gap penalty");
    }

    @Test
    void largeTableBlockPenaltyAppliedForSmallGroupsOnBigTables() {
        DiningRoom room = createRoom("Main", 1, true, true);
        AssignmentCandidate table2 = createTableCandidate(room, "T2", 2, 2, 1);
        AssignmentCandidate table8 = createTableCandidate(room, "T8", 2, 8, 2);

        ScoredCandidate scoredSmall = scorer.score(table2, twoPersonReservation, available(), List.of(table2, table8));
        ScoredCandidate scoredBig = scorer.score(table8, twoPersonReservation, available(), List.of(table2, table8));

        double bigPenalty = scoredBig.penalties().getOrDefault("large_table_block_penalty", 0.0);
        double smallPenalty = scoredSmall.penalties().getOrDefault("large_table_block_penalty", 0.0);
        assertTrue(bigPenalty > smallPenalty,
            "Using a large table for a small group should incur large_table_block_penalty");
    }

    @Test
    void combinationComplexityPenaltyIncreasesWithTableCount() {
        DiningRoom room = createRoom("Main", 1, true, true);
        AssignmentCandidate singleTable = createTableCandidate(room, "T4", 2, 4, 1);
        AssignmentCandidate combination = createCombinationCandidate(room, "T2A+T2B", 2, 4, "Pair", List.of(
            createTableRaw(room, "T2A", 2, 2), createTableRaw(room, "T2B", 2, 2)
        ));

        ScoredCandidate singleScored = scorer.score(singleTable, fourPersonReservation, available(), List.of(singleTable, combination));
        ScoredCandidate comboScored = scorer.score(combination, fourPersonReservation, available(), List.of(singleTable, combination));

        double comboPenalty = comboScored.penalties().getOrDefault("combination_complexity_penalty", 0.0);
        double singlePenalty = singleScored.penalties().getOrDefault("combination_complexity_penalty", 0.0);
        assertTrue(comboPenalty > singlePenalty,
            "Combinations should have higher complexity penalty than single tables");
    }

    @Test
    void accessibilityMatchAppliedWhenRequired() {
        DiningRoom room = createRoom("Main", 1, true, true);
        AssignmentCandidate table = createTableCandidate(room, "T2", 2, 2, 1);

        ScoredCandidate scoredNonAccessible = scorer.score(table, twoPersonReservation, available(), List.of(table));
        ScoredCandidate scoredAccessible = scorer.score(table, accessibilityReservation, available(), List.of(table));

        assertEquals(0, scoredNonAccessible.bonuses().getOrDefault("accessibility_match", 0.0));
        assertTrue(scoredAccessible.bonuses().getOrDefault("accessibility_match", 0.0) > 0,
            "Accessibility match bonus should be applied when reservation requires accessibility");
    }

    @Test
    void totalScoreCombinesBonusesAndPenalties() {
        DiningRoom room = createRoom("Main", 1, true, true);
        Reservation reservation = createReservation(3, LocalTime.of(20, 0), 90, 15, false);
        AssignmentCandidate table4 = createTableCandidate(room, "T4", 2, 4, 1);
        AssignmentCandidate table8 = createTableCandidate(room, "T8", 2, 8, 2);

        CandidateAvailability availability = new CandidateAvailability(true, List.of(), 60, 60);
        List<AssignmentCandidate> candidates = List.of(table4, table8);

        ScoredCandidate scored4 = scorer.score(table4, reservation, availability, candidates);
        ScoredCandidate scored8 = scorer.score(table8, reservation, availability, candidates);

        assertNotNull(scored4.bonuses());
        assertNotNull(scored4.penalties());
        assertTrue(scored4.totalScore() > scored8.totalScore(),
            "A better fitting table should have a higher total score");
    }

    @Test
    void equalTablesInSameRoomHaveScoresFromDisplayNameDeterminism() {
        DiningRoom room = createRoom("Main", 1, true, true);
        AssignmentCandidate tableA = createTableCandidate(room, "A1", 2, 4, 1);
        AssignmentCandidate tableB = createTableCandidate(room, "B1", 2, 4, 2);

        ScoredCandidate scoredA = scorer.score(tableA, twoPersonReservation, available(), List.of(tableA, tableB));
        ScoredCandidate scoredB = scorer.score(tableB, twoPersonReservation, available(), List.of(tableA, tableB));

        assertEquals(scoredA.totalScore(), scoredB.totalScore(), 0.0001,
            "Identical tables should have the same score");
    }

    @Test
    void advancedCostAndSetupPenaltiesAreDeterministic() {
        DiningRoom room = createRoom("Main", 1, true, true);
        List<RestaurantTable> tables = List.of(
            createTableRaw(room, "A1", 2, 2),
            createTableRaw(room, "A2", 2, 2)
        );
        AssignmentCandidate low = new AssignmentCandidate(
            AssignmentCandidateType.TABLE_COMBINATION,
            null,
            null,
            tables,
            2,
            4,
            "Low",
            CombinationType.ADVANCED,
            OperationalCostLevel.LOW,
            20,
            List.of()
        );
        AssignmentCandidate high = new AssignmentCandidate(
            AssignmentCandidateType.TABLE_COMBINATION,
            null,
            null,
            tables,
            2,
            4,
            "High",
            CombinationType.ADVANCED,
            OperationalCostLevel.HIGH,
            80,
            List.of()
        );

        ScoredCandidate lowScore = scorer.score(low, fourPersonReservation, available(), List.of(low, high));
        ScoredCandidate highScore = scorer.score(high, fourPersonReservation, available(), List.of(low, high));

        assertEquals(8d, lowScore.penalties().get("operational_cost_penalty"), 0.001);
        assertEquals(48d, highScore.penalties().get("operational_cost_penalty"), 0.001);
        assertEquals(10d, lowScore.penalties().get("setup_time_penalty"), 0.001);
        assertEquals(30d, highScore.penalties().get("setup_time_penalty"), 0.001);
        assertTrue(lowScore.totalScore() > highScore.totalScore());
    }

    private CandidateAvailability available() {
        return new CandidateAvailability(true, List.of(), 60, 60);
    }

    private Reservation createReservation(int partySize, LocalTime startTime, int durationMin, int cleaningBuffer, boolean accessibilityRequired) {
        Reservation reservation = new Reservation();
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

    private AssignmentCandidate createTableCandidate(DiningRoom room, String code, int minCapacity, int maxCapacity, int priority) {
        RestaurantTable table = createTableRaw(room, code, minCapacity, maxCapacity);
        return new AssignmentCandidate(AssignmentCandidateType.TABLE, table, null, List.of(table), minCapacity, maxCapacity, code);
    }

    private AssignmentCandidate createCombinationCandidate(DiningRoom room, String name, int minCapacity, int maxCapacity, String displayName, List<RestaurantTable> tables) {
        return new AssignmentCandidate(AssignmentCandidateType.TABLE_COMBINATION, null, null, tables, minCapacity, maxCapacity, displayName);
    }
}
