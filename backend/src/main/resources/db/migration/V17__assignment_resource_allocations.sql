ALTER TABLE reservation_assignment
    ADD COLUMN operational_cost_level VARCHAR(20) NOT NULL DEFAULT 'LOW',
    ADD COLUMN setup_time_minutes INTEGER NOT NULL DEFAULT 0;

ALTER TABLE reservation_assignment
    ADD CONSTRAINT chk_assignment_operational_cost
        CHECK (operational_cost_level IN ('LOW', 'MEDIUM', 'HIGH')),
    ADD CONSTRAINT chk_assignment_setup_time
        CHECK (setup_time_minutes >= 0);

CREATE TABLE reservation_assignment_resource (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    reservation_assignment_id BIGINT NOT NULL REFERENCES reservation_assignment(id) ON DELETE CASCADE,
    storage_resource_id BIGINT NOT NULL REFERENCES storage_resource(id) ON DELETE RESTRICT,
    resource_name_snapshot VARCHAR(160) NOT NULL,
    resource_type_snapshot VARCHAR(40) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity > 0),
    capacity_per_unit_snapshot INTEGER NOT NULL CHECK (capacity_per_unit_snapshot >= 0),
    setup_time_minutes_snapshot INTEGER NOT NULL CHECK (setup_time_minutes_snapshot >= 0),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_assignment_resource UNIQUE (reservation_assignment_id, storage_resource_id)
);

CREATE INDEX idx_assignment_resource_restaurant
    ON reservation_assignment_resource(restaurant_id, reservation_assignment_id);
CREATE INDEX idx_assignment_resource_storage
    ON reservation_assignment_resource(storage_resource_id);
