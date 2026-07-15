#!/bin/sh
set -eu
umask 077

: "${POSTGRES_DB:?POSTGRES_DB is required}"
: "${POSTGRES_USER:?POSTGRES_USER is required}"

backup_dir="${BACKUP_DIR:-./backups}"
retention_days="${BACKUP_RETENTION_DAYS:-14}"
timestamp="$(date -u +%Y%m%dT%H%M%SZ)"
backup_path="$backup_dir/${POSTGRES_DB}_${timestamp}.dump"
temporary_path="$backup_path.tmp"

cleanup() {
  rm -f "$temporary_path"
}
trap cleanup EXIT
trap 'exit 1' INT TERM

mkdir -p "$backup_dir"
docker compose --env-file .env.production -f docker-compose.prod.yml exec -T postgres \
  pg_dump -U "$POSTGRES_USER" -d "$POSTGRES_DB" --format=custom --no-owner --no-acl > "$temporary_path"

if [ ! -s "$temporary_path" ]; then
  echo "Backup failed: generated dump is empty" >&2
  exit 1
fi

mv "$temporary_path" "$backup_path"

find "$backup_dir" -type f -name "${POSTGRES_DB}_*.dump" -mtime "+$retention_days" -delete
echo "Backup created: $backup_path"
