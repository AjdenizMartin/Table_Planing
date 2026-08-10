# Deployment

## Objective

Deploy the modular monolith on a single VPS for the pilot, with persistent PostgreSQL, a static frontend, HTTPS, and documented recovery.

## Environments

### Development

`docker-compose.yml` starts PostgreSQL, the backend with the `dev` profile, and Vite. It exposes the frontend on `5173` and the backend on `8080`. The demo bootstrap exists only in this profile.

```bash
docker compose up --build
```

### Pilot

`docker-compose.prod.yml` contains:

- PostgreSQL 16 with no public port and a persistent volume.
- Spring Boot backend with the `prod` profile, a read-only filesystem, health check, and mounted secrets. The entrypoint uses minimal capabilities to read them and immediately switches to the unprivileged user without retaining capabilities.
- frontend built with Node and served by Nginx.
- same-origin proxy for `/api` and `/ws`.
- HTTP redirected to HTTPS, TLS 1.2/1.3, and HSTS.
- login rate limiting and no public registration in either the production backend or frontend.
- CSP and headers against framing, sniffing, and access to the camera, microphone, or geolocation.
- Nginx as the only service on the perimeter network; backend and PostgreSQL exclusively on the internal network.
- capabilities removed before running Java in the backend, log rotation, and `unless-stopped` restart policy.

## Configuration

- `.env.production.example`: variable names without secrets.
- `secrets/postgres_password.txt`: PostgreSQL password, ignored by Git.
- `secrets/jwt_secret.txt`: long random JWT secret, ignored by Git.
- `secrets/restic_password.txt` and S3 credentials: encryption and access to the off-site backup.
- `frontend/nginx/default.conf.template`: TLS and proxy.
- `frontend/Dockerfile.prod`: multi-stage static build.

Do not use the demo values or the development Compose file on the VPS.

## Initial deployment

1. Install Docker Engine, the Compose plugin, `jq`, and `restic`, and configure the firewall for SSH, 80, and 443.
2. Create DNS records for the pilot domain.
3. Check out the tag specified by `PILOT_RELEASE_TAG` and prepare `.env.production` with `0600` permissions and independent secrets.
4. Run the OS, tag, DNS, secrets, disk, ports, and Compose preflight check:

```bash
./scripts/production-preflight.sh
```

5. Obtain the certificate with `scripts/bootstrap-tls.sh`.
6. Validate the configuration:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml config --quiet
```

7. Build and start:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
```

8. Verify status:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml ps
curl --fail https://reservas.example.com/api/system/ping
```

9. Run the administrative onboarding described in `docs/PILOT_RUNBOOK.md`. Do not temporarily enable `/api/auth/register` or insert users through SQL.

Flyway applies V1-V17 when the backend starts. The backend is not considered healthy until migrations finish and it responds at `/actuator/health` within the network.

## Update

1. Create a pre-update backup.
2. Download the revision approved by CI.
3. Build the new images.
4. Restart the backend and verify health/migrations.
5. Restart the frontend.
6. Run manager/staff smoke tests and performance tests.

Do not combine an image update with manual schema changes.

## Backup and restoration

`scripts/backup-postgres.sh` atomically generates a custom dump with private permissions and removes expired local copies. `scripts/backup-offsite.sh` encrypts and replicates those dumps through Restic to S3-compatible storage.

`scripts/restore-postgres.sh <backup.dump>` stops the backend, restores with `pg_restore --clean --if-exists`, and starts it again. Every restoration requires a maintenance window and subsequent functional verification.

The complete procedure, TLS renewal, incident handling, and rollback are documented in [docs/PILOT_RUNBOOK.md](./docs/PILOT_RUNBOOK.md).

## Minimum observability

- Docker health checks for the database, backend, and frontend
- rotated PostgreSQL, Spring Boot, and Nginx logs through `docker compose logs`
- `scripts/pilot-ops-check.sh` for containers, HTTPS, blocked registration, TLS, disk, and local/off-site backups
- optional webhook alert through `OPS_ALERT_WEBHOOK_URL_FILE`

The operational check is enabled after creating and verifying the first off-site backup; before then, it must fail to prevent a false sense of recoverability.

Actuator is not exposed externally; backend health is queried within Compose.

## Android clients

Ubuntu runs the server. Android tablets access the HTTPS URL through Chrome and do not need Docker or an APK. The pilot requires validation on a real device for touch input, virtual keyboard, rotation, time zone, and WebSocket reconnection. There is no offline support in this version.

## Pilot limitations

- one VPS and one persistent PostgreSQL instance
- no Kubernetes, Redis, microservices, or multi-region deployment
- recovery through backup/restore, not high availability
- real SMS disabled until a provider and dedicated secrets are configured
