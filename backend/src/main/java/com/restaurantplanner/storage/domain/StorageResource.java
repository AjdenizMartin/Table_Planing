package com.restaurantplanner.storage.domain;

import com.restaurantplanner.common.domain.BaseEntity;
import com.restaurantplanner.restaurant.domain.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "storage_resource")
public class StorageResource extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Enumerated(EnumType.STRING)
    @Column(name = "resource_type", nullable = false, length = 40)
    private StorageResourceType resourceType;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "capacity_per_unit", nullable = false)
    private Integer capacityPerUnit;

    @Column(name = "setup_time_minutes", nullable = false)
    private Integer setupTimeMinutes;

    @Column(nullable = false)
    private boolean active;

    @Column(columnDefinition = "TEXT")
    private String notes;

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public void setRestaurant(Restaurant restaurant) {
        this.restaurant = restaurant;
    }

    public StorageResourceType getResourceType() {
        return resourceType;
    }

    public void setResourceType(StorageResourceType resourceType) {
        this.resourceType = resourceType;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getCapacityPerUnit() {
        return capacityPerUnit;
    }

    public void setCapacityPerUnit(Integer capacityPerUnit) {
        this.capacityPerUnit = capacityPerUnit;
    }

    public Integer getSetupTimeMinutes() {
        return setupTimeMinutes;
    }

    public void setSetupTimeMinutes(Integer setupTimeMinutes) {
        this.setupTimeMinutes = setupTimeMinutes;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}
