#!/bin/sh
set -u

: "${APP_DOMAIN:?APP_DOMAIN is required}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"

env_file="${PRODUCTION_ENV_FILE:-.env.production}"
compose_file="${PRODUCTION_COMPOSE_FILE:-docker-compose.prod.yml}"
compose="docker compose --env-file $env_file -f $compose_file"
backup_dir="${BACKUP_DIR:-./backups}"
backup_max_age_minutes="${OPS_BACKUP_MAX_AGE_MINUTES:-1560}"
disk_usage_limit="${OPS_DISK_USAGE_LIMIT_PERCENT:-85}"
tls_warning_seconds="${OPS_TLS_WARNING_SECONDS:-1209600}"
https_port="${OPS_HTTPS_PORT:-443}"
curl_tls_option=""
if [ "${OPS_CURL_INSECURE:-false}" = "true" ]; then
  curl_tls_option="--insecure"
fi
if [ "$https_port" = "443" ]; then
  base_url="https://$APP_DOMAIN"
else
  base_url="https://$APP_DOMAIN:$https_port"
fi
failures=""

record_failure() {
  echo "FAIL: $1" >&2
  if [ -z "$failures" ]; then
    failures="$1"
  else
    failures="$failures; $1"
  fi
}

for service in postgres backend frontend; do
  container_id="$($compose ps -q "$service" 2>/dev/null || true)"
  if [ -z "$container_id" ]; then
    record_failure "$service container is missing"
    continue
  fi
  container_state="$(docker inspect --format '{{.State.Status}}' "$container_id" 2>/dev/null || true)"
  container_health="$(docker inspect --format '{{if .State.Health}}{{.State.Health.Status}}{{else}}none{{end}}' "$container_id" 2>/dev/null || true)"
  if [ "$container_state" != "running" ]; then
    record_failure "$service container state is ${container_state:-unknown}"
  elif [ "$container_health" != "healthy" ]; then
    record_failure "$service container health is ${container_health:-unknown}"
  fi
done

ping_status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  $curl_tls_option --max-time 10 "$base_url/api/system/ping" 2>/dev/null || true)"
if [ "$ping_status" != "200" ]; then
  record_failure "HTTPS system ping returned ${ping_status:-no response}"
fi

register_status="$(curl --silent --show-error --output /dev/null --write-out '%{http_code}' \
  $curl_tls_option --max-time 10 --request POST "$base_url/api/auth/register" 2>/dev/null || true)"
if [ "$register_status" != "404" ]; then
  record_failure "public registration returned ${register_status:-no response} instead of 404"
fi

if ! openssl s_client -connect "$APP_DOMAIN:$https_port" -servername "$APP_DOMAIN" < /dev/null 2>/dev/null \
  | openssl x509 -checkend "$tls_warning_seconds" -noout > /dev/null 2>&1; then
  record_failure "TLS certificate is unavailable or expires within the warning window"
fi

if ! find "$backup_dir" -type f -name "${POSTGRES_DB}_*.dump" -size +0c \
  -mmin "-$backup_max_age_minutes" -print -quit 2>/dev/null | grep -q .; then
  record_failure "no recent local PostgreSQL backup was found"
fi

disk_usage="$(df -Pk . | awk 'NR == 2 {gsub("%", "", $5); print $5}')"
if [ -z "$disk_usage" ] || [ "$disk_usage" -ge "$disk_usage_limit" ]; then
  record_failure "disk usage is ${disk_usage:-unknown}% (limit $disk_usage_limit%)"
fi

if [ "${OPS_CHECK_RESTIC:-true}" = "true" ]; then
  if [ -z "${RESTIC_REPOSITORY:-}" ] || [ -z "${RESTIC_PASSWORD_FILE:-}" ] \
    || [ -z "${AWS_ACCESS_KEY_ID_FILE:-}" ] || [ -z "${AWS_SECRET_ACCESS_KEY_FILE:-}" ]; then
    record_failure "Restic environment is incomplete"
  else
    export AWS_ACCESS_KEY_ID="$(cat "$AWS_ACCESS_KEY_ID_FILE" 2>/dev/null || true)"
    export AWS_SECRET_ACCESS_KEY="$(cat "$AWS_SECRET_ACCESS_KEY_FILE" 2>/dev/null || true)"
    if ! restic snapshots --latest 1 --json 2>/dev/null | jq -e 'length == 1' > /dev/null 2>&1; then
      record_failure "encrypted offsite backup repository is unavailable or empty"
    fi
  fi
fi

if [ -n "$failures" ]; then
  if [ -n "${OPS_ALERT_WEBHOOK_URL_FILE:-}" ] && [ -r "$OPS_ALERT_WEBHOOK_URL_FILE" ]; then
    webhook_url="$(cat "$OPS_ALERT_WEBHOOK_URL_FILE")"
    alert_text="Table Planning production check failed on $(hostname): $failures"
    alert_payload="$(jq -n --arg text "$alert_text" '{text: $text}')"
    curl --fail --silent --show-error --max-time 10 \
      --header 'Content-Type: application/json' \
      --data "$alert_payload" "$webhook_url" > /dev/null 2>&1 || true
  fi
  exit 1
fi

echo "Production operations check passed for $APP_DOMAIN"
