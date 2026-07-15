package com.restaurantplanner.reservation.domain;

import com.restaurantplanner.common.domain.BaseEntity;
import com.restaurantplanner.restaurant.domain.Restaurant;
import com.restaurantplanner.storage.domain.StorageResource;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservation_assignment_resource")
public class ReservationAssignmentResource extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_assignment_id", nullable = false)
    private ReservationAssignment reservationAssignment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "storage_resource_id", nullable = false)
    private StorageResource storageResource;

    @Column(name = "resource_name_snapshot", nullable = false, length = 160)
    private String resourceNameSnapshot;

    @Column(name = "resource_type_snapshot", nullable = false, length = 40)
    private String resourceTypeSnapshot;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "capacity_per_unit_snapshot", nullable = false)
    private Integer capacityPerUnitSnapshot;

    @Column(name = "setup_time_minutes_snapshot", nullable = false)
    private Integer setupTimeMinutesSnapshot;

    public Restaurant getRestaurant() {
        return restaurant;
    }

    public void setRestaurant(Restaurant restaurant) {
        this.restaurant = restaurant;
    }

    public ReservationAssignment getReservationAssignment() {
        return reservationAssignment;
    }

    public void setReservationAssignment(ReservationAssignment reservationAssignment) {
        this.reservationAssignment = reservationAssignment;
    }

    public StorageResource getStorageResource() {
        return storageResource;
    }

    public void setStorageResource(StorageResource storageResource) {
        this.storageResource = storageResource;
    }

    public String getResourceNameSnapshot() {
        return resourceNameSnapshot;
    }

    public void setResourceNameSnapshot(String resourceNameSnapshot) {
        this.resourceNameSnapshot = resourceNameSnapshot;
    }

    public String getResourceTypeSnapshot() {
        return resourceTypeSnapshot;
    }

    public void setResourceTypeSnapshot(String resourceTypeSnapshot) {
        this.resourceTypeSnapshot = resourceTypeSnapshot;
    }

    public Integer getQuantity() {
        return quantity;
    }

    public void setQuantity(Integer quantity) {
        this.quantity = quantity;
    }

    public Integer getCapacityPerUnitSnapshot() {
        return capacityPerUnitSnapshot;
    }

    public void setCapacityPerUnitSnapshot(Integer capacityPerUnitSnapshot) {
        this.capacityPerUnitSnapshot = capacityPerUnitSnapshot;
    }

    public Integer getSetupTimeMinutesSnapshot() {
        return setupTimeMinutesSnapshot;
    }

    public void setSetupTimeMinutesSnapshot(Integer setupTimeMinutesSnapshot) {
        this.setupTimeMinutesSnapshot = setupTimeMinutesSnapshot;
    }
}
