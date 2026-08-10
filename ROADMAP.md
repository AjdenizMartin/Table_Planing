# Roadmap

## Phase status

- `Phase 1`: PARTIALLY_DONE
- `Phase 2`: PARTIALLY_DONE
- `Phase 3`: PARTIALLY_DONE
- `Phase 4`: PARTIALLY_DONE
- `Phase 5`: PARTIALLY_DONE
- `Phase 6`: PARTIALLY_DONE
- `Phase 7`: PARTIALLY_DONE
- `Phase 8`: PARTIALLY_DONE
- `Phase 9`: NOT_STARTED
- `Phase 10`: IN_PROGRESS

## Approach

The roadmap prioritizes the product's core value first: restaurant configuration, reservations, assignment engine, and visual planning. Advanced integrations and AI are incorporated after the main operation has been stabilized.

## Phase 1. Project foundation and authentication

**Status:** `PARTIALLY_DONE`

### Objectives

- create the initial technical structure
- configure the backend and frontend
- establish authentication and authorization
- prepare basic multi-tenant isolation

### Deliverables

- structured repository
- base Spring Boot project
- base React project
- login
- JWT and refresh token
- initial roles

### Completion criteria

- an authenticated user can access only their resources
- the app starts in a local environment with Docker

## Phase 2. Restaurant configuration

**Status:** `PARTIALLY_DONE`

### Objectives

- model the restaurant, dining areas, and tables
- enable visual and operational configuration
- support table combinations and initial rules

### Deliverables

- restaurant CRUD
- dining area CRUD
- table CRUD
- combination CRUD
- minimum operational rules

### Completion criteria

- a restaurant can be fully configured without modifying code

## Phase 3. Reservation management

**Status:** `PARTIALLY_DONE`

### Objectives

- create reservations manually
- manage customers
- control reservation statuses
- validate scheduling conflicts

### Deliverables

- customer module
- reservation module
- reservation statuses
- temporal validation
- basic history

### Completion criteria

- a real service can be operated manually without serious errors

## Phase 4. Basic assignment algorithm

**Status:** `PARTIALLY_DONE`

### Objectives

- assign tables automatically
- apply robust scoring
- support valid combinations
- explain the decision made

### Deliverables

- candidate engine
- availability validation
- initial scoring
- assignment explanation

### Planned advanced extension

- exclude storage tables from the normal algorithm
- evaluate extra chairs and storage only at advanced levels with an operational cost
- generate setup plans and tasks before applying special options

### Completion criteria

- the system assigns tables consistently better than a trivial rule

## Phase 5. Visual planning

**Status:** `PARTIALLY_DONE`

### Objectives

- provide a clear view of daily service
- allow reservations to be moved manually
- display availability and conflicts

### Deliverables

- planning by time and table
- filters by dining area
- quick operational actions
- initial drag and drop

### Planned advanced extension

- display storage resources and special setups as distinct operational elements
- display preparation tasks associated with reservations
- allow advanced options to be approved or rejected without changing existing times

### Completion criteria

- the manager can work primarily from the planning view

## Phase 6. Real time

**Status:** `PARTIALLY_DONE`

### Objectives

- synchronize changes between devices
- reflect the current planning status

### Deliverables

- WebSocket/STOMP
- reservation events
- planning events
- reactive frontend updates

### Completion criteria

- changes are visible almost in real time on two connected clients

## Phase 7. SMS and WhatsApp confirmations

**Status:** `PARTIALLY_DONE`

### Objectives

- automate reminders and confirmations
- reduce no-shows and uncertain reservations

### Deliverables

- Twilio SMS integration
- provider abstraction
- delivery logs
- retries
- foundation for WhatsApp

### Completion criteria

- messages are sent and auditable end-to-end

## Phase 8. AI and recommendations

**Status:** `PARTIALLY_DONE`

### Objectives

- add a layer of explanatory intelligence
- identify opportunities for improvement

### Deliverables

- planning recommendations
- decision explanations
- suboptimal usage alerts

### Completion criteria

- AI provides useful context without replacing the algorithm

