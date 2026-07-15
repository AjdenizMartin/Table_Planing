# Technical TODO

## Pilot blockers

- Provision the Ubuntu VPS, DNS and S3-compatible backup repository.
- Load and review the real restaurant configuration and individual accounts.
- Validate Chrome on a physical Android tablet, including reconnect and rotation.
- Run performance, restore, rollback and signed UAT on the pilot environment.

## Post-pilot high priority

- Add authenticated owner-facing team administration and password reset flows.
- Review JSONB persistence and standardize typed serialization.
- Add long-term metric aggregation and dashboards beyond the pilot operations check and webhook alert.
- Expand route-level loading, conflict and offline messaging in the frontend.

## Medium

- Improve tablet ergonomics in reservations and the layout editor.
- Document realtime event payloads and topics in more detail.
- Review whether `spring-boot-starter-mail` matches the notification roadmap.
- Add a dedicated availability API if an external booking channel is introduced.

## Future

- WhatsApp Cloud API and external reservation channels.
- Advanced planning simulation and controlled reassignment.
- Redis only when measured load requires cache or distributed coordination.
- External AI integration for explanation and analysis, never primary assignment.
