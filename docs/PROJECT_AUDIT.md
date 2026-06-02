# Project Audit

## Scope

This audit compares the documented architecture with the implementation currently present in the repository. It reflects the codebase state in the current working tree, including local stabilization changes not yet pushed to GitHub.

## Executive Summary

- The project is no longer a scaffold. It already contains a modular Spring Boot backend, a React frontend, Flyway migrations, JWT auth, restaurant configuration, reservations, planning, realtime events, SMS abstractions and an AI insight layer.
- The high-level architecture is still consistent with the intended monolithic modular design.
- The main gaps are documentation drift, incomplete testing coverage for the optimization core, and a few operational issues recently fixed locally during stabilization.
- The most important technical risk remains the assignment/planning core: it exists and is more than "first free table", but it is still heuristic and only partially protected by focused tests.

## Backend Status

### Implemented

- `auth`, `user`, `restaurant`, `diningroom`, `table`, `tablecombination`, `customer`, `reservation`, `planning`, `optimization`, `rules`, `notification`, `ai`, `audit`, `realtime`, `config`, `common`, `system`
- JWT login, refresh token, logout and `/api/auth/me`
- Role-based authorization with restaurant-level access checks
- Multi-tenant filtering by `restaurantId` on business resources
- CRUD or operational APIs for restaurants, dining rooms, tables, combinations, customers, reservations, rules and planning
- Deterministic assignment service with scoring and stored explanation
- AI insight generation as a deterministic planning analysis layer
- SMS abstraction with fake and Twilio providers
- WebSocket/STOMP topics and event publishing

### Partially Implemented

- Audit coverage is present but not fully systematic across all critical flows
- Planning is operational, but availability/simulation endpoints are not implemented as separate APIs
- AI exists as "insights" rather than the originally documented "AIRecommendation" model
- Dev bootstrap login now exists locally, but it was not part of the original documented flow

### Missing or Not Yet Implemented

- Dedicated `availability` module/package
- Public registration or first-run onboarding flow
- Planning simulation endpoint
- Advanced reassignment / multi-step replanning in the optimization engine
- Redis integration
- WhatsApp integration
- Spring AI / external AI provider integration

## Frontend Status

### Implemented

- Feature-oriented structure under `frontend/src/features`
- Auth flow with login, token refresh and protected routes
- Restaurant selector
- Restaurant, dining room, table, layout and combination configuration screens
- Customers and reservations management screens
- Planning screen with room filters, unassigned reservations and assignment/move actions
- Realtime provider with query invalidation
- Notifications UI and AI insights UI

### Partially Implemented

- Tablet UX is reasonably prepared, but not fully refined
- Layout editor exists, but drag-and-drop is still simple and not based on `dnd-kit`
- No true dashboard beyond the current home/operational navigation

### Missing or Not Yet Implemented

- PWA-specific install/offline features are not visible in the current code
- `dnd-kit`-based drag and drop is not present
- No dedicated calendar/timeline library
- No advanced error boundary or shared form abstraction across all features

## Database Status

### Tables Created by Flyway

- `restaurant`
- `app_user`
- `role_assignment`
- `refresh_token`
- `dining_room`
- `restaurant_table`
- `table_combination`
- `table_combination_item`
- `customer`
- `reservation`
- `reservation_assignment`
- `audit_log`
- `restaurant_rule`
- `notification`
- `scheduled_notification`
- `notification_log`
- `ai_insight`

### Planned but Not Present as Tables

- `planning_slot`
- `ai_recommendation`

### Observations

- `spring.jpa.hibernate.ddl-auto=validate` is configured, so entity/DDL drift matters
- Migration coverage is broad enough for the current MVP modules
- JSONB columns required extra local stabilization because entity mappings were not fully safe for PostgreSQL
- Notification-related schema is richer than the original documentation: there is both in-app notification data and delivery logs

## Algorithm Status

### Implemented

- Candidate generation for tables and predefined table combinations
- Hard constraints: activity, capacity, overlap, room activity, accessibility
- Deterministic scoring with explanation persisted in `reservation_assignment`
- Tie-breaking to keep decisions deterministic

### Not Yet Implemented

- Deep replanning or cascading reassignment
- Separate optimization service abstraction matching the original plan
- Full availability API
- Strong algorithm test suite matching the critical scenarios described in the docs

## Docker Status

### Implemented

- `docker-compose.yml`
- Backend Dockerfile
- Frontend Dockerfile
- PostgreSQL service

### Recent Local Stabilization

- PostgreSQL host port publication was removed to avoid local port conflicts
- Backend startup required local fixes for:
  - Flyway PostgreSQL 16 support
  - backend circular dependency between planning and optimization services
  - JSONB persistence mapping
  - CORS for `localhost:5173`
  - bootstrap admin user for local development

### Remaining Observation

- `/actuator/health` should be reviewed because login works, but health was observed as `DOWN` in one verification pass

## Documentation Drift

### Documented but Not Implemented

- Redis usage
- Spring AI / provider integration
- availability module
- planning simulation API
- `PlanningSlot` persistence
- `AIRecommendation` entity/model

### Implemented but Poorly Documented

- In-app notifications
- scheduled notifications
- AI insights endpoints and frontend
- dev bootstrap admin flow
- notification logs alias endpoint behavior

### Naming / Modeling Inconsistencies

- `AIRecommendation` in docs vs `AiInsight` in code
- documented table `PlanningSlot` vs computed planning response in code
- several documented endpoints use older paths than the actual implemented controllers
- architecture docs still mention `availability` as a backend module, but no such package exists

## Main Risks

### Critical

- Local working tree contains important stabilization fixes that are not yet reflected in repository documentation/history
- Optimization engine still lacks focused automated coverage for its most important scenarios
- Documentation overstates some planned capabilities as if they were already implemented

### High

- Multi-tenant safety depends on disciplined service filtering and should be regression-tested more aggressively
- JSONB persistence strategy needs a consistent long-term approach
- Health reporting and local bootstrap should be formalized for developer experience

### Medium

- Frontend tablet experience still needs refinement
- Realtime is present, but not comprehensively documented or tested end-to-end
- Notification and planning features have grown beyond the original docs and need clearer lifecycle documentation

## Recommended Improvements by Priority

1. Align documentation with the real codebase state
2. Commit and push the current local stabilization fixes after review
3. Add focused tests for assignment scoring, overlap, cleaning buffer, accessibility and combinations
4. Review tenant boundaries module by module and add regression tests
5. Clarify JSONB persistence strategy across all affected entities
6. Review `/actuator/health` contributors and make health semantics meaningful
7. Document which APIs are implemented, partial or planned
8. Add a minimal local onboarding/developer runbook to avoid future login/startup confusion
