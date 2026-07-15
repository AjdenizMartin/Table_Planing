#!/bin/sh
set -eu

env_file="${1:-.env.production}"
compose_file="${PRODUCTION_COMPOSE_FILE:-docker-compose.prod.yml}"
case "$env_file" in
  /*) ;;
  *) env_file="$(pwd)/$env_file" ;;
esac

if [ ! -f "$env_file" ] || [ -L "$env_file" ]; then
  echo "Production environment file must be a regular non-symlink file: $env_file" >&2
  exit 1
fi

if [ "$(stat -c '%a' "$env_file")" != "600" ]; then
  echo "Production environment file must have permissions 0600: $env_file" >&2
  exit 1
fi

set -a
. "$env_file"
set +a

require_command() {
  if ! command -v "$1" > /dev/null 2>&1; then
    echo "Required command is missing: $1" >&2
    exit 1
  fi
}

for command_name in docker git getent openssl curl jq restic stat ss; do
  require_command "$command_name"
done

if [ "${PILOT_PREFLIGHT_SKIP_OS_CHECK:-false}" != "true" ]; then
  if [ ! -r /etc/os-release ]; then
    echo "Cannot verify the operating system" >&2
    exit 1
  fi
  . /etc/os-release
  if [ "${ID:-}" != "ubuntu" ] || [ "${VERSION_ID:-}" != "24.04" ]; then
    echo "Pilot requires Ubuntu 24.04 LTS; found ${ID:-unknown} ${VERSION_ID:-unknown}" >&2
    exit 1
  fi
fi

: "${PILOT_RELEASE_TAG:?PILOT_RELEASE_TAG is required}"
: "${APP_DOMAIN:?APP_DOMAIN is required}"
: "${HTTP_PORT:=80}"
: "${HTTPS_PORT:=443}"
: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"
: "${POSTGRES_PASSWORD_FILE:?POSTGRES_PASSWORD_FILE is required}"
: "${JWT_SECRET_FILE:?JWT_SECRET_FILE is required}"
: "${RESTIC_REPOSITORY:?RESTIC_REPOSITORY is required}"
: "${RESTIC_PASSWORD_FILE:?RESTIC_PASSWORD_FILE is required}"
: "${AWS_ACCESS_KEY_ID_FILE:?AWS_ACCESS_KEY_ID_FILE is required}"
: "${AWS_SECRET_ACCESS_KEY_FILE:?AWS_SECRET_ACCESS_KEY_FILE is required}"

case "$APP_DOMAIN" in
  *[!A-Za-z0-9.-]*|.*|*.)
    echo "APP_DOMAIN must be a hostname without a scheme or path" >&2
    exit 1
    ;;
esac
if [ "$HTTP_PORT" != "80" ] || [ "$HTTPS_PORT" != "443" ]; then
  echo "Production must publish only HTTP 80 and HTTPS 443" >&2
  exit 1
fi

if [ -n "$(git status --porcelain)" ]; then
  echo "The release checkout has uncommitted changes" >&2
  exit 1
fi
current_tag="$(git describe --tags --exact-match HEAD 2>/dev/null || true)"
if [ "$current_tag" != "$PILOT_RELEASE_TAG" ]; then
  echo "Checkout must be exactly $PILOT_RELEASE_TAG; found ${current_tag:-untagged commit}" >&2
  exit 1
fi

check_secret() {
  secret_file="$1"
  label="$2"
  minimum_bytes="$3"

  if [ ! -f "$secret_file" ] || [ -L "$secret_file" ] || [ ! -r "$secret_file" ]; then
    echo "$label must be a readable regular non-symlink file: $secret_file" >&2
    exit 1
  fi
  if [ "$(stat -c '%a' "$secret_file")" != "600" ]; then
    echo "$label must have permissions 0600: $secret_file" >&2
    exit 1
  fi
  secret_bytes="$(wc -c < "$secret_file" | tr -d ' ')"
  if [ "$secret_bytes" -lt "$minimum_bytes" ]; then
    echo "$label must contain at least $minimum_bytes bytes" >&2
    exit 1
  fi
}

check_secret "$POSTGRES_PASSWORD_FILE" "PostgreSQL password" 24
check_secret "$JWT_SECRET_FILE" "JWT secret" 64
check_secret "$RESTIC_PASSWORD_FILE" "Restic password" 24
check_secret "$AWS_ACCESS_KEY_ID_FILE" "S3 access key" 8
check_secret "$AWS_SECRET_ACCESS_KEY_FILE" "S3 secret key" 24
if [ -n "${OPS_ALERT_WEBHOOK_URL_FILE:-}" ]; then
  check_secret "$OPS_ALERT_WEBHOOK_URL_FILE" "Operations alert webhook URL" 12
fi

if ! getent ahostsv4 "$APP_DOMAIN" > /dev/null 2>&1; then
  echo "APP_DOMAIN does not resolve through DNS: $APP_DOMAIN" >&2
  exit 1
fi
if ! docker info > /dev/null 2>&1 || ! docker compose version > /dev/null 2>&1; then
  echo "Docker Engine and Docker Compose must be available" >&2
  exit 1
fi
docker compose --env-file "$env_file" -f "$compose_file" config --quiet

available_kb="$(df -Pk . | awk 'NR == 2 {print $4}')"
minimum_kb=$((20 * 1024 * 1024))
if [ "$available_kb" -lt "$minimum_kb" ]; then
  echo "At least 20 GB of free disk space is required" >&2
  exit 1
fi

if [ "${PILOT_PREFLIGHT_REQUIRE_FREE_PORTS:-true}" = "true" ]; then
  for port in 80 443; do
    if ss -ltn "sport = :$port" | awk 'NR > 1 {found = 1} END {exit !found}'; then
      echo "TCP port $port is already in use" >&2
      exit 1
    fi
  done
fi

echo "Production preflight passed for $PILOT_RELEASE_TAG on $APP_DOMAIN"
