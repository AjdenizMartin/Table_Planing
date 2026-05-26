CREATE TABLE dining_room (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    name VARCHAR(160) NOT NULL,
    priority INTEGER NOT NULL CHECK (priority > 0),
    accessible BOOLEAN NOT NULL,
    active BOOLEAN NOT NULL,
    layout_width INTEGER NOT NULL CHECK (layout_width >= 100 AND layout_width <= 10000),
    layout_height INTEGER NOT NULL CHECK (layout_height >= 100 AND layout_height <= 10000),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_dining_room_restaurant_name UNIQUE (restaurant_id, name)
);

CREATE INDEX idx_dining_room_restaurant_id_priority ON dining_room(restaurant_id, priority);
