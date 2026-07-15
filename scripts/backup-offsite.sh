#!/bin/sh
set -eu
umask 077

: "${RESTIC_REPOSITORY:?RESTIC_REPOSITORY is required}"
: "${RESTIC_PASSWORD_FILE:?RESTIC_PASSWORD_FILE is required}"
: "${AWS_ACCESS_KEY_ID_FILE:?AWS_ACCESS_KEY_ID_FILE is required}"
: "${AWS_SECRET_ACCESS_KEY_FILE:?AWS_SECRET_ACCESS_KEY_FILE is required}"

backup_dir="${BACKUP_DIR:-./backups}"
keep_daily="${RESTIC_KEEP_DAILY:-14}"
keep_weekly="${RESTIC_KEEP_WEEKLY:-8}"
keep_monthly="${RESTIC_KEEP_MONTHLY:-12}"

if ! command -v restic > /dev/null 2>&1; then
  echo "restic is required for offsite backups" >&2
  exit 1
fi

for secret_file in "$RESTIC_PASSWORD_FILE" "$AWS_ACCESS_KEY_ID_FILE" "$AWS_SECRET_ACCESS_KEY_FILE"; do
  if [ ! -r "$secret_file" ]; then
    echo "Backup secret is not readable: $secret_file" >&2
    exit 1
  fi
done

if ! find "$backup_dir" -type f -name '*.dump' -size +0c -print -quit | grep -q .; then
  echo "No PostgreSQL dump is available for offsite backup" >&2
  exit 1
fi

export AWS_ACCESS_KEY_ID="$(cat "$AWS_ACCESS_KEY_ID_FILE")"
export AWS_SECRET_ACCESS_KEY="$(cat "$AWS_SECRET_ACCESS_KEY_FILE")"

restic backup "$backup_dir" --tag postgresql --tag table-planning-pilot
restic forget \
  --tag table-planning-pilot \
  --keep-daily "$keep_daily" \
  --keep-weekly "$keep_weekly" \
  --keep-monthly "$keep_monthly" \
  --prune
restic check

echo "Encrypted offsite backup completed"
