CREATE TABLE notification_log (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    reservation_id BIGINT REFERENCES reservation(id) ON DELETE SET NULL,
    customer_id BIGINT REFERENCES customer(id) ON DELETE SET NULL,
    channel VARCHAR(32) NOT NULL,
    template_code VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    provider_message_id VARCHAR(255),
    error_message TEXT,
    sent_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_notification_log_restaurant_created_at
    ON notification_log(restaurant_id, created_at DESC);
CREATE INDEX idx_notification_log_reservation_id
    ON notification_log(reservation_id);
CREATE INDEX idx_notification_log_customer_id
    ON notification_log(customer_id);
