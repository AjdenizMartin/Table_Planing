package com.restaurantplanner.tablecombination.domain;

import com.restaurantplanner.common.domain.BaseEntity;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.storage.domain.StorageResource;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Column;

@Entity
@Table(name = "table_combination_resource_requirement")
public class TableCombinationResourceRequirement extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_combination_id", nullable = false)
    private TableCombination tableCombination;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "storage_resource_id", nullable = false)
    private StorageResource storageResource;

    @Column(nullable = false)
    private Integer quantity;

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public void setRestaurant(Restaurant restaurant) {
        this.restaurant = restaurant;
    }

    public TableCombination getTableCombination() {
        return tableCombination;
    }

    public void setTableCombination(TableCombination tableCombination) {
        this.tableCombination = tableCombination;
    }

    public StorageResource getStorageResource() {
        return storageResource;
    }

    public void setStorageResource(StorageResource storageResource) {
        this.storageResource = storageResource;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }
}
