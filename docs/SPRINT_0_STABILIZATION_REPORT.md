# Sprint 0 Stabilization Report

Date: 2026-07-14

Final note (2026-07-15): the Sprint 1 gaps recorded in this point-in-time report were addressed in `docs/SPRINT_1_STORAGE_INVENTORY_REPORT.md`. Docker, Testcontainers and Flyway V1-V15 were subsequently verified successfully during Sprint 1, closing the environment gap for this workstation.

## Scope

Sprint 0 reviewed and stabilized the current base before expanding Sprint 1 storage inventory work. The goal was evidence, not roadmap inflation: verify code, migrations, tests, frontend behavior and documentation against the actual implementation.

## General Status

Status: `APPROVED_WITH_DOCKER_ENVIRONMENT_GAP`

The project has a functional Spring Boot backend and React frontend with the first advanced table-planning primitives already present. The quick backend suite and frontend production build pass. Docker-dependent verification could not run in this environment because Docker is not available through the local socket.

## Backend Status

Implemented:

- Flyway migration `V14__advanced_table_planning_phase1.sql` exists.
- `restaurant_table.table_type` exists with values `FIXED`, `MOVABLE`, `STORAGE`, `TEMPORARY`.
- `RestaurantTable` has `tableType`.
- `TableType` enum exists.
- `storage_resource` table exists.
- `StorageResource` entity, repository, service, controller, mapper and DTOs exist.
- Storage resources are restaurant-scoped through `restaurant_id`.
- Storage resource create/update/delete operations record audit events.
- Standard table combinations reject `STORAGE` tables at service level.
- Candidate finder excludes direct `STORAGE` tables.
- Candidate finder now also ignores combinations containing `STORAGE` tables defensively.
- Planning snapshot excludes `STORAGE` tables from dining room table lists.

Partial:

- `StorageResource` API has CRUD and availability check, but the list endpoint does not yet expose `resourceType` or `active` query filters.
- Availability check validates requested quantity against configured quantity, but it is not connected to reservation assignment or setup planning.
- `TEMPORARY` is present as a table type but has no dedicated operational lifecycle.

Not implemented:

- Automatic use of extra chairs or storage tables in assignment.
- Setup options.
- Manager approval workflow.
- Setup tasks.
- Daily floor plan.
- Advanced dynamic combinations.

## Frontend Status

Implemented:

- Table configuration can distinguish `FIXED`, `MOVABLE`, `STORAGE`, and `TEMPORARY`.
- Creating a `STORAGE` table disables dining room selection and sends `diningRoomId: null`.
- Tables page shows `STORAGE` tables as warehouse/inventory-style records, not as normal dining room tables.
- Tables page includes a basic storage inventory section.
- Storage inventory section can create `EXTRA_CHAIR`, `STORAGE_TABLE`, and `OTHER` resources.
- UI text states that storage resources are configured but not used automatically by the algorithm yet.

Partial:

- Inventory is embedded in the Tables page, not a dedicated inventory screen.
- Inventory has no frontend filters by type or active state yet.
- Inventory has no frontend edit/deactivate flow yet, although backend supports update/delete.
- The summary is basic and not yet grouped into separate totals for extra chairs, stored tables and other resources.

## Migration Audit

Migrations found:

- `V1__init.sql`
- `V2__security_and_multitenant_base.sql`
- `V3__create_dining_room.sql`
- `V4__create_restaurant_table.sql`
- `V5__create_table_combination.sql`
- `V6__create_customer.sql`
- `V7__create_reservation.sql`
- `V8__create_audit_log.sql`
- `V9__create_restaurant_rule.sql`
- `V10__create_notification.sql`
- `V11__create_scheduled_notification.sql`
- `V12__create_notification_log.sql`
- `V13__create_ai_insight.sql`
- `V14__advanced_table_planning_phase1.sql`

V14 status: `IMPLEMENTED`

V14 adds:

- `restaurant_table.table_type`
- nullable `restaurant_table.dining_room_id`
- check constraint for allowed table types
- check constraint requiring `STORAGE` tables to have no dining room
- `storage_resource`
- indexes for `storage_resource(restaurant_id, resource_type)` and `storage_resource(restaurant_id, active)`

Flyway empty-database validation: `NOT VERIFIED IN THIS ENVIRONMENT`

Reason: integration tests and Docker Compose cannot reach a Docker daemon/socket.

## Specific Sprint 0 Checks

### Can a STORAGE table be created?

Status: `IMPLEMENTED`

Evidence:

- `RestaurantTableService.create` parses `tableType`.
- `resolveDiningRoomForTableType` returns `null` for `TableType.STORAGE`.
- `RestaurantTableIntegrationTest.createStorageTableWithoutDiningRoom` covers API behavior.

### Is STORAGE excluded from operational planning?

Status: `IMPLEMENTED`

Evidence:

- `PlanningSnapshotService` uses `findByRestaurantIdAndTableTypeNotOrderByDiningRoomIdAscCodeAsc(..., TableType.STORAGE)`.
- `PlanningIntegrationTest.storageTableDoesNotAppearAsNormalDiningRoomTable` covers the API behavior, pending Docker/Testcontainers execution.

