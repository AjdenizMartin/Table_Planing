CREATE TABLE customer (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    first_name VARCHAR(120),
    last_name VARCHAR(120),
    phone VARCHAR(40),
    email VARCHAR(160),
    notes TEXT,
    tags_json JSONB,
    mobility_needs VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_customer_restaurant_id ON customer(restaurant_id);
CREATE INDEX idx_customer_restaurant_id_phone ON customer(restaurant_id, phone);
CREATE INDEX idx_customer_restaurant_id_last_name_first_name ON customer(restaurant_id, last_name, first_name);
