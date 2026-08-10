# Restaurant Table Planning

Professional restaurant reservation and intelligent table planning platform.

## Vision

This project aims to solve a real operational problem: many reservation systems accept requests but assign tables too simplistically. This results in empty tables, poor use of large tables, limited flexibility for future reservations, and a suboptimal daily plan.

Restaurant Table Planning is conceived as a marketable product for restaurants that need to:

- Manage reservations centrally.
- Configure dining rooms, tables, and combinations without modifying code.
- Automatically assign the best table or table combination.
- Maintain a real-time visual and operational plan.
- Improve occupancy, turnover, and service quality.

## Product Objective

The application must not be limited to storing reservations. It must operate as a decision engine that evaluates each new reservation within the full service context:

- party size
- time and estimated duration
- cleaning time
- available tables and possible combinations
- restaurant rules
- impact on future reservations
- risk of creating unusable gaps
- accessibility and preferences

## Value Proposition

- Better use of restaurant space.
- Less wasted capacity.
- Greater operational control for managers and front-of-house staff.
- Flexible tablet-based configuration.
- A technology foundation ready to scale to multiple restaurants.

## Target Stack

### Backend

- Java 21
- Spring Boot
- Spring Web
- Spring Data JPA
- Spring Security
- Spring Validation
- Spring WebSocket
- Spring Scheduler
- Spring AI
- Flyway

### Database and Infrastructure

- PostgreSQL
- Redis, optional and recommended
- Docker
- Docker Compose
- Nginx

### Frontend

- React
- TypeScript
- Vite
- Tailwind CSS
- React Router
- TanStack Query
- dnd-kit
- PWA

### Planned Integrations

- Twilio SMS
- WhatsApp Cloud API
- future web and social channels

## Current Status

The project is no longer solely in the documentation phase. It currently includes an initial functional foundation with:

- `Spring Boot` backend with JWT authentication, multi-tenancy, and initial modules
- restaurant, dining room, table, and combination configuration
- customers, manual reservations, and initial automatic assignment
- advanced combinations with inventory, a manual top 3, and transactional application
- daily planning, real-time updates through `WebSocket`, and deterministic operational insights
- `React + TypeScript` frontend with login, configuration, reservations, and visual planning
- CI with backend tests, PostgreSQL, frontend, Playwright, and Docker images
- local and production Compose configurations with Nginx, HTTPS, secrets, backups, and health checks

## Key Documentation

- [ARCHITECTURE.md](./ARCHITECTURE.md)
- [DATABASE.md](./DATABASE.md)
- [ALGORITHM.md](./ALGORITHM.md)
- [API.md](./API.md)
- [SECURITY.md](./SECURITY.md)
- [TESTING.md](./TESTING.md)
- [DEPLOYMENT.md](./DEPLOYMENT.md)
- [ROADMAP.md](./ROADMAP.md)
- [AGENTS.md](./AGENTS.md)
- [docs/PROJECT_AUDIT.md](./docs/PROJECT_AUDIT.md)
- [docs/TECHNICAL_TODO.md](./docs/TECHNICAL_TODO.md)
- [docs/PLANNING_PANEL_VISION.md](./docs/PLANNING_PANEL_VISION.md)
- [docs/PLANNING_PANEL_IMPLEMENTATION_PLAN.md](./docs/PLANNING_PANEL_IMPLEMENTATION_PLAN.md)
- [docs/PILOT_RUNBOOK.md](./docs/PILOT_RUNBOOK.md)
- [docs/PILOT_UAT_CHECKLIST.md](./docs/PILOT_UAT_CHECKLIST.md)

## Construction Principles

- Modular monolith before microservices.
- Deterministic engine for table assignment.
- AI only for explanatory and analytical support.
- Logical multi-tenancy from the start.
- Traceability of decisions and changes.
- UX designed for tablets and real front-of-house operations.

## Implemented Operational MVP

The current foundation already covers a significant portion of the MVP:

- authentication and roles
- restaurant configuration
- dining rooms, tables, and combinations
- manual reservations
- initial automatic assignment
- daily visual planning
- basic SMS confirmations
- advanced manager-approved suggestions with reserved inventory
- assignment history and explanations

Pending for a more mature phase:

- deeper operational automation
- additional external integrations
- algorithm refinement and advanced analytics
- data loading and UAT on the pilot's actual VPS

## Quick Start

### Requirements

