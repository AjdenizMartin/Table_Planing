package com.restaurantplanner.tablecombination.domain;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TableCombinationRepository extends JpaRepository<TableCombination, Long> {

    @EntityGraph(attributePaths = {"items", "items.table", "items.table.diningRoom", "resourceRequirements", "resourceRequirements.storageResource", "restaurant"})
    List<TableCombination> findByRestaurantIdAndActiveTrueOrderByNameAscIdAsc(Long restaurantId);

    @EntityGraph(attributePaths = {"items", "items.table", "items.table.diningRoom", "resourceRequirements", "resourceRequirements.storageResource", "restaurant"})
    Optional<TableCombination> findByIdAndRestaurantIdAndActiveTrue(Long id, Long restaurantId);

    @EntityGraph(attributePaths = {"items", "items.table", "items.table.diningRoom", "resourceRequirements", "resourceRequirements.storageResource", "restaurant"})
    Optional<TableCombination> findByIdAndRestaurantId(Long id, Long restaurantId);
}