## Phase 9. Statistics and advanced optimization

**Status:** `NOT_STARTED`

### Objectives

- measure operational performance
- improve scoring with historical data
- evaluate future scenarios

### Deliverables

- occupancy metrics
- no-show rate
- analysis of unusable gaps
- advanced simulations

### Completion criteria

- product improvements can be justified with metrics

## Phase 10. Production readiness

**Status:** `IN_PROGRESS`

**Code track:** `DONE`; validation in real infrastructure remains pending.

### Objectives

- harden the platform
- prepare pilot deployment and support

### Deliverables

- production configuration
- backups
- observability
- operational documentation
- reviewed security

### Implemented for the pilot

- production profile without public registration or demo credentials in the frontend
- unprivileged backend, read-only filesystem, capabilities removed, and file-based secrets
- TLS, CSP, rate limiting, internal network, and log rotation
- reproducible preflight and monitoring of HTTPS, TLS, disk, and backups with optional alerts
- documented onboarding, encrypted external backup, restoration, rollback, and UAT

### Pending closure

- run preflight and deploy the approved tag on Ubuntu 24.04
- verify S3 backup and actual restoration
- validate performance, a physical Android device, and UAT with real users/data

### Completion criteria

- the product is ready for a real pilot with restaurants

## Recommended MVP

The MVP should include only what is essential to demonstrate real value:

- authentication
- restaurant, dining areas, and tables
- combinations
- manual reservations
- initial automatic assignment
- visual planning
- basic SMS confirmation

## Recommended advanced MVP

To demonstrate value for the real-world problem of movable and stored tables without disrupting the current system:

- `tableType` on tables (`FIXED`, `MOVABLE`, `STORAGE`, `TEMPORARY`)
- `StorageResource` inventory for extra chairs and stored tables
- exclusion of `STORAGE` tables from normal planning and basic candidates
- minimum UI for viewing and creating storage resources
- documentation of the tiered algorithm before implementing automatic setups

### Sprint 1. Storage Inventory

**Status:** `DONE`

- configurable inventory with operational types, quantity, capacity per unit, and preparation time
- filters by type and status
- summary of chairs, tables, and active/inactive resources
- editing, deactivation, and reactivation without physical deletion
- restaurant isolation and negative validations covered by integration tests
- no automatic use by the algorithm

### Sprint 2. Advanced Combinations

**Status:** `DONE`

- V16 with type, cost, preparation, and inventory requirements
- backend/frontend CRUD with effective capacity and multi-tenant validation
- protection of inventory committed to future reservations

### Sprint 3. Suggestions and safe application

**Status:** `DONE`

- separate automatic/manual modes and deterministic top 3
- V17 with resource snapshots per assignment
- transactional selection, pessimistic locking, auditing, and real time

### Sprint 4. Operations and tablet

**Status:** `DONE`

- comparison of suggestions, resources, and history in the reservation panel
- separate approval permissions for manager and staff
- Vitest, Testing Library, and Playwright at 768/1024 px

### Sprint 5. Pilot deployment

**Status:** `READY_FOR_ENVIRONMENT`

- static Nginx frontend, API/WebSocket proxy, TLS, and rate limiting
- production Compose with internal network, secrets, health checks, and restart
- documented backup, restoration, and rollback

### Sprint 6. Pilot launch

**Status:** `READY_FOR_UAT`

- automated critical E2E tests and performance script
- transactional onboarding runner, fixture with 150 reservations, and available UAT checklist
- automatable TLS renewal and encrypted external backup
- external dependencies: domain, certificates, real data, accounts, and UAT execution on VPS

Setup plans, operational tasks, and the advanced visual editor remain outside the pilot. Explicit selection by the manager serves as approval.

## Outside the MVP

- automatic intake from all external channels
- advanced forecasting
- AI with complex replanning
- multi-location enterprise dashboards

## Suggested implementation order

1. documentation and foundational decisions
2. repository structure
3. base backend and security
4. base frontend
5. restaurant domain and layout
6. reservations and customers
7. initial algorithm
8. visual planning
9. real time
10. messaging
