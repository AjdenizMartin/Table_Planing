# Architecture

## Summary

The solution is designed as a modular monolith with a decoupled frontend. The priority is to maximize development speed, transactional consistency, and domain clarity while retaining sufficient room to evolve toward advanced integrations and capabilities.

## Architectural Objectives

- support one or more restaurants on a single platform
- allow operational configuration without modifying code
- maintain transactional consistency in reservations and assignments
- provide real-time planning updates
- clearly separate the algorithm, business logic, and UI
- enable gradual growth toward a commercial SaaS

## High-Level Architecture

```text
Tablet / Web PWA (React + TypeScript)
        ↓
REST API + WebSocket Gateway (Spring Boot)
        ↓
Application Services / Domain Modules
        ↓
Planning Engine + Optimization Engine
        ↓
PostgreSQL
        ↓
Redis (cache, locks, realtime helpers)
        ↓
External services
  - Twilio SMS
  - WhatsApp Cloud API
  - AI provider via Spring AI
```

## Architectural Style

### Backend

A modular monolith separated by domain is recommended:

- `auth`
- `user`
- `restaurant`
- `diningroom`
- `table`
- `customer`
- `reservation`
- `planning`
- `optimization`
- `availability`
- `notification`
- `rules`
- `ai`
- `audit`

Advantages:

- simple, safe transactions
- lower operational complexity
- faster MVP delivery
- highly cohesive domain

### Frontend

Tablet-oriented React SPA/PWA application:

- fast operational views
- visual planning by time and table
- high-frequency-use forms
- backend synchronization through REST and WebSocket

## Main Components

### 1. Frontend React PWA

Responsibilities:

- authentication and restaurant context
- daily dashboard
- visual planning
- reservation and customer management
- dining room, table, and rule configuration
- consumption of real-time events

### 2. API Spring Boot

Responsibilities:

- expose REST endpoints
- enforce authentication and authorization
- coordinate domain services
- emit WebSocket events
- integrate external services

### 3. Planning Engine

Responsibilities:

- build the daily plan
- calculate effective occupancy windows
- detect conflicts and gaps
- prepare operational information for the UI

Current implementation:

- `PlanningService` orchestrates permissions, recalculation, manual moves, auditing, and real-time events
- `PlanningSnapshotService` builds the daily snapshot reused by planning, assignment, and insights
- `ReservationAssignmentService` does not depend on `PlanningService`; it uses `PlanningSnapshotService` to regenerate insights after assignment

### 4. Optimization Engine

Responsibilities:

- find valid tables and combinations
- calculate scores
- justify decisions
- test bounded reallocations

### 5. Persistence Layer

Responsibilities:

- store configuration, reservations, assignments, and logs
- ensure data integrity
- support auditing and multi-tenancy

### 6. Notification Layer

Responsibilities:

- confirmations and reminders
- retries and delivery logs
- provider abstraction

### 7. AI Assistance Layer

Responsibilities:

- explain algorithm decisions
- detect suboptimal patterns
- suggest reorganizations
- never make autonomous assignment decisions

## Key Principles

### 1. The Algorithm Decides; AI Explains

Table assignment must depend on rules and deterministic scoring. AI must never replace the primary engine.

### 2. Logical Multi-Tenancy from the Start

Every business resource must be linked to `restaurant_id`. Every query, permission, and event must respect that isolation.

### 3. Configuration Without Code

The restaurant must be able to create:

- dining rooms
- tables
- combinations
- priorities
- operational rules

### 4. Explainability

Every assignment must be able to explain:

- which option was selected
- which alternatives were rejected
- which rules influenced the decision

### 5. Real Time with the Backend as the Source of Truth

WebSocket is used for visual synchronization. Validation and consistency remain in the backend.

## Security

- JWT with access token and refresh token
- role-based authorization
- mandatory filtering by restaurant
- auditing of critical changes
- rate limiting for login and messaging

## Planned Roles

- `PLATFORM_ADMIN`
- `RESTAURANT_OWNER`
- `MANAGER`
- `WAITER`

## Backend Modules

| Module | Responsibility |
|---|---|
| `auth` | login, JWT, refresh, and permissions |
| `user` | users, profiles, and memberships |
| `restaurant` | global restaurant data |
| `diningroom` | dining rooms, zones, priorities |
| `table` | tables, capacities, statuses, layout |
| `customer` | customers and preferences |
| `reservation` | reservations, statuses, and lifecycle |
| `planning` | daily operational view and reusable snapshot |
| `optimization` | scoring, candidates, and reallocation |
| `availability` | availability queries |
| `notification` | SMS, WhatsApp, and logs |
| `rules` | configurable business rules |
| `ai` | recommendations and explanations |
| `audit` | action traceability |

## Recommended Infrastructure

### Development

- Docker Compose
- backend
- frontend
- PostgreSQL
- Redis

### Initial Production

- VPS or a platform such as Render, Railway, or Fly.io
- Nginx as a reverse proxy
- backend packaged in a container
- frontend served as static assets
- managed or self-managed PostgreSQL

## Planned Observability

- structured logs
- key event traces
- assignment and occupancy metrics
- functional auditing

## Future Evolution

The following may be separated in the future:

- notification service
- external channel integrations
- advanced analytics

However, fragmentation is not recommended before validating the product and the core algorithm.
