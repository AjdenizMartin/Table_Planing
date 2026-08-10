# AGENTS

## Purpose

This document defines how development agents should collaborate on this project, especially in the early stages, when the architecture, data model, and algorithm are more important than the speed of implementing screens or secondary integrations.

## Project Objective

Build a professional restaurant reservation and intelligent table planning application capable of evolving into a marketable multi-restaurant product.

## Team Priorities

1. Protect the quality of the domain and the algorithm.
2. Maintain consistency across the backend, frontend, and database.
3. Avoid unnecessary complexity in the early stages.
4. Document important decisions before scaling implementation.

## Working Rules

### 1. Do Not Introduce Microservices Prematurely

The system will be based on a modular monolith. Do not split services unless an explicit, documented decision has been made.

### 2. Do Not Replace the Algorithm with AI

AI may explain, summarize, or suggest. It must never be the primary source of table assignments.

### 3. Every Business Module Must Support Multi-Tenancy

Business entities must include `restaurant_id` where applicable. Queries and permissions must be filtered by restaurant.

### 4. Do Not Implement Workflows Without Traceability

Important state changes must leave an audit trail of:

- who performed the action
- when it was performed
- which entity it affected
- the context in which it was performed

### 5. Prioritize Explainability

Every relevant automated decision must be justifiable.

### 6. Do Not Hard-Code Rules in the UI

Business logic and validation belong in the backend. The frontend must consume capabilities and rules, not invent them.

## Recommended Implementation Order

1. foundational documentation
2. repository structure
3. data model and migrations
4. authentication and permissions
5. restaurant configuration
6. reservations and customers
7. assignment algorithm
8. visual planning
9. real-time functionality
10. external integrations

## Architecture Conventions

### Backend

- Java 21
- Spring Boot
- domain-based modularization
- small, clear application services
- entities and repositories by module
- business rules outside controllers

### Frontend

- React with TypeScript
- feature-based structure
- server state with TanStack Query
- tablet-oriented UI for fast operation

### Database

- PostgreSQL as the source of truth
- Flyway for migrations
- `jsonb` only when it provides genuine flexibility

## Change Conventions

- do not mix large refactors with new features when avoidable
- do not break foundational documents without updating related references
- if the algorithm changes, also update `ALGORITHM.md`
- if the data model changes, update `DATABASE.md`
- if a structural decision changes, update `ARCHITECTURE.md`

## Quality Conventions

- prefer small, reviewable changes
- write tests for critical logic
- take particular care to protect temporal and assignment calculations
- assess the impact of changes on permissions and multi-tenancy

## Risks to Monitor

- a trivial algorithm that does not provide real value
- contradictory rules without validation
- overlapping reservations caused by temporal errors
- an overly complex planning UI introduced too early
- excessive dependence on external integrations

## Indicative Definition of Done

A change is truly complete when it:

- meets the functional need
- respects restaurant isolation
- maintains traceability
- includes reasonable validations
- updates relevant documentation if it changes the design

## Required Reference Documents

- [README.md](./README.md)
- [ARCHITECTURE.md](./ARCHITECTURE.md)
- [DATABASE.md](./DATABASE.md)
- [ALGORITHM.md](./ALGORITHM.md)
- [API.md](./API.md)
- [SECURITY.md](./SECURITY.md)
- [TESTING.md](./TESTING.md)
- [DEPLOYMENT.md](./DEPLOYMENT.md)
- [ROADMAP.md](./ROADMAP.md)

## Final Instruction for Agents

Before implementing any major module, check whether the decision is already defined in these documents. If it is not, document it first or explicitly record the decision as pending.
