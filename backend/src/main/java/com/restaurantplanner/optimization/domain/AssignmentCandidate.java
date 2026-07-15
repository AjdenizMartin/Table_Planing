package com.restaurantplanner.optimization.domain;

import com.restaurantplanner.diningroom.domain.DiningRoom;
import com.restaurantplanner.table.domain.RestaurantTable;
import com.restaurantplanner.tablecombination.domain.TableCombination;
import com.restaurantplanner.tablecombination.domain.CombinationType;
import com.restaurantplanner.tablecombination.domain.OperationalCostLevel;
import java.util.List;

public record AssignmentCandidate(
    AssignmentCandidateType type,
    RestaurantTable table,
    TableCombination tableCombination,
    List<RestaurantTable> tables,
    int minCapacity,
    int maxCapacity,
    String displayName,
    CombinationType combinationType,
    OperationalCostLevel operationalCostLevel,
    int setupTimeMinutes,
    List<CandidateResourceRequirement> resourceRequirements
) {

    public AssignmentCandidate(
        AssignmentCandidateType type,
        RestaurantTable table,
        TableCombination tableCombination,
        List<RestaurantTable> tables,
        int minCapacity,
        int maxCapacity,
        String displayName
    ) {
        this(
            type,
            table,
            tableCombination,
            tables,
            minCapacity,
            maxCapacity,
            displayName,
            CombinationType.STANDARD,
            OperationalCostLevel.LOW,
            0,
            List.of()
        );
    }

    public boolean advanced() {
        return combinationType == CombinationType.ADVANCED;
    }

    public List<Long> tableIds() {
        return tables.stream().map(RestaurantTable::getId).toList();
    }

    public List<DiningRoom> diningRooms() {
        return tables.stream().map(RestaurantTable::getDiningRoom).distinct().toList();
    }

    public int tableCount() {
        return tables.size();
    }

    public boolean allDiningRoomsAccessible() {
        return diningRooms().stream().allMatch(DiningRoom::isAccessible);
    }

    public boolean allDiningRoomsActive() {
        return diningRooms().stream().allMatch(DiningRoom::isActive);
    }

    public int primaryRoomPriority() {
        return diningRooms().stream().mapToInt(DiningRoom::getPriority).min().orElse(Integer.MAX_VALUE);
    }

    public Long singleDiningRoomIdOrNull() {
        List<DiningRoom> rooms = diningRooms();
        return rooms.size() == 1 ? rooms.get(0).getId() : null;
    }
}
