CREATE TABLE reservation (
    id BIGSERIAL PRIMARY KEY,
    restaurant_id BIGINT NOT NULL REFERENCES restaurant(id) ON DELETE CASCADE,
    customer_id BIGINT NOT NULL REFERENCES customer(id) ON DELETE RESTRICT,
    channel VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    party_size INTEGER NOT NULL CHECK (party_size > 0),
    reservation_date DATE NOT NULL,
    start_time TIME NOT NULL,
    end_time TIME NOT NULL,
    estimated_duration_min INTEGER NOT NULL CHECK (estimated_duration_min > 0),
    cleaning_buffer_min INTEGER NOT NULL CHECK (cleaning_buffer_min >= 0),
    confirmed_at TIMESTAMP,
    cancelled_at TIMESTAMP,
    special_requests TEXT,
    accessibility_required BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE reservation_assignment (
    id BIGSERIAL PRIMARY KEY,
    reservation_id BIGINT NOT NULL REFERENCES reservation(id) ON DELETE CASCADE,
    assignment_type VARCHAR(40),
    dining_room_id BIGINT REFERENCES dining_room(id) ON DELETE RESTRICT,
    table_id BIGINT REFERENCES restaurant_table(id) ON DELETE RESTRICT,
    table_combination_id BIGINT REFERENCES table_combination(id) ON DELETE RESTRICT,
    score DOUBLE PRECISION,
    explanation_json JSONB,
    assigned_by BIGINT REFERENCES app_user(id) ON DELETE SET NULL,
    assigned_at TIMESTAMP,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_reservation_restaurant_id_date_time ON reservation(restaurant_id, reservation_date, start_time);
CREATE INDEX idx_reservation_restaurant_id_status ON reservation(restaurant_id, status);
CREATE INDEX idx_reservation_assignment_reservation_id ON reservation_assignment(reservation_id);
