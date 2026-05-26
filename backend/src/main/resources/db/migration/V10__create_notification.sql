CREATE TABLE notification (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    user_id BIGINT REFERENCES app_user(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT,
    entity_type VARCHAR(80),
    entity_id BIGINT,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_restaurant_id ON notification(restaurant_id);
CREATE INDEX idx_notification_user_id_read ON notification(user_id, is_read);
CREATE INDEX idx_notification_created_at ON notification(created_at DESC);
