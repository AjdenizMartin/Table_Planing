ALTER TABLE storage_resource
    ADD COLUMN capacity_per_unit INTEGER NOT NULL DEFAULT 0,
    ADD COLUMN setup_time_minutes INTEGER NOT NULL DEFAULT 0;

ALTER TABLE storage_resource
    ADD CONSTRAINT chk_storage_resource_capacity_per_unit
        CHECK (capacity_per_unit >= 0),
    ADD CONSTRAINT chk_storage_resource_setup_time_minutes
        CHECK (setup_time_minutes >= 0);

ALTER TABLE storage_resource
    DROP CONSTRAINT chk_storage_resource_type;

ALTER TABLE storage_resource
    ADD CONSTRAINT chk_storage_resource_type CHECK (
        resource_type IN (
            'EXTRA_TABLE',
            'EXTRA_CHAIR',
            'HIGH_CHAIR',
            'FOLDING_TABLE',
            'TABLE_EXTENSION',
            'BENCH',
            'STORAGE_TABLE',
            'OTHER'
        )
    );
