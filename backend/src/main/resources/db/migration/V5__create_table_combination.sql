CREATE TABLE table_combination (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    min_capacity INTEGER NOT NULL CHECK (min_capacity > 0),
    max_capacity INTEGER NOT NULL CHECK (max_capacity > 0),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_table_combination_capacity_range CHECK (min_capacity <= max_capacity)
);

CREATE TABLE table_combination_item (
    id BIGSERIAL PRIMARY KEY,
    table_combination_id BIGINT NOT NULL REFERENCES table_combination(id) ON DELETE CASCADE,
    table_id BIGINT NOT NULL REFERENCES restaurant_table(id) ON DELETE RESTRICT,
    order_index INTEGER NOT NULL CHECK (order_index >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_table_combination_item_combination_table UNIQUE (table_combination_id, table_id),
    CONSTRAINT uq_table_combination_item_combination_order UNIQUE (table_combination_id, order_index)
);

CREATE INDEX idx_table_combination_restaurant_id_active ON table_combination(restaurant_id, active);
CREATE INDEX idx_table_combination_item_table_combination_id ON table_combination_item(table_combination_id);
