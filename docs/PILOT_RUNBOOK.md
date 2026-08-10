# Pilot Runbook

## Preparation

1. Prepare Ubuntu 24.04 LTS with 2 vCPUs, 4 GB RAM, an 80 GB SSD, Docker, Compose, `jq`, and `restic`.
2. Restrict SSH access to key-based authentication and expose only ports 80/443.
3. Create a DNS `A` record for `APP_DOMAIN` pointing to the VPS.
4. Copy `.env.production.example` to `.env.production`, complete the domain, tag, email, paths, and Restic repository, and run `chmod 600 .env.production`.
5. Create all files under `secrets/` with random values and `0600` permissions.
6. Create `logs/`, check out `v0.1.0-rc.6`, and do not deploy a floating branch.
7. Run `./scripts/production-preflight.sh`; it must validate Ubuntu, the tag, DNS, secrets, disk, ports, and Compose.
8. Export `.env.production` and run `scripts/bootstrap-tls.sh` before the initial startup.
9. Validate the Compose configuration and start the approved tag:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml config --quiet
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
docker compose --env-file .env.production -f docker-compose.prod.yml ps
curl --fail "https://$APP_DOMAIN/api/system/ping"
```

Production does not expose PostgreSQL or the backend, does not load demo data, and Nginx returns `404` for public registration.

## Administrative onboarding

Initial provisioning does not use a public API. Prepare a Git-ignored directory:

```bash
mkdir -p onboarding
cp docs/pilot-onboarding.example.json onboarding/manifest.json
chmod 600 onboarding/manifest.json
for role in owner manager staff; do
  { printf 'Aa1!'; openssl rand -base64 24 | tr -d '\n'; printf '\n'; } > "onboarding/${role}_password.txt"
  chmod 600 "onboarding/${role}_password.txt"
done
```

Edit names and email addresses, preserving the `/run/onboarding/...` paths within the manifest. Run:

```bash
./scripts/pilot-onboard.sh
```

The command copies the `0600` files to a private, ephemeral Docker volume owned by UID `10001`, uses `prod,onboarding`, and removes the volume upon completion. It validates a single owner, permitted roles, time zone, conflicts, and permissions. It accepts `RESTAURANT_OWNER`, `MANAGER`, `WAITER`, and the `STAFF` alias, which is persisted as `WAITER`. The operation is transactional, does not reset existing passwords, and audits the actor, users, and restaurant. A repeated run may omit `passwordFile` for users who have already been verified.

Then load the following through the UI in this order: dining areas, 40 tables, inventory, standard combinations, 20 total combinations, and test reservations. Confirm with all three accounts that staff (`WAITER`) cannot approve suggestions.

## Off-site backup

Initialize the encrypted repository once:

```bash
set -a && . ./.env.production && set +a
export AWS_ACCESS_KEY_ID="$(cat "$AWS_ACCESS_KEY_ID_FILE")"
export AWS_SECRET_ACCESS_KEY="$(cat "$AWS_SECRET_ACCESS_KEY_FILE")"
restic init
```

Run daily from cron:

```text
15 3 * * * cd /opt/table-planning && set -a && . ./.env.production && set +a && ./scripts/backup-postgres.sh && ./scripts/backup-offsite.sh >> logs/backup.log 2>&1
```

The default retention policy is 14 daily, 8 weekly, and 12 monthly snapshots. A copy on the same VPS does not qualify as a recovery backup.

## Operational observability

After creating the first local and off-site backups, run `./scripts/pilot-ops-check.sh`. It checks all three containers, HTTPS, blocked registration, TLS expiration, disk space, and local/Restic backups. Configure `OPS_ALERT_WEBHOOK_URL_FILE` with a `0600` file to report failures without including secrets.

Run hourly and log the output:

```text
5 * * * * cd /opt/table-planning && set -a && . ./.env.production && set +a && ./scripts/pilot-ops-check.sh >> logs/ops-check.log 2>&1
```

## Performance

Create a baseline backup first. The load process deliberately mutates data, which must be restored upon completion:

```bash
export PILOT_BASE_URL="https://$APP_DOMAIN"
export PILOT_OWNER_EMAIL="owner@example.com"
export PILOT_OWNER_PASSWORD_FILE="./onboarding/owner_password.txt"
export PILOT_RESTAURANT_ID="<id>"
export PILOT_DATE="<YYYY-MM-DD>"
export PILOT_FIXTURE_CONFIRM=CREATE_SYNTHETIC_DATA
./scripts/pilot-load-fixture.sh
set -a && . ./pilot-fixture-result.env && set +a
./scripts/pilot-performance-smoke.sh
```

The gate requires ten planning responses under 2 seconds and ten suggestion responses under 1 second. Restore the baseline backup after saving the results.

## Restoration

1. Enable the maintenance window and export `.env.production`.
2. Run `./scripts/restore-postgres.sh backups/<file>.dump`.
3. Confirm health, manager login, and restaurant, reservation, and assignment counts.
4. Record the date, operator, backup, and result.

Test restoration before UAT and monthly during the pilot.

## TLS renewal

The challenge uses the webroot served by Nginx. Schedule a daily check:

```text
20 4 * * * cd /opt/table-planning && set -a && . ./.env.production && set +a && ./scripts/renew-tls.sh >> logs/tls.log 2>&1
```

Also maintain an external alert at least 14 days before expiration.

## Rollback

1. Stop the frontend and backend; keep PostgreSQL running.
2. Revert to the previous tag.
3. If the migrations are incompatible, restore the previous backup.
4. Start the backend, check Flyway/health, and then start the frontend.
5. Run manager and staff smoke tests. Never edit `flyway_schema_history`.

## Incidents

- Exhausted inventory: refresh suggestions; do not force changes through SQL.
- Concurrent conflict: the second selection receives `409`; reload and choose another option.
- Unhealthy backend: review logs and internal connectivity to PostgreSQL.
- Realtime unavailable: REST remains valid; reload planning while WebSocket connectivity recovers.
- Android offline: revert to the manual procedure; this version does not provide an offline mode.
