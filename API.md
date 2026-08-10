# API

## Objective

This document distinguishes between the API actually implemented today and the API planned for future phases.

## API Principles

- REST API on `Spring Boot`
- simple initial versioning with the `/api` prefix
- JWT authentication
- mandatory multi-tenant isolation
- backend input validation
- consistent, traceable responses
- WebSocket for real-time updates, not write operations

## General Conventions

### Base path

```text
/api
```

### Format

- `application/json` for request and response
- dates in `ISO-8601`
- timestamps in UTC

### Authentication

Except for defined public exceptions, endpoints require:

```text
Authorization: Bearer <access_token>
```

### Restaurant Context

The application supports multiple restaurants. The backend must resolve the restaurant context from:

- token claims
- user permissions
- target resource

In future phases, an explicit supporting header may be accepted, for example:

```text
X-Restaurant-Id: <uuid or id>
```

However, it must never replace permission validation.

## Suggested HTTP Statuses

- `200 OK`
- `201 Created`
- `204 No Content`
- `400 Bad Request`
- `401 Unauthorized`
- `403 Forbidden`
- `404 Not Found`
- `409 Conflict`
- `422 Unprocessable Entity`

## Suggested Error Structure

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": [
    {
      "field": "partySize",
      "message": "must be greater than 0"
    }
  ],
  "timestamp": "2026-05-26T13:00:00Z"
}
```

## Current Status

- `IMPLEMENTED`: a controller and functional base flow exist
- `PARTIAL`: exists in a limited form or with a narrower scope than originally planned
- `PLANNED`: documented but not present in the current backend

## Endpoints

### 1. System

Internal or technical use to verify the backend's basic status.

- `GET /api/system/ping` - `IMPLEMENTED`
- `GET /actuator/health` - `IMPLEMENTED`

### 2. Auth

Responsibility:

- login
- refresh token
- current session
- logical logout

Endpoints:

- `POST /api/auth/login` - `IMPLEMENTED`
- `POST /api/auth/refresh` - `IMPLEMENTED`
- `POST /api/auth/logout` - `IMPLEMENTED`
- `GET /api/auth/me` - `IMPLEMENTED`
- `POST /api/auth/register` - `DEV_ONLY`; the controller is not registered under the `prod` profile, and Nginx returns `404`

Expected initial payloads:

`POST /api/auth/login`

```json
{
  "email": "owner@restaurant.com",
  "password": "********"
}
```

Conceptual response:

```json
{
  "accessToken": "jwt-access-token",
  "refreshToken": "opaque-or-jwt-refresh-token",
  "expiresIn": 900,
  "user": {
    "id": 1,
    "name": "Owner Name",
    "email": "owner@restaurant.com"
  },
  "restaurants": [
    {
      "id": 10,
      "name": "Main Restaurant",
      "role": "RESTAURANT_OWNER"
    }
  ]
}
```

### 3. Restaurants

Responsibility:

- restaurant creation and editing
- general configuration queries
- multi-tenant administration

Endpoints:

- `POST /api/restaurants` - `IMPLEMENTED`
- `GET /api/restaurants` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/settings` - `PLANNED`
- `PUT /api/restaurants/{restaurantId}/settings` - `PLANNED`

### 4. Dining Rooms

Responsibility:

- manage restaurant dining rooms or zones
- define priority and accessibility

Endpoints:

- `POST /api/restaurants/{restaurantId}/dining-rooms` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/dining-rooms` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/dining-rooms/{diningRoomId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/dining-rooms/{diningRoomId}` - `IMPLEMENTED`
- `DELETE /api/restaurants/{restaurantId}/dining-rooms/{diningRoomId}` - `IMPLEMENTED`

### 5. Tables

Responsibility:

- manage tables
- minimum and maximum capacity
- status and visual position

Endpoints:

- `POST /api/restaurants/{restaurantId}/tables` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/tables` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/tables/{tableId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/tables/{tableId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/tables/{tableId}/layout` - `IMPLEMENTED`
- `DELETE /api/restaurants/{restaurantId}/tables/{tableId}` - `IMPLEMENTED`
- `POST /api/tables/{tableId}/enable` - `PLANNED`
- `POST /api/tables/{tableId}/disable` - `PLANNED`

Phase 1 of advanced planning adds `tableType` to tables:

- `FIXED`
- `MOVABLE`
- `STORAGE`
- `TEMPORARY`

`STORAGE` tables are recorded as operational inventory and do not appear as regular tables in the plan.

### 6. Table Combinations

Responsibility:

- configure valid table combinations
- enable or disable combinations

Endpoints:

- `POST /api/restaurants/{restaurantId}/table-combinations` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/table-combinations` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/table-combinations/{combinationId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/table-combinations/{combinationId}` - `IMPLEMENTED`
- `DELETE /api/restaurants/{restaurantId}/table-combinations/{combinationId}` - `IMPLEMENTED`

The creation and editing contracts accept `combinationType`, `operationalCostLevel`, `setupTimeMinutes`, and `resourceRequirements: [{storageResourceId, quantity}]`. Omitted fields maintain compatibility as `STANDARD`, `LOW`, `0`, and an empty list. A standard combination cannot consume inventory or require prior setup.

### 6b. Storage Resources

Responsibility:

- manage storage inventory such as extra chairs or stored tables
- validate available quantities for future setup options

Endpoints:

