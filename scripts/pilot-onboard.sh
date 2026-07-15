#!/bin/sh
set -eu

onboarding_dir="${PILOT_ONBOARDING_DIR:-./onboarding}"
manifest="$onboarding_dir/manifest.json"
env_file="${PRODUCTION_ENV_FILE:-.env.production}"
compose_file="${PRODUCTION_COMPOSE_FILE:-docker-compose.prod.yml}"

if [ ! -r "$manifest" ]; then
  echo "Onboarding manifest is not readable: $manifest" >&2
  exit 1
fi

case "$onboarding_dir" in
  /*) onboarding_mount="$onboarding_dir" ;;
  *) onboarding_mount="$(pwd)/$onboarding_dir" ;;
esac

docker compose --env-file "$env_file" -f "$compose_file" run \
  --rm \
  --no-deps \
  --user "$(id -u):$(id -g)" \
  -e SPRING_PROFILES_ACTIVE=prod,onboarding \
  -e APP_ONBOARDING_MANIFEST=/run/onboarding/manifest.json \
  -e SERVER_PORT=0 \
  -v "$onboarding_mount:/run/onboarding:ro" \
  backend
