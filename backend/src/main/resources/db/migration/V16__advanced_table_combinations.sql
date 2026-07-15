ALTER TABLE table_combination
    ADD COLUMN combination_type VARCHAR(20) NOT NULL DEFAULT 'STANDARD',
    ADD COLUMN operational_cost_level VARCHAR(20) NOT NULL DEFAULT 'LOW',
    ADD COLUMN setup_time_minutes INTEGER NOT NULL DEFAULT 0;

ALTER TABLE table_combination
    ADD CONSTRAINT chk_table_combination_type
        CHECK (combination_type IN ('STANDARD', 'ADVANCED')),
    ADD CONSTRAINT chk_table_combination_operational_cost
        CHECK (operational_cost_level IN ('LOW', 'MEDIUM', 'HIGH')),
    ADD CONSTRAINT chk_table_combination_setup_time
        CHECK (setup_time_minutes >= 0);

CREATE TABLE table_combination_resource_requirement (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    table_combination_id BIGINT NOT NULL REFERENCES table_combination(id) ON DELETE CASCADE,
    storage_resource_id BIGINT NOT NULL REFERENCES storage_resource(id) ON DELETE RESTRICT,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_combination_resource_requirement UNIQUE (table_combination_id, storage_resource_id)
);

CREATE INDEX idx_combination_resource_restaurant
    ON table_combination_resource_requirement(restaurant_id, table_combination_id);
CREATE INDEX idx_combination_resource_storage
    ON table_combination_resource_requirement(storage_resource_id);
