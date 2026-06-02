# Technical TODO

## Critical

- Stabilize and publish the current local backend fixes before they diverge from `main`
- Review `/actuator/health` and make sure health status reflects real service readiness
- Add regression tests for tenant isolation on all restaurant-scoped modules
- Add focused unit/integration tests for reservation assignment and overlap handling

## High

- Align `API.md` with the actual implemented endpoints and mark future endpoints explicitly
- Align `DATABASE.md` with real Flyway migrations, especially `notification`, `scheduled_notification`, `notification_log` and `ai_insight`
- Align `ALGORITHM.md` with the currently implemented optimization version
- Decide whether to formalize a dedicated onboarding/seed strategy for first local login
- Review JSONB persistence strategy and standardize it

## Medium

- Add `.env` usage guidance to `README.md` and `DEPLOYMENT.md`
- Improve tablet ergonomics in planning, reservations and layout editor
- Add clearer documentation for realtime events and topics
- Review whether `spring-boot-starter-mail` is still the right choice for the current notification scope
- Add a dedicated availability API or mark it explicitly as pending everywhere

## Low

- Add route-level loading and error handling polish in frontend
- Review whether `react-router` future flags should be enabled intentionally
- Review frontend dependencies promised in docs but not yet used, such as `dnd-kit`

## Future

- WhatsApp Cloud API
- Google / social channel reservation ingestion
- Advanced planning simulation
- Multi-step optimization and controlled reassignment
- Redis for cache/locks/realtime helpers
- External AI provider integration for explanatory analysis only
