package com.restaurantplanner.reservation.domain;

import com.restaurantplanner.common.domain.BaseEntity;
import com.restaurantplanner.diningroom.domain.DiningRoom;
import com.restaurantplanner.table.domain.RestaurantTable;
import com.restaurantplanner.tablecombination.domain.TableCombination;
import com.restaurantplanner.tablecombination.domain.OperationalCostLevel;
import com.restaurantplanner.user.domain.User;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.CascadeType;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import org.hibernate.annotations.ColumnTransformer;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "reservation_assignment")
public class ReservationAssignment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "assignment_type", length = 40)
    private String assignmentType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dining_room_id")
    private DiningRoom diningRoom;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_id")
    private RestaurantTable table;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "table_combination_id")
    private TableCombination tableCombination;

    @Column
    private Double score;

    @JdbcTypeCode(SqlTypes.JSON)
    @ColumnTransformer(write = "?::jsonb")
    @Column(name = "explanation_json", columnDefinition = "jsonb")
    private String explanationJson;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_by")
    private User assignedBy;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(nullable = false)
    private boolean active;

    @Enumerated(EnumType.STRING)
    @Column(name = "operational_cost_level", nullable = false, length = 20)
    private OperationalCostLevel operationalCostLevel = OperationalCostLevel.LOW;

    @Column(name = "setup_time_minutes", nullable = false)
    private Integer setupTimeMinutes = 0;

    @OneToMany(mappedBy = "reservationAssignment", cascade = CascadeType.ALL, orphanRemoval = true)
    private Set<ReservationAssignmentResource> resources = new LinkedHashSet<>();

    public Reservation getReservation() {
        return reservation;
    }

    public void setReservation(Reservation reservation) {
        this.reservation = reservation;
    }

    public String getAssignmentType() {
        return assignmentType;
    }

    public void setAssignmentType(String assignmentType) {
        this.assignmentType = assignmentType;
    }

    public DiningRoom getDiningRoom() {
        return diningRoom;
    }

    public void setDiningRoom(DiningRoom diningRoom) {
        this.diningRoom = diningRoom;
    }

    public RestaurantTable getTable() {
        return table;
    }

    public void setTable(RestaurantTable table) {
        this.table = table;
    }

    public TableCombination getTableCombination() {
        return tableCombination;
    }

    public void setTableCombination(TableCombination tableCombination) {
        this.tableCombination = tableCombination;
    }

    public Double getScore() {
        return score;
    }

    public void setScore(Double score) {
        this.score = score;
    }

    public String getExplanationJson() {
        return explanationJson;
    }

    public void setExplanationJson(String explanationJson) {
        this.explanationJson = explanationJson;
    }

    public User getAssignedBy() {
        return assignedBy;
    }

    public void setAssignedBy(User assignedBy) {
        this.assignedBy = assignedBy;
    }

    public Instant getAssignedAt() {
        return assignedAt;
    }

    public void setAssignedAt(Instant assignedAt) {
        this.assignedAt = assignedAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
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

    public Set<ReservationAssignmentResource> getResources() {
        return resources;
    }
}
