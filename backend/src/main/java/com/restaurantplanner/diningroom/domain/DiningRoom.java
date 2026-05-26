package com.restaurantplanner.diningroom.domain;

import com.restaurantplanner.common.domain.BaseEntity;
import com.restaurantplanner.restaurant.domain.Restaurant;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "dining_room")
public class DiningRoom extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "restaurant_id", nullable = false)
    private Restaurant restaurant;

    @Column(nullable = false, length = 160)
    private String name;

    @Column(nullable = false)
    private Integer priority;

    @Column(nullable = false)
    private boolean accessible;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "layout_width", nullable = false)
    private Integer layoutWidth;

    @Column(name = "layout_height", nullable = false)
    private Integer layoutHeight;

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

    public Integer getPriority() {
        return priority;
    }

    public void setPriority(Integer priority) {
        this.priority = priority;
    }

    public boolean isAccessible() {
        return accessible;
    }

    public void setAccessible(boolean accessible) {
        this.accessible = accessible;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Integer getLayoutWidth() {
        return layoutWidth;
    }

    public void setLayoutWidth(Integer layoutWidth) {
        this.layoutWidth = layoutWidth;
    }

    public Integer getLayoutHeight() {
        return layoutHeight;
    }

    public void setLayoutHeight(Integer layoutHeight) {
        this.layoutHeight = layoutHeight;
    }
}

