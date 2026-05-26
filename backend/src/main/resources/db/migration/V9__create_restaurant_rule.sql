CREATE TABLE restaurant_rule (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    rule_type VARCHAR(80) NOT NULL,
    name VARCHAR(160) NOT NULL,
    enabled BOOLEAN NOT NULL,
    priority INTEGER NOT NULL CHECK (priority >= 0),
    config_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_restaurant_rule_restaurant_id_type ON restaurant_rule(restaurant_id, rule_type);
CREATE INDEX idx_restaurant_rule_restaurant_id_enabled ON restaurant_rule(restaurant_id, enabled);
