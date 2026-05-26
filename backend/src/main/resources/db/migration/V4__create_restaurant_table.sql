CREATE TABLE restaurant_table (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    dining_room_id BIGINT NOT NULL REFERENCES dining_room(id) ON DELETE CASCADE,
    code VARCHAR(80) NOT NULL,
    label VARCHAR(160),
    min_capacity INTEGER NOT NULL CHECK (min_capacity > 0),
    max_capacity INTEGER NOT NULL CHECK (max_capacity > 0),
    shape VARCHAR(40) NOT NULL,
    x INTEGER NOT NULL CHECK (x >= 0 AND x <= 10000),
    y INTEGER NOT NULL CHECK (y >= 0 AND y <= 10000),
    width INTEGER NOT NULL CHECK (width >= 20 AND width <= 5000),
    height INTEGER NOT NULL CHECK (height >= 20 AND height <= 5000),
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_restaurant_table_restaurant_code UNIQUE (restaurant_id, code),
    CONSTRAINT chk_restaurant_table_capacity_range CHECK (min_capacity <= max_capacity)
);

CREATE INDEX idx_restaurant_table_restaurant_id_dining_room_id ON restaurant_table(restaurant_id, dining_room_id);
