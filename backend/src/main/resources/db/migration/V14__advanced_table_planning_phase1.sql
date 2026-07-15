ALTER TABLE restaurant_table
    ADD COLUMN table_type VARCHAR(40) NOT NULL DEFAULT 'FIXED';

ALTER TABLE restaurant_table
    ALTER COLUMN dining_room_id DROP NOT NULL;

ALTER TABLE restaurant_table
    ADD CONSTRAINT chk_restaurant_table_table_type
        CHECK (table_type IN ('FIXED', 'MOVABLE', 'STORAGE', 'TEMPORARY'));

ALTER TABLE restaurant_table
    ADD CONSTRAINT chk_restaurant_table_storage_room
        CHECK (table_type <> 'STORAGE' OR dining_room_id IS NULL);

CREATE TABLE storage_resource (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    resource_type VARCHAR(40) NOT NULL,
    name VARCHAR(160) NOT NULL,
    quantity INTEGER NOT NULL CHECK (quantity >= 0),
    active BOOLEAN NOT NULL,
    notes TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_storage_resource_type CHECK (resource_type IN ('EXTRA_CHAIR', 'STORAGE_TABLE', 'OTHER'))
);

CREATE INDEX idx_storage_resource_restaurant_type ON storage_resource(restaurant_id, resource_type);
CREATE INDEX idx_storage_resource_restaurant_active ON storage_resource(restaurant_id, active);
