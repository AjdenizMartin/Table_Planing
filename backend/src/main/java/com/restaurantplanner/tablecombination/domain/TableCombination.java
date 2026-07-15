package com.restaurantplanner.tablecombination.domain;

import com.restaurantplanner.common.domain.BaseEntity;
import com.restaurantplanner.restaurant.domain.Restaurant;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;

@Entity
@Table(name = "table_combination")
public class TableCombination extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(name = "min_capacity", nullable = false)
    private Integer minCapacity;

    @Column(name = "max_capacity", nullable = false)
    private Integer maxCapacity;

    @Column(nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "combination_type", nullable = false, length = 20)
    private CombinationType combinationType = CombinationType.STANDARD;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_cost_level", nullable = false, length = 20)
    private OperationalCostLevel operationalCostLevel = OperationalCostLevel.LOW;

    @Column(name = "setup_time_minutes", nullable = false)
    private Integer setupTimeMinutes = 0;

    @OneToMany(mappedBy = "tableCombination", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("orderIndex ASC")
    private List<TableCombinationItem> items = new ArrayList<>();

    @OneToMany(mappedBy = "tableCombination", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id ASC")
    private Set<TableCombinationResourceRequirement> resourceRequirements = new LinkedHashSet<>();

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public void setRestaurant(Restaurant restaurant) {
        this.restaurant = restaurant;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getMinCapacity() {
        return minCapacity;
    }

    public void setMinCapacity(Integer minCapacity) {
        this.minCapacity = minCapacity;
    }

    public Integer getMaxCapacity() {
        return maxCapacity;
    }

    public void setMaxCapacity(Integer maxCapacity) {
        this.maxCapacity = maxCapacity;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public CombinationType getCombinationType() {
        return combinationType;
    }

    public void setCombinationType(CombinationType combinationType) {
        this.combinationType = combinationType;
    }

    public OperationalCostLevel getOperationalCostLevel() {
        return operationalCostLevel;
    }

    public void setOperationalCostLevel(OperationalCostLevel operationalCostLevel) {
        this.operationalCostLevel = operationalCostLevel;
    }

    public Integer getSetupTimeMinutes() {
        return setupTimeMinutes;
    }

    public void setSetupTimeMinutes(Integer setupTimeMinutes) {
        this.setupTimeMinutes = setupTimeMinutes;
    }

    public List<TableCombinationItem> getItems() {
        return items;
    }

    public Set<TableCombinationResourceRequirement> getResourceRequirements() {
        return resourceRequirements;
    }
}
