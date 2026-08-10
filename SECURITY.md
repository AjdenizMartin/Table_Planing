# Security

## Objective

Define the platform's initial security model for the first technical phase and the phases immediately following it. The goal is to protect access, data, and restaurant isolation without introducing unnecessary complexity.

## Principles

- centralized authentication
- authorization by role and restaurant context
- mandatory multi-tenant isolation
- backend as the final authority on permissions
- traceability of sensitive actions
- least privilege by default

## System roles

### `PLATFORM_ADMIN`

Scope:

- manages the entire platform
- can create restaurants
- can manage global users
- can inspect general configuration

It must not be used as a shortcut for restaurant logic in the UI.

Initial implementation:

- the persistence model continues to use `RoleAssignment` with `restaurant_id`
- if a user has at least one `RoleAssignment` with the `PLATFORM_ADMIN` role, authorization treats it as global access

### `RESTAURANT_OWNER`

Scope:

- administers their restaurant
- configures dining rooms, tables, rules, and integrations
- manages restaurant users
- can manage reservations and planning

### `MANAGER`

Scope:

- handles day-to-day operations
- creates and modifies reservations
- reassigns tables
- uses planning, customers, and confirmations
- can modify limited operational configuration according to policy

### `WAITER`

Scope:

- views planning
- records arrivals or permitted operational changes
- must not modify the restaurant structure or security

## Initial permissions matrix

| Resource / action | PLATFORM_ADMIN | RESTAURANT_OWNER | MANAGER | WAITER |
|---|---|---|---|---|
| View assigned restaurants | Yes | Yes | Yes | Yes |
| Create restaurant | Yes | No | No | No |
| Edit restaurant data | Yes | Yes | Limited | No |
| Manage dining rooms and tables | Yes | Yes | Limited | No |
| Manage combinations | Yes | Yes | Limited | No |
| Manage rules | Yes | Yes | Limited | No |
| Create and edit customers | Yes | Yes | Yes | Limited |
| Delete customers without reservations | Yes | Yes | Yes | No |
| Create and edit reservations | Yes | Yes | Yes | Limited |
| Confirm or cancel reservations | Yes | Yes | Yes | Limited |
| Manually reassign tables | Yes | Yes | Yes | No |
| View advanced top 3 | Yes | Yes | Yes | No |
| Approve advanced assignment | Yes | Yes | Yes | No |
| View resources and history | Yes | Yes | Yes | Yes |
| View planning | Yes | Yes | Yes | Yes |
| Recalculate planning | Yes | Yes | Yes | No |
| View AI recommendations | Yes | Yes | Yes | No |
| Manage restaurant users | Yes | Yes | No | No |

`Limited` means that the action can be enabled with more precise restrictions to be defined per endpoint or policy.

## Authentication model

## Access token

- JWT format
- short-lived
- sent in the `Authorization` header
- signed by the backend

Suggested claims:

- `sub`
- `user_id`
- `email`
- `roles`
- `restaurant_ids`
- optional `active_restaurant_id`
- `iat`
- `exp`

## Refresh token

- longer-lived token
- stored securely
- used to renew the `access token`
- revocable

In the initial implementation, it is modeled as a persisted, opaque token that can be revoked by the backend.

## Session

Initial flow:

1. login with email and password
2. issuance of `access token`
3. issuance of `refresh token`
4. renewal through `/api/auth/refresh`
5. logical invalidation on logout

## Passwords

- hash with `BCrypt` or `Argon2`
- never store passwords in plain text
- never record passwords in logs

## Multi-tenant

## Primary rule

Every business resource belongs to a restaurant or must be resolved within a restaurant context.

## Isolation rules

- every functional query must filter by `restaurant_id`
- do not rely solely on IDs sent by the client
- the backend must validate that the user has access to the resource's restaurant
- WebSocket events must also be isolated by restaurant

## Restaurant context resolution

Suggested order:

