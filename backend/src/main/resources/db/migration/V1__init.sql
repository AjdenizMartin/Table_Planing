CREATE TABLE IF NOT EXISTS app_metadata (
    id BIGSERIAL PRIMARY KEY,
    application_name VARCHAR(120) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO app_metadata (application_name)
SELECT 'restaurant-table-planning-backend'
WHERE NOT EXISTS (
    SELECT 1
    FROM app_metadata
    WHERE application_name = 'restaurant-table-planning-backend'
);

