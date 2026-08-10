# Testing

## Objective

Define a testing strategy consistent with a platform where business logic, multi-tenant isolation, and the assignment algorithm are critical.

## Principles

- test the logic that delivers real value first
- combine fast unit tests with realistic integration tests
- pay particular attention to permissions, time calculations, and the algorithm
- avoid a slow or fragile suite from the outset

## Recommended testing pyramid

### 1. Unit tests

For:

- pure utilities
- validation rules
- time calculations
- algorithm scoring
- isolated permission resolution

Objective:

- high speed
- fast feedback
- strong edge-case coverage

### 2. Integration tests

For:

- application services
- JPA repositories
- HTTP flows with Spring Boot
- actual endpoint security
- Flyway integration

Objective:

- validate module wiring
- detect configuration or persistence errors

### 3. Selective end-to-end tests

For later phases:

- login
- create a reservation
- view planning
- move a reservation

They are not an absolute priority before the backend and data model are stabilized.

## Recommended tools

### Backend

- `JUnit 5`
- `Spring Boot Test`
- `MockMvc`
- `Spring Security Test`
- `Testcontainers`
- `AssertJ`

### Frontend

- `Vitest`
- `React Testing Library`
- `Playwright` with Chromium for critical flows at 768 and 1024 px
- tests for the ES/EN selector and language persistence
- interface tests for destructive actions and visible permissions

## Testcontainers

Using `Testcontainers` from the early phases is recommended for:

- real PostgreSQL
- Redis if it is included in relevant tests

Benefits:

- tests that are more faithful than database mocks
- actual validation of Flyway migrations
- lower risk of differences between local environments and CI

## Suite execution

The backend suite is divided into fast tests and integration tests to prevent Docker from blocking daily feedback.

Commands:

- `mvn test` from `backend/`: runs unit tests and excludes `*IntegrationTest`.
- `mvn test -Pintegration-tests` from `backend/`: also runs `*IntegrationTest`; requires Docker/Testcontainers to be available.
- `mvn clean compile` from `backend/`: validates compilation without running tests.
- `npm test` from `frontend/`: runs component tests and form contract tests.
- `npm run build` from `frontend/`: validates TypeScript and the static bundle.
- `npm run e2e` from `frontend/`: runs manager/staff flows on desktop and tablet with a controlled network API.

Local note:

- if the Docker CLI responds but Testcontainers fails with `Could not find a valid Docker environment`, check the active Docker Desktop socket and the compatibility of `docker-java`/Testcontainers with the installed Docker Desktop version
- on macOS with Docker Desktop, it may be necessary to correctly expose the socket used by the `desktop-linux` context; until this is resolved, `mvn test` remains the mandatory fast-feedback suite
- on recent JDKs, Mockito inline may fail if the runtime does not permit agent self-attachment; the suite uses `mock-maker-subclass` in tests to avoid relying on that mechanism when only interfaces and non-final collaborators are mocked.

Practical rule:

- changes to the algorithm, rules, SMS, audit, or AI must have fast unit tests whenever possible
- HTTP flows, actual security, Flyway, and JPA must be validated with integration tests
- if Docker is unavailable, integration tests must not block the fast suite

## Test types by module

### Auth and Security

Important cases:

- successful login
- failed login
- valid refresh
- invalid or revoked refresh
- access to a protected endpoint without a token
- access with an insufficient role
- access to another restaurant's resource

### Restaurant, DiningRoom, and Table

Important cases:

- create a valid restaurant
- prevent prohibited duplicates
- create a dining room and table with valid capacities
- prevent editing another restaurant's resources

### Reservation

Important cases:

- create a valid reservation
- reject a reservation with invalid data
- detect overlap according to the effective window
- change statuses correctly
- cancel and release the associated resource

### Planning

Important cases:

- return the restaurant's planning for the day
- isolate dining rooms correctly
- restrict recalculation to permitted roles
- do not display `STORAGE` tables as operational planning tables

### Storage Inventory

Important cases:

- create an active storage resource
- reject negative quantities
- reject negative `capacityPerUnit` and `setupTimeMinutes`
- require `name` and `resourceType` on creation
- filter by `resourceType`
- filter by `active`
- update name, type, quantity, capacity, time, notes, and status
- deactivate and reactivate without physically deleting
- filter by restaurant
- do not expose another restaurant's resources
- do not modify another restaurant's resources
- prevent reducing or deactivating inventory below future consumption requirements
- keep inventory out of automatic mode