1. validate the user's token
2. resolve authorized restaurants
3. validate the requested resource
4. confirm that the resource belongs to the expected restaurant

## Authorization

It is recommended to combine:

- declarative authorization at endpoints
- domain validation in services

This prevents an endpoint from appearing to be protected while allowing improper cross-restaurant or cross-action access.

## Access rules by module

### Auth

- public login
- controlled refresh
- authenticated `me`

### Restaurant

- read and write access limited by role
- `PLATFORM_ADMIN` with global scope
- `RESTAURANT_OWNER` with scope over their restaurant

### DiningRoom, Table and TableCombination

- write access only for an authorized owner or manager
- read access for the restaurant's operational roles

### Customer and Reservation

- read and write access for operational roles
- `WAITER` with access restricted to service actions
- suggestions and advanced selection only for owner, manager, and platform admin
- history and assigned resources visible to the restaurant's operational roles

### Planning

- read access for all operational roles
- recalculation only for manager, owner, or platform admin

### Notification

- triggering and viewing logs according to role
- restricted access to sensitive data such as phone numbers

### AI

- recommendation read access for owner and manager
- actions that apply suggestions are subject to planning permissions

### Customers

- `PLATFORM_ADMIN`, `RESTAURANT_OWNER`, and `MANAGER` can delete customers
- `WAITER` retains read access and does not see the delete action
- the service validates the target restaurant and blocks deletion if there are associated reservations
- every completed deletion generates an audit entry

## API security

- HTTPS mandatory in production
- CORS restricted to allowed origins
- payload validation with `Spring Validation`
- rate limiting for login and messaging
- errors without sensitive information leakage

Pilot implementation:

- Nginx terminates TLS and redirects HTTP to HTTPS
- login limited to 5 requests per minute per IP, with controlled bursts
- backend CORS parameterized and restricted to the pilot's HTTPS origin
- `prod` profile without a registration controller, frontend build without a registration route/CTA, and additional blocking in Nginx
- Actuator exposes only `health` and is not published to the Internet
- PostgreSQL and the backend reside on an internal Docker network without host ports
- PostgreSQL and JWT secrets are mounted as files, never included in the image
- the `prod` profile does not activate the demo bootstrap because it is limited to `@Profile("dev")`
- the backend entrypoint starts with limited capabilities to read `0600` secrets, immediately switches to UID/GID `10001`, and runs Java without Linux capabilities, with a read-only filesystem and `no-new-privileges`
- Nginx applies HSTS, CSP, framing protection, and browser permission restrictions
- rotated Docker logs and an operational monitor with an optional webhook alert that does not include secrets

IP-based rate limiting in Nginx is appropriate for the VPS pilot. Before broad public exposure, it must be supplemented with observability, progressive account-based blocking, and edge provider protection.

## Frontend security

- do not store secrets in the frontend
- avoid placing permission rules only in the UI
- hide unauthorized actions, but do not rely on this as an actual control

## Audit

At a minimum, the following must be recorded:

- successful and relevant failed logins
- restaurant creation and modification
- changes to tables and dining rooms
- reservation creation, confirmation, cancellation, and no-show
- planning reassignments and recalculations
- notification deliveries

## Initial risks and mitigation

### Risk: cross-restaurant data leakage

Mitigation:

- filters by `restaurant_id`
- isolation tests
- resource ownership validation

### Risk: excessive privileges

Mitigation:

- minimum permissions per role
- endpoints with explicit rules
- security reviews by module

### Risk: compromised refresh token

Mitigation:

- revocation
- future rotation
- reasonable expiration

### Risk: exposure of phone numbers or internal notes

Mitigation:

- careful serialization
- DTOs
- fine-grained permission control

## Scope of the first technical phase

The first phase must deliver:

- login
- `access token`
- `refresh token`
- `me` endpoint
- restaurant context
- basic role-based authorization
- foundation for auditing

Not yet required:

- SSO
- MFA
- advanced device management policies
- complex delegation between users
