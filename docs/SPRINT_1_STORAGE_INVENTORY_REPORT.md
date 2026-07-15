# Sprint 1 Storage Inventory Report

Date: 2026-07-15

Status: `DONE`

## Scope Delivered

Sprint 1 turns the existing storage resource prototype into a usable manager configuration flow without connecting storage inventory to reservations, planning or automatic assignment.

Implemented:

- restaurant-scoped storage resource listing with optional `resourceType` and `active` filters
- create and partial update of storage resources
- activation, deactivation and reactivation through the existing `PATCH` endpoint
- compatibility `DELETE` behavior as soft deactivation rather than physical deletion
- non-negative validation for quantity, capacity per unit and setup time
- required name and resource type on creation
- expanded resource types for extra tables, chairs, high chairs, folding tables, extensions, benches and other resources
- retention of `STORAGE_TABLE` for V14 data compatibility
- frontend summary, filters, editable table and active-state actions
- explicit product messaging that inventory does not affect the algorithm yet

Not implemented by design:

- setup options or setup plans
- setup tasks or manager approval
- daily floor plans
- automatic use of storage inventory
- advanced assignment changes
- automatic reservation-time changes

## API

Available endpoints:

- `POST /api/restaurants/{restaurantId}/storage-resources`
- `GET /api/restaurants/{restaurantId}/storage-resources`
- `GET /api/restaurants/{restaurantId}/storage-resources?resourceType=EXTRA_CHAIR&active=true`
- `GET /api/restaurants/{restaurantId}/storage-resources/{resourceId}`
- `PATCH /api/restaurants/{restaurantId}/storage-resources/{resourceId}`
- `DELETE /api/restaurants/{restaurantId}/storage-resources/{resourceId}` (soft deactivate)
- `POST /api/restaurants/{restaurantId}/storage-resources/{resourceId}/availability-check`

`PATCH` accepts any subset of:

- `resourceType`
- `name`
- `quantity`
- `capacityPerUnit`
- `setupTimeMinutes`
- `notes`
- `active`

Resource lookups and modifications use both `resourceId` and `restaurantId`. A resource from another restaurant is returned as `NOT_FOUND` and cannot be modified.

## Data Model

Migration `V15__storage_inventory_sprint1.sql` extends `storage_resource` with:

- `capacity_per_unit INTEGER NOT NULL DEFAULT 0`
- `setup_time_minutes INTEGER NOT NULL DEFAULT 0`
- database checks requiring both values to be non-negative
- an expanded resource type check constraint

Supported types:

- `EXTRA_TABLE`
- `EXTRA_CHAIR`
- `HIGH_CHAIR`
- `FOLDING_TABLE`
- `TABLE_EXTENSION`
- `BENCH`
- `STORAGE_TABLE` (legacy-compatible)
- `OTHER`

The default value of `0` preserves existing V14 rows and allows older API clients to omit the two new fields when creating a resource.

## Frontend Behavior

Storage inventory remains integrated into the existing table configuration screen. The manager can:

- see active extra-chair and storage-table quantities
- see active and inactive resource counts
- filter by resource type
- filter by active, inactive or all resources
- create a resource
- edit every supported field
- deactivate and reactivate a resource
- see legacy restaurant tables marked as `STORAGE`

The inventory table shows:

```text
Name | Type | Quantity | Capacity/unit | Setup time | Active | Actions
```

The required messages are visible:

```text
Storage inventory
Configure extra resources available in storage.
These resources are configured but are not used automatically by the algorithm yet.
Advanced setup suggestions will be implemented in a later sprint.
```

## Tests Added

`StorageResourceIntegrationTest` now covers:

- creation of an active resource with quantity, capacity and setup time
- negative quantity rejection
- negative capacity-per-unit rejection
- negative setup-time rejection
- required name and resource type
- filtering by resource type
- filtering by active state
- combined filters
- updating type, name, quantity, capacity, setup time, notes and active state
- deactivation and reactivation
- persistence after deactivation
- read and update isolation between restaurants

Existing `CandidateFinderTest` and planning integration coverage continue to protect the rule that storage inventory and `STORAGE` tables do not become normal candidates.

## Verification

Commands executed:

```bash
cd backend && mvn test
cd backend && mvn test -Pintegration-tests
cd backend && mvn -Pintegration-tests -Dtest=StorageResourceIntegrationTest test
cd frontend && npm run build
docker compose build
docker compose up -d
docker compose ps
curl http://localhost:8080/actuator/health
curl http://localhost:5173
```

Results:

- backend quick suite: `PASS` - 32 tests, 0 failures, 0 errors
- StorageResource integration suite: `PASS` - 7 tests, 0 failures, 0 errors
- complete backend suite: `PASS` - 116 tests, 0 failures, 0 errors
- Flyway empty PostgreSQL migration: `PASS` - 15 migrations applied through V15
- frontend TypeScript and Vite production build: `PASS`
- Docker image build: `PASS`
- Docker Compose startup: `PASS`
- PostgreSQL container: `healthy`
- backend health: `UP`
- frontend HTTP response: `PASS`

The running local application is available at `http://localhost:5173`; backend health is available at `http://localhost:8080/actuator/health`.

## Limitations And Technical Debt

- Storage resources remain descriptive configuration and are not used by `CandidateFinder`, planning or reservation assignment.
- `availability-check` remains disconnected from reservation setup planning.
- Automated screenshot QA could not run: the bundled Playwright Chromium executable was absent and local Chrome aborted under the sandbox process restrictions. Build and HTTP checks passed, but a browser screenshot was not produced.
- The frontend Docker build reported two dependency audit findings (one moderate and one high). They were not force-upgraded inside this feature sprint because that could introduce unrelated breaking changes; dependency remediation should be handled as a separate reviewed change.
- The Compose frontend uses the existing Vite development-server container and is not a production deployment architecture.

## Sprint 2 Recommendation

Sprint 2 should focus on Combinaciones Avanzadas while preserving current reservation times and deterministic explainability.

Before implementation, define:

- the difference between standard and advanced combinations
- which storage or extra-chair inputs a combination may reference
- capacity and operational-cost rules
- conflict and availability semantics
- audit and explanation requirements

Keep setup plans, setup tasks, manager approval and automatic application outside Sprint 2 unless a separate domain decision explicitly brings them into scope.
