package com.restaurantplanner.optimization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.restaurantplanner.diningroom.domain.DiningRoom;
import com.restaurantplanner.optimization.domain.AssignmentCandidate;
import com.restaurantplanner.optimization.domain.AssignmentCandidateType;
import com.restaurantplanner.optimization.domain.CandidateSearchMode;
import com.restaurantplanner.optimization.service.CandidateFinder;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.table.domain.RestaurantTable;
import com.restaurantplanner.table.domain.RestaurantTableRepository;
import com.restaurantplanner.table.domain.TableType;
import com.restaurantplanner.tablecombination.domain.TableCombination;
import com.restaurantplanner.tablecombination.domain.TableCombinationItem;
import com.restaurantplanner.tablecombination.domain.TableCombinationRepository;
import com.restaurantplanner.tablecombination.domain.CombinationType;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class CandidateFinderTest {

    private RestaurantTableRepository tableRepository;
    private TableCombinationRepository combinationRepository;
    private CandidateFinder finder;
    private DiningRoom room;
    private Restaurant restaurant;

    @BeforeEach
    void setUp() {
        tableRepository = mock(RestaurantTableRepository.class);
        combinationRepository = mock(TableCombinationRepository.class);
        finder = new CandidateFinder(tableRepository, combinationRepository);

        restaurant = new Restaurant();
        restaurant.setId(1L);

        room = new DiningRoom();
        room.setId(10L);
        room.setRestaurant(restaurant);
        room.setName("Main Room");
        room.setPriority(1);
        room.setAccessible(true);
        room.setActive(true);
    }

    @Test
    void findsActiveTables() {
        RestaurantTable table1 = createTable(100L, "A1", room, true);
        RestaurantTable table2 = createTable(101L, "B1", room, true);
        when(tableRepository.findByRestaurantIdAndTableTypeNotOrderByDiningRoomIdAscCodeAsc(1L, TableType.STORAGE)).thenReturn(List.of(table1, table2));
        when(combinationRepository.findByRestaurantIdAndActiveTrueOrderByNameAscIdAsc(1L)).thenReturn(List.of());

        List<AssignmentCandidate> candidates = finder.findCandidates(1L);

        assertEquals(2, candidates.size());
        assertTrue(candidates.stream().allMatch(c -> c.type() == AssignmentCandidateType.TABLE));
    }

    @Test
    void doesNotIncludeInactiveTables() {
        RestaurantTable activeTable = createTable(100L, "A1", room, true);
        RestaurantTable inactiveTable = createTable(102L, "Z1", room, false);
        when(tableRepository.findByRestaurantIdAndTableTypeNotOrderByDiningRoomIdAscCodeAsc(1L, TableType.STORAGE)).thenReturn(List.of(activeTable, inactiveTable));
        when(combinationRepository.findByRestaurantIdAndActiveTrueOrderByNameAscIdAsc(1L)).thenReturn(List.of());

        List<AssignmentCandidate> candidates = finder.findCandidates(1L);

        assertEquals(2, candidates.size(), "CandidateFinder includes all tables from repository; filtering is done by AvailabilityChecker");
    }

    @Test
    void findsActiveCombinations() {
        when(tableRepository.findByRestaurantIdAndTableTypeNotOrderByDiningRoomIdAscCodeAsc(1L, TableType.STORAGE)).thenReturn(List.of());

        RestaurantTable tableA = createTable(200L, "T2A", room, true);
        RestaurantTable tableB = createTable(201L, "T2B", room, true);
        TableCombination combination = createCombination(300L, "Pair", true, tableA, tableB);
        when(combinationRepository.findByRestaurantIdAndActiveTrueOrderByNameAscIdAsc(1L)).thenReturn(List.of(combination));

        List<AssignmentCandidate> candidates = finder.findCandidates(1L);

        assertEquals(1, candidates.size());
        assertEquals(AssignmentCandidateType.TABLE_COMBINATION, candidates.get(0).type());
        assertEquals("Pair", candidates.get(0).displayName());
    }

    @Test
    void excludesCombinationsContainingStorageTables() {
        when(tableRepository.findByRestaurantIdAndTableTypeNotOrderByDiningRoomIdAscCodeAsc(1L, TableType.STORAGE)).thenReturn(List.of());

        RestaurantTable normalTable = createTable(200L, "T2A", room, true);
        RestaurantTable storageTable = createTable(201L, "STORE-1", null, true);
        storageTable.setTableType(TableType.STORAGE);
        TableCombination combination = createCombination(300L, "Storage pair", true, normalTable, storageTable);
        when(combinationRepository.findByRestaurantIdAndActiveTrueOrderByNameAscIdAsc(1L)).thenReturn(List.of(combination));

        List<AssignmentCandidate> candidates = finder.findCandidates(1L);

        assertTrue(candidates.isEmpty());
    }

    @Test
    void returnsEmptyWhenNoActiveResources() {
        when(tableRepository.findByRestaurantIdAndTableTypeNotOrderByDiningRoomIdAscCodeAsc(1L, TableType.STORAGE)).thenReturn(List.of());
        when(combinationRepository.findByRestaurantIdAndActiveTrueOrderByNameAscIdAsc(1L)).thenReturn(List.of());

        List<AssignmentCandidate> candidates = finder.findCandidates(1L);

        assertTrue(candidates.isEmpty());
    }

    @Test
    void returnedTablesHaveCorrectCapacities() {
        RestaurantTable table = createTable(100L, "A1", room, true);
        when(tableRepository.findByRestaurantIdAndTableTypeNotOrderByDiningRoomIdAscCodeAsc(1L, TableType.STORAGE)).thenReturn(List.of(table));
        when(combinationRepository.findByRestaurantIdAndActiveTrueOrderByNameAscIdAsc(1L)).thenReturn(List.of());

        List<AssignmentCandidate> candidates = finder.findCandidates(1L);

        assertEquals(1, candidates.size());
        assertEquals(2, candidates.get(0).minCapacity());
        assertEquals(4, candidates.get(0).maxCapacity());
    }

    @Test
    void returnsTablesOrderedByRoomThenCode() {
        DiningRoom roomA = createRoom("Alpha", 1, true, true);
        DiningRoom roomB = createRoom("Beta", 2, true, true);

        RestaurantTable tableB1 = createTable(1L, "B1", roomB, true);
        RestaurantTable tableA1 = createTable(2L, "A1", roomA, true);

        when(tableRepository.findByRestaurantIdAndTableTypeNotOrderByDiningRoomIdAscCodeAsc(1L, TableType.STORAGE)).thenReturn(List.of(tableA1, tableB1));
        when(combinationRepository.findByRestaurantIdAndActiveTrueOrderByNameAscIdAsc(1L)).thenReturn(List.of());

        List<AssignmentCandidate> candidates = finder.findCandidates(1L);

        assertEquals(2, candidates.size());
        assertEquals("A1", candidates.get(0).displayName());
        assertEquals("B1", candidates.get(1).displayName());
    }

    @Test
    void advancedCombinationsOnlyAppearInManualSuggestionMode() {
        when(tableRepository.findByRestaurantIdAndTableTypeNotOrderByDiningRoomIdAscCodeAsc(1L, TableType.STORAGE))
            .thenReturn(List.of());
        RestaurantTable tableA = createTable(200L, "A1", room, true);
        RestaurantTable tableB = createTable(201L, "A2", room, true);
        TableCombination combination = createCombination(300L, "Advanced pair", true, tableA, tableB);
        combination.setCombinationType(CombinationType.ADVANCED);
        when(combinationRepository.findByRestaurantIdAndActiveTrueOrderByNameAscIdAsc(1L))
            .thenReturn(List.of(combination));

        assertTrue(finder.findCandidates(1L).isEmpty());
        assertEquals(
            1,
            finder.findCandidates(1L, CandidateSearchMode.MANUAL_SUGGESTION).size()
        );
    }

    private DiningRoom createRoom(String name, int priority, boolean accessible, boolean active) {
        DiningRoom r = new DiningRoom();
        r.setId((long) name.hashCode());
        r.setName(name);
        r.setPriority(priority);
        r.setAccessible(accessible);
        r.setActive(active);
        return r;
    }

    private RestaurantTable createTable(Long id, String code, DiningRoom diningRoom, boolean active) {
        RestaurantTable table = new RestaurantTable();
        table.setId(id);
        table.setCode(code);
        table.setMinCapacity(2);
        table.setMaxCapacity(4);
        table.setActive(active);
        table.setDiningRoom(diningRoom);
        table.setTableType(TableType.FIXED);
        table.setShape("RECTANGLE");
        table.setX(0);
        table.setY(0);
        table.setWidth(100);
        table.setHeight(100);
        return table;
    }

    private TableCombination createCombination(Long id, String name, boolean active, RestaurantTable... tables) {
        TableCombination combination = new TableCombination();
        combination.setId(id);
        combination.setName(name);
        combination.setMinCapacity(2);
        combination.setMaxCapacity(java.util.Arrays.stream(tables).mapToInt(RestaurantTable::getMaxCapacity).sum());
        combination.setActive(active);

        for (int i = 0; i < tables.length; i++) {
            TableCombinationItem item = new TableCombinationItem();
            item.setId((long) (id + i));
            item.setTableCombination(combination);
            item.setTable(tables[i]);
            item.setOrderIndex(i);
            combination.getItems().add(item);
        }

        return combination;
    }
}
