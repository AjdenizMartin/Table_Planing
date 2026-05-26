CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    entity_type VARCHAR(80) NOT NULL,
    entity_id BIGINT,
    action VARCHAR(80) NOT NULL,
    user_id BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    metadata_json JSONB,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_audit_log_restaurant_id_created_at ON audit_log(restaurant_id, created_at DESC);
CREATE INDEX idx_audit_log_entity_type_entity_id ON audit_log(entity_type, entity_id);
