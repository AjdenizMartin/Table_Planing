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

staging_volume="table-planning-onboarding-$(id -u)-$$"
cleanup() {
  docker volume rm -f "$staging_volume" > /dev/null 2>&1 || true
}
trap cleanup EXIT HUP INT TERM

docker volume create "$staging_volume" > /dev/null
docker compose --env-file "$env_file" -f "$compose_file" run \
  --rm \
  --no-deps \
  --user 0:0 \
  --cap-add CHOWN \
  --entrypoint /bin/sh \
  -v "$onboarding_mount:/source:ro" \
  -v "$staging_volume:/target" \
  backend \
  -c '
    set -eu
    cp /source/manifest.json /target/manifest.json
    for password_file in /source/*_password.txt; do
      [ -e "$password_file" ] || continue
      cp "$password_file" "/target/$(basename "$password_file")"
    done
    find /target -type d -exec chmod 700 {} +
    find /target -type f -exec chmod 600 {} +
    chown -R 10001:10001 /target
  '

docker compose --env-file "$env_file" -f "$compose_file" run \
  --rm \
  --no-deps \
  -e SPRING_PROFILES_ACTIVE=prod,onboarding \
  -e APP_ONBOARDING_MANIFEST=/run/onboarding/manifest.json \
  -e SERVER_PORT=0 \
  -v "$staging_volume:/run/onboarding:ro" \
  backend