- `POST /api/restaurants/{restaurantId}/storage-resources` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/storage-resources?resourceType={type}&active={boolean}` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/storage-resources/{resourceId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/storage-resources/{resourceId}` - `IMPLEMENTED`
- `DELETE /api/restaurants/{restaurantId}/storage-resources/{resourceId}` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/storage-resources/{resourceId}/availability-check` - `IMPLEMENTED`

Current resource types:

- `EXTRA_TABLE`
- `EXTRA_CHAIR`
- `HIGH_CHAIR`
- `FOLDING_TABLE`
- `TABLE_EXTENSION`
- `BENCH`
- `STORAGE_TABLE`
- `OTHER`

The `resourceType` and `active` filters are optional and may be combined. All queries and modifications are resolved by `restaurantId`; a resource identifier belonging to another restaurant returns `NOT_FOUND`.

Fields editable through `PATCH`:

- `resourceType`
- `name`
- `quantity`
- `capacityPerUnit`
- `setupTimeMinutes`
- `notes`
- `active`

`quantity`, `capacityPerUnit`, and `setupTimeMinutes` must be non-negative integers. `name` and `resourceType` are required on creation. The two operational fields added in Sprint 1 may be omitted on creation and are initialized to `0` to maintain compatibility with previous clients.

`DELETE` does not physically delete the record: it maintains compatibility with the existing endpoint and performs a logical deactivation. The UI uses `PATCH` with `active=false` or `active=true` to deactivate and reactivate it.

A resource may not be reduced or deactivated below its already committed peak future consumption. The calculation considers active assignments and the setup, service, and cleaning windows.

### 7. Customers

Responsibility:

- create and maintain customer records
- store preferences and operational incidents

Endpoints:

- `POST /api/restaurants/{restaurantId}/customers` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/customers` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/customers/{customerId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/customers/{customerId}` - `IMPLEMENTED`
- `DELETE /api/restaurants/{restaurantId}/customers/{customerId}` - `IMPLEMENTED`

Physical deletion requires the `PLATFORM_ADMIN`, `RESTAURANT_OWNER`, or `MANAGER` role,
respects restaurant isolation, and records `customer.deleted` in the audit log.
It returns `204` when applied and `409` with `reason=HAS_RESERVATIONS` if associated
reservations exist; `WAITER` may view the record but cannot delete it.

Planned filters:

- `phone`
- `email`
- `name`
- `tags`

### 8. Reservations

Responsibility:

- reservation creation and maintenance
- status changes
- confirmation, cancellation, and no-show
- automatic or manual assignment

Endpoints:

- `POST /api/restaurants/{restaurantId}/reservations` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/reservations` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/reservations/{reservationId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/reservations/{reservationId}` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/assign` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/reservations/{reservationId}/assignment-suggestions` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/assignment-selection` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/reservations/{reservationId}/assignment-history` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/confirm` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/cancel` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/seat` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/complete` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/no-show` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/reassign` - `PLANNED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/arrived` - `IMPLEMENTED`

`assignment-suggestions` returns at most three deterministic candidates and does not write to the database. Each option includes tables, capacity, score, cost, setup, required/available inventory, and a structured explanation.

`assignment-selection` receives:

```json
{
  "candidateType": "TABLE_COMBINATION",
  "candidateId": 42
}
```

The selection is revalidated within a transaction, locks inventory resources in a stable order, rechecks overlaps, and only then deactivates the previous assignment and persists the new one. A concurrent conflict returns `409` and does not oversell inventory.

Owner, manager, and platform admin users may view and apply suggestions. Staff may view planning, assigned resources, and history, but may not request or approve suggestions. The automatic `assign` endpoint, automatic confirmation, and daily recalculation remain limited to `STANDARD` tables and combinations.

Planned filters:

- `restaurantId`
- `date`
- `status`
- `channel`
- `customerId`

Conceptual payload for creating a reservation:

```json
{
  "restaurantId": 10,
  "customerId": 200,
  "partySize": 4,
  "reservationDate": "2026-05-30",
  "startTime": "20:30:00",
  "channel": "MANUAL",
  "specialRequests": "Quiet table if possible",
  "accessibilityRequired": false
}
```

### 9. Planning

- `GET /api/restaurants/{restaurantId}/planning?date=YYYY-MM-DD` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/planning/recalculate` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/planning/move-reservation` - `IMPLEMENTED`
- `GET /api/availability` - `PLANNED`
- `POST /api/planning/simulate` - `PLANNED`

### 10. Rules

- `POST /api/restaurants/{restaurantId}/rules` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/rules` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/rules/{ruleId}` - `IMPLEMENTED`
- `DELETE /api/restaurants/{restaurantId}/rules/{ruleId}` - `IMPLEMENTED`

### 11. Notifications

- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/notifications/confirmation` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/notifications` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/notifications/unread-count` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/notifications/{notificationId}/read` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/notifications/read-all` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/notifications/logs` - `IMPLEMENTED`

### 12. AI Insights

- `GET /api/restaurants/{restaurantId}/ai/insights?date=YYYY-MM-DD` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/ai/insights/summary?date=YYYY-MM-DD` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/ai/insights/{insightId}/dismiss` - `IMPLEMENTED`

## Future Implementation Notes

- do not expose algorithm logic directly in controllers
- separate DTOs from entities
- document contracts with OpenAPI later
- maintain naming consistent with `DATABASE.md`, `ARCHITECTURE.md`, and `ALGORITHM.md`
- AI does not assign tables by itself; in the current state, it only analyzes and explains planning
