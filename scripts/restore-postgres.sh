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

$compose stop backend
$compose exec -T postgres pg_restore \
  -U "$POSTGRES_USER" \
  -d "$POSTGRES_DB" \
  --clean --if-exists --no-owner --no-acl < "$backup_path"
$compose start backend

echo "Restore completed from: $backup_path"
