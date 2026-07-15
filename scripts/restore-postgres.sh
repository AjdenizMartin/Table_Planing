#!/bin/sh
set -eu

if [ "$#" -ne 1 ] || [ ! -r "$1" ]; then
  echo "Usage: $0 <backup.dump>" >&2
  exit 1
fi

: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"

backup_path="$1"
compose="docker compose --env-file .env.production -f docker-compose.prod.yml"
backend_stopped=0

restart_backend() {
  if [ "$backend_stopped" -eq 1 ]; then
    $compose start backend > /dev/null
    backend_stopped=0
  fi
}
trap restart_backend EXIT
trap 'exit 1' INT TERM

$compose exec -T postgres pg_restore --list < "$backup_path" > /dev/null
$compose stop backend
backend_stopped=1
$compose exec -T postgres pg_restore \
  -U "$POSTGRES_USER" \
  -d "$POSTGRES_DB" \
  --clean --if-exists --exit-on-error --no-owner --no-acl < "$backup_path"
$compose start backend
backend_stopped=0
trap - EXIT INT TERM

echo "Restore completed from: $backup_path"