### Is STORAGE excluded from the basic assignment algorithm?

Status: `IMPLEMENTED`

Evidence:

- `CandidateFinder` uses the non-STORAGE repository query for direct table candidates.
- `CandidateFinder` now skips combinations containing a `STORAGE` table.
- `CandidateFinderTest.excludesCombinationsContainingStorageTables` covers the defensive combination case.

### Is multi-tenant respected?

Status: `IMPLEMENTED_WITH_TEST_GAP`

Evidence:

- Tables use `restaurant_id`; services resolve restaurants through accessible restaurant checks.
- Table lookup uses `findByIdAndRestaurantId` and `findByRestaurantIdAndIdIn`.
- Storage resources use `restaurant_id`; repository supports `findByIdAndRestaurantId`.
- `StorageResourceIntegrationTest.storageResourcesAreScopedByRestaurant` exists but needs Docker/Testcontainers to run.

Remaining risk:

- Tenant isolation is not exhaustively regression-tested for every table/storage endpoint in the fast suite.

## Tests Executed

### Backend quick suite

Command:

```bash
cd backend && mvn test
```

Result: `PASS`

Summary:

- Tests run: 32
- Failures: 0
- Errors: 0
- Skipped: 0

Notes:

- Initial run failed on JDK 26 because Mockito inline could not self-attach Byte Buddy.
- Added `backend/src/test/resources/mockito-extensions/org.mockito.plugins.MockMaker` with `mock-maker-subclass`.
- Re-run passed.

### Frontend build

Command:

```bash
cd frontend && npm run build
```

Result: `PASS`

Summary:

- TypeScript checks passed.
- Vite production build passed.
- Output bundle generated under `frontend/dist`.

### Integration tests / Testcontainers

Command:

```bash
cd backend && mvn test -Pintegration-tests
```

Result: `BLOCKED_BY_ENVIRONMENT`

Observed error:

```text
Could not find a valid Docker environment
NoSuchFileException (/var/run/docker.sock)
```

Meaning:

- Integration tests were discovered.
- Docker/Testcontainers could not start PostgreSQL because no Docker socket is available.
- This does not prove Flyway is broken; it means Flyway-on-empty-PostgreSQL is not verified in this environment.

### Docker Compose

Command:

```bash
docker compose up --build --abort-on-container-exit
```

Result: `BLOCKED_BY_ENVIRONMENT`

Observed error:

```text
failed to connect to the docker API at unix:///Users/angeldenizmartin/.docker/run/docker.sock
```

Local command for the user:

```bash
docker compose up --build
```

Run it after Docker Desktop/daemon is running and the Docker socket is available.

## Risks Detected

- Docker/Testcontainers remain unverified in this environment.
- `StorageResource` exists earlier than the requested Sprint 1 scope, but its frontend is only basic.
- Backend list endpoint for storage resources has no query filters yet.
- Frontend inventory has no edit/deactivate flow yet.
- `TEMPORARY` table type exists but has no explicit behavior beyond non-storage table behavior.
- V14 allows non-STORAGE tables with `dining_room_id` null at database level; service validation prevents it, but direct DB writes could violate the domain invariant.
- Tenant isolation is implemented by service/repository patterns but still needs broader regression coverage.

## Documentation Updated

- `ALGORITHM.md`: clarified that standard combinations containing `STORAGE` are excluded defensively.
- `API.md`: clarified that storage resource query filters are planned, not implemented.
- `TESTING.md`: added JDK/Mockito note and storage/planning regression cases.
- `docs/SPRINT_0_STABILIZATION_REPORT.md`: created this report.

Existing related docs:

- `DATABASE.md` already documents `storage_resource`, `table_type`, and planned advanced tables.
- `ROADMAP.md` already marks advanced storage/table-type work as MVP advanced while keeping setup tasks and advanced flows outside the first step.

## Sprint 1 Recommendation

Sprint 1 should not touch the assignment algorithm. It should make storage inventory usable for managers while preserving the rule that storage resources are not applied automatically.

Recommended Sprint 1 scope:

- Add backend list filters for `resourceType` and `active`.
- Add/update tests for create, filter by type, deactivate, negative quantity rejection, and tenant isolation.
- Improve frontend inventory section or split into a dedicated configuration page.
- Show visible totals for:
  - extra chairs
  - stored tables
  - other resources
- Add edit/deactivate resource UI.
- Keep this UI message visible:

```text
These resources are configured but are not used automatically by the algorithm yet.
```

Explicitly out of Sprint 1:

- setup options
- algorithm use of storage
- manager approval
- setup tasks
- daily floor plan
- 3D
- AI changes
- WhatsApp
- complex drag and drop

## Sprint 0 Decision

Sprint 0 is approved for the code paths that can be verified without Docker:

- Backend quick tests pass.
- Frontend build passes.
- `STORAGE` is excluded from planning and basic candidate generation in code.
- Documentation now records the Docker/Testcontainers gap instead of pretending it passed.

Sprint 0 still requires one local follow-up verification on a machine with Docker running:

```bash
cd backend && mvn test -Pintegration-tests
cd .. && docker compose up --build
```
