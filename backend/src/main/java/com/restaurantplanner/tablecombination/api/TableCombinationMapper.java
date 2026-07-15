package com.restaurantplanner.tablecombination.api;

import com.restaurantplanner.tablecombination.domain.TableCombination;
import com.restaurantplanner.tablecombination.domain.TableCombinationItem;
import org.springframework.stereotype.Component;

@Component
public class TableCombinationMapper {

    public TableCombinationResponse toResponse(TableCombination tableCombination) {
        return new TableCombinationResponse(
            tableCombination.getId(),
            tableCombination.getRestaurant().getId(),
            tableCombination.getName(),
            tableCombination.getMinCapacity(),
            tableCombination.getMaxCapacity(),
            tableCombination.isActive(),
            tableCombination.getCombinationType().name(),
            tableCombination.getOperationalCostLevel().name(),
            tableCombination.getSetupTimeMinutes(),
            tableCombination.getItems().stream().map(this::toItemResponse).toList(),
            tableCombination.getResourceRequirements().stream().map(requirement ->
                new TableCombinationResourceRequirementResponse(
                    requirement.getId(),
                    requirement.getStorageResource().getId(),
                    requirement.getStorageResource().getResourceType().name(),
                    requirement.getStorageResource().getName(),
                    requirement.getQuantity(),
                    requirement.getStorageResource().getCapacityPerUnit(),
                    requirement.getQuantity() * requirement.getStorageResource().getCapacityPerUnit(),
                    requirement.getStorageResource().getSetupTimeMinutes()
                )
            ).toList(),
            tableCombination.getCreatedAt(),
            tableCombination.getUpdatedAt()
        );
    }

    private TableCombinationItemResponse toItemResponse(TableCombinationItem item) {
        return new TableCombinationItemResponse(
            item.getId(),
            item.getTable().getId(),
            item.getTable().getDiningRoom() == null ? null : item.getTable().getDiningRoom().getId(),
            item.getTable().getCode(),
            item.getTable().getLabel(),
            item.getOrderIndex()
        );
    }
}
