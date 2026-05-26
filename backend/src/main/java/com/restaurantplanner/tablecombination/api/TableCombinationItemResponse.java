package com.restaurantplanner.tablecombination.api;

public record TableCombinationItemResponse(
    Long id,
    Long tableId,
    Long diningRoomId,
    String tableCode,
    String tableLabel,
    Integer orderIndex
) {
}
