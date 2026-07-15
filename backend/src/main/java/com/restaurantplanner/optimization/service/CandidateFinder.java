package com.restaurantplanner.optimization.service;

import com.restaurantplanner.optimization.domain.AssignmentCandidate;
import com.restaurantplanner.optimization.domain.AssignmentCandidateType;
import com.restaurantplanner.optimization.domain.CandidateResourceRequirement;
import com.restaurantplanner.optimization.domain.CandidateSearchMode;
import com.restaurantplanner.table.domain.RestaurantTable;
import com.restaurantplanner.table.domain.RestaurantTableRepository;
import com.restaurantplanner.table.domain.TableType;
import com.restaurantplanner.tablecombination.domain.TableCombination;
import com.restaurantplanner.tablecombination.domain.TableCombinationRepository;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CandidateFinder {

    private final RestaurantTableRepository restaurantTableRepository;
    private final TableCombinationRepository tableCombinationRepository;

    public CandidateFinder(
        RestaurantTableRepository restaurantTableRepository,
        TableCombinationRepository tableCombinationRepository
    ) {
        this.restaurantTableRepository = restaurantTableRepository;
        this.tableCombinationRepository = tableCombinationRepository;
    }

    @Transactional(readOnly = true)
    public List<AssignmentCandidate> findCandidates(Long restaurantId) {
        return findCandidates(restaurantId, CandidateSearchMode.AUTOMATIC);
    }

    @Transactional(readOnly = true)
    public List<AssignmentCandidate> findCandidates(Long restaurantId, CandidateSearchMode mode) {
        List<AssignmentCandidate> candidates = new ArrayList<>();

        for (RestaurantTable table : restaurantTableRepository.findByRestaurantIdAndTableTypeNotOrderByDiningRoomIdAscCodeAsc(restaurantId, TableType.STORAGE)) {
            candidates.add(new AssignmentCandidate(
                AssignmentCandidateType.TABLE,
                table,
                null,
                List.of(table),
                table.getMinCapacity(),
                table.getMaxCapacity(),
                table.getCode()
            ));
        }

        for (TableCombination combination : tableCombinationRepository.findByRestaurantIdAndActiveTrueOrderByNameAscIdAsc(restaurantId)) {
            if (mode == CandidateSearchMode.AUTOMATIC && combination.getCombinationType().name().equals("ADVANCED")) {
                continue;
            }
            List<RestaurantTable> tables = combination.getItems().stream().map(item -> item.getTable()).toList();
            if (tables.stream().anyMatch(table -> table.getTableType() == TableType.STORAGE)) {
                continue;
            }
            candidates.add(new AssignmentCandidate(
                AssignmentCandidateType.TABLE_COMBINATION,
                null,
                combination,
                tables,
                combination.getMinCapacity(),
                combination.getMaxCapacity(),
                combination.getName(),
                combination.getCombinationType(),
                combination.getOperationalCostLevel(),
                combination.getSetupTimeMinutes(),
                combination.getResourceRequirements().stream()
                    .map(requirement -> new CandidateResourceRequirement(
                        requirement.getStorageResource(),
                        requirement.getQuantity()
                    ))
                    .toList()
            ));
        }

        return candidates;
    }
}