### Advanced Assignments

Covered cases:

- compatible migration of existing combinations
- effective capacity with resources of any type
- advanced exclusion in `AUTOMATIC`
- inclusion, ranking, and penalties in `MANUAL_SUGGESTION`
- suggest without mutating assignments
- apply and persist inventory snapshots
- detect inventory overlaps
- revalidate under a transactional lock
- isolate restaurants and restrict approval to owner/manager/admin

### Notification

Important cases:

- record the delivery log
- retry according to status
- do not call the external provider if SMS is disabled
- do not expose sensitive data to unauthorized roles

### AI

Important cases:

- generate an explanatory recommendation from valid data
- preserve dismissed when regenerating equivalent insights
- do not allow AI to act as the primary source of assignments

## Algorithm testing

This is one of the most important aspects of the product.

### Algorithm testing objectives

- validate hard constraints
- protect scoring
- ensure consistent decisions
- avoid regressions when adjusting weights or rules

### Recommended minimum scenarios

- a reservation for 2 does not occupy a table for 6 if a suitable table exists
- a table with a scheduling conflict is not assigned
- the cleaning buffer is respected
- a non-priority dining room is avoided when unnecessary
- accessibility requirements are respected
- blocking a large table is penalized
- a relevant dead gap is detected
- a one-step reassignment improves the result
- a `STORAGE` table is not considered a direct candidate
- a combination containing a `STORAGE` table is not considered a basic candidate

### Approach

- unit tests for score functions and filters
- integration tests for complete planning scenarios
- small, readable datasets

## API tests

The following should be covered:

- correct status codes
- payload validations
- date serialization
- base response contracts
- controlled errors

Cases:

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/restaurants`
- `POST /api/reservations`
- `GET /api/planning`

## Permission tests

They must be explicit and not left implicit within other tests.

Minimum cases:

- `WAITER` cannot edit configuration
- `MANAGER` can create reservations
- `RESTAURANT_OWNER` can edit their restaurant
- `WAITER` cannot delete customers
- manager can delete customers without reservations
- a customer from another restaurant cannot be deleted
- a customer with associated reservations returns a conflict and retains their data
- a user from restaurant A cannot access data from restaurant B
- `PLATFORM_ADMIN` can operate globally according to policy

## WebSocket tests

They are not a priority in the first line of code, but they must be planned.

Suggested coverage:

- authenticated connection
- subscription by restaurant
- event emission when creating a reservation
- event emission when canceling a reservation
- do not receive events from another restaurant

## Test data strategy

- factories or builders for entities
- small, readable fixtures
- avoid hidden dependencies between tests
- each test must prepare its own context or a controlled one

## Recommended CI

Current pipeline:

1. validate formatting and compilation
2. run unit tests
3. run integration tests
4. run frontend tests and static build
5. run Playwright on desktop and tablet
6. build backend, frontend dev, and frontend prod Docker images

Before the pilot, `PilotOnboardingIntegrationTest` is also run: it verifies audited creation, idempotent repetition, rollback on conflict, and rejection of password files without `0600` permissions.

Playwright covers tablet viewports with Chromium, but does not replace UAT on a physical Android tablet. That manual gate includes touch input, virtual keyboard, rotation, time zone, and WebSocket reconnection.

## Production validation

- `ProductionRegistrationIntegrationTest` starts with the `prod` profile and requires `404` at `/api/auth/register`.
- frontend tests verify that a production build cannot enable the registration route.
- the production build must not contain demo credentials.
- `scripts/production-preflight.sh` rejects a checkout without an exact tag, insecure secrets, missing DNS, insufficient disk space, or invalid Compose configuration.
- `scripts/pilot-ops-check.sh` fails when containers are unhealthy, HTTPS/registration/TLS settings are incorrect, disk usage is high, or backups are missing.
- the backend image must run as a non-root user and start with a read-only filesystem and dropped capabilities.

## Definition of done for testing

Before completing an important feature:

- happy-path coverage exists
- relevant error cases exist
- permissions have been tested
- multi-tenant constraints have been tested
- if it affects the algorithm, regression test cases exist

## Scope of the first technical phase

The first phase must establish:

- Spring context tests
- basic security tests
- initial `Testcontainers` setup for PostgreSQL
- foundation for `MockMvc`

Not yet required:

- complete e2e suite
- exhaustive frontend coverage
- advanced algorithm simulations
