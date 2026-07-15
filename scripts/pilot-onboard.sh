#!/bin/sh
set -eu

onboarding_dir="${PILOT_ONBOARDING_DIR:-./onboarding}"
manifest="$onboarding_dir/manifest.json"

if [ ! -r "$manifest" ]; then
  echo "Onboarding manifest is not readable: $manifest" >&2
  exit 1
fi

case "$onboarding_dir" in
  /*) onboarding_mount="$onboarding_dir" ;;
  *) onboarding_mount="$(pwd)/$onboarding_dir" ;;
esac

docker compose --env-file .env.production -f docker-compose.prod.yml run \
  --rm \
  --no-deps \
  -e SPRING_PROFILES_ACTIVE=prod,onboarding \
  -e APP_ONBOARDING_MANIFEST=/run/onboarding/manifest.json \
  -e SERVER_PORT=0 \
  -v "$onboarding_mount:/run/onboarding:ro" \
  backend