- Docker Desktop or a running Docker daemon
- or, alternatively, `Java 21`, `Maven`, and `Node.js 20+`

### Implemented vs. Planned Status

Currently implemented:

- JWT authentication with refresh token
- restaurant, dining rooms, tables, and combinations
- customers and manual reservations
- daily planning
- deterministic automatic assignment
- real-time events
- internal notifications and basic SMS
- explanatory planning insights
- manual top 3 with resources, cost, and setup
- Vitest/Testing Library and Playwright tests at 768/1024 px
- administrative onboarding without a public endpoint and backup/TLS operations for the pilot

Planned or partial:

- dedicated availability as a separate module/API
- planning simulation
- advanced drag and drop
- WhatsApp and external channels
- Redis
- Spring AI / external AI provider

### Expected Local URLs

- Frontend: `http://localhost:5173`
- Backend API: `http://localhost:8080`
- Healthcheck: `http://localhost:8080/actuator/health`
- Technical ping: `http://localhost:8080/api/system/ping`

## Repository Structure

```text
.
├── backend/
├── frontend/
├── docker/
├── README.md
├── ARCHITECTURE.md
├── DATABASE.md
├── ALGORITHM.md
├── API.md
├── SECURITY.md
├── TESTING.md
├── DEPLOYMENT.md
├── ROADMAP.md
└── AGENTS.md
```

## Local Demo

The local demo is prepared for direct access with an owner user and minimal operational data. Under the `dev` profile, the backend idempotently creates the demo restaurant, dining rooms, tables, combinations, customers, and reservations for the day.

### Demo Credentials

- email: `demo@restaurant.com`
- password: `Demo1234!`
- role: `RESTAURANT_OWNER`
- restaurant: `Demo Restaurant`

### Included Demo Data

- Dining rooms: `Main Dining Room`, `Side Dining Room`, `Upper Dining Room`
- Tables: `Table 1` through `Table 7`, with capacities of 2, 4, and 6 guests
- Combinations: `Table 1 + Table 2` and `Table 3 + Table 4`
- Customers: `John Smith`, `Maria Garcia`, `David Murphy`
- Reservations for the current day with a mix of pending and confirmed statuses

### Option 1. Database Only with Docker

```bash
docker compose up postgres
```

This mode is useful if you want to start the backend from your machine. PostgreSQL is used internally in Docker Compose; if you want to connect external tools to port 5432, first check that the port is not already in use on your system.

### Option 2. Full Stack with Docker

```bash
docker compose up --build
```

Then open:

- Frontend: `http://localhost:5173`
- Backend: `http://localhost:8080`
- Health: `http://localhost:8080/actuator/health`
- Ping: `http://localhost:8080/api/system/ping`

### Option 3. Hybrid Development

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

For hybrid development, the frontend uses `VITE_API_BASE_URL=http://localhost:8080`. If the backend does not respond, the login page will display a message asking you to check Docker, port 8080, or CORS.

### Common Errors

- If Docker reports that a port is in use, stop the container/process using it or change the port in a local `.env` file.
- If login fails due to incorrect credentials, use exactly `demo@restaurant.com` and `Demo1234!`.
- If the browser displays a CORS error, confirm that the backend is running and that the frontend is opened from `http://localhost:5173`.
- If `health` appears as `DOWN`, check the backend logs and the PostgreSQL connection.
- If you previously tested with old data and want a clean demo, you can recreate the local database with `docker compose down -v` and then `docker compose up --build`. This deletes the local data from the Docker volume.

## Basic Startup

### Option 1. Database Only with Docker

```bash
docker compose up postgres
```

### Option 2. Full Stack with Docker

```bash
docker compose up --build
```

If Docker is not running, this command will fail. On macOS, this usually means that Docker Desktop is not open or the Docker socket is unavailable.

Local demo credentials under the `dev` profile:

- email: `demo@restaurant.com`
- password: `Demo1234!`

### Option 3. Hybrid Development

Backend:

```bash
cd backend
mvn spring-boot:run
```

Frontend:

```bash
cd frontend
npm install
npm run dev
```

## Environment Notes

- The backend is configured for `Java 21`.
- Recent project validation confirms:
  - `mvn -q -DskipTests package` succeeds in `backend`
  - `npm run build` succeeds in `frontend`
- If the project does not start locally, first check that Docker Desktop is running.
- PostgreSQL does not need to be exposed to the host for the application to work within Docker Compose.
