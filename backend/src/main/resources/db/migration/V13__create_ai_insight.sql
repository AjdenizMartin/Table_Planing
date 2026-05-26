CREATE TABLE ai_insight (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    date DATE NOT NULL,
    type VARCHAR(64) NOT NULL,
    severity VARCHAR(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    description TEXT NOT NULL,
    entity_type VARCHAR(80),
    entity_id BIGINT,
    metadata_json JSONB,
    dismissed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_ai_insight_restaurant_date ON ai_insight(restaurant_id, date);
CREATE INDEX idx_ai_insight_restaurant_date_dismissed ON ai_insight(restaurant_id, date, dismissed);
