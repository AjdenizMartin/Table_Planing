CREATE TABLE scheduled_notification (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    reservation_id BIGINT REFERENCES reservation(id) ON DELETE CASCADE,
    type VARCHAR(40) NOT NULL,
    recipient_email VARCHAR(255) NOT NULL,
    subject VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    scheduled_at TIMESTAMP NOT NULL,
    sent_at TIMESTAMP,
    error TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_scheduled_notification_scheduled_at ON scheduled_notification(scheduled_at)
    WHERE sent_at IS NULL;
CREATE INDEX idx_scheduled_notification_restaurant_id ON scheduled_notification(restaurant_id);
