package com.restaurantplanner.tablecombination.domain;

import com.restaurantplanner.common.domain.BaseEntity;
import com.restaurantplanner.table.domain.RestaurantTable;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "table_combination_item")
public class TableCombinationItem extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_combination_id", nullable = false)
    private TableCombination tableCombination;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "table_id", nullable = false)
    private RestaurantTable table;

    @Column(name = "order_index", nullable = false)
    private Integer orderIndex;

    public TableCombination getTableCombination() {
        return tableCombination;
    }

    public void setTableCombination(TableCombination tableCombination) {
        this.tableCombination = tableCombination;
    }

    public RestaurantTable getTable() {
        return table;
    }

    public void setTable(RestaurantTable table) {
        this.table = table;
    }

    public Integer getOrderIndex() {
        return orderIndex;
    }

    public void setOrderIndex(Integer orderIndex) {
        this.orderIndex = orderIndex;
    }
}
