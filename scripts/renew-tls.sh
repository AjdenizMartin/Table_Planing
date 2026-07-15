#!/bin/sh
set -eu

: "${APP_DOMAIN:?APP_DOMAIN is required}"

cert_dir="${LETSENCRYPT_DIR:-./deploy/letsencrypt}"
webroot_dir="${ACME_WEBROOT_DIR:-./deploy/certbot-www}"
env_file="${PRODUCTION_ENV_FILE:-.env.production}"
compose_file="${PRODUCTION_COMPOSE_FILE:-docker-compose.prod.yml}"

absolute_path() {
  case "$1" in
    /*) printf '%s\n' "$1" ;;
    *) printf '%s/%s\n' "$(pwd)" "$1" ;;
  esac
}

cert_mount="$(absolute_path "$cert_dir")"
webroot_mount="$(absolute_path "$webroot_dir")"
mkdir -p "$cert_mount" "$webroot_mount"

docker run --rm \
  -v "$cert_mount:/etc/letsencrypt" \
  -v "$webroot_mount:/var/www/certbot" \
  certbot/certbot:latest renew \
  --webroot \
  --webroot-path /var/www/certbot \
  --quiet

docker compose --env-file "$env_file" -f "$compose_file" exec -T frontend nginx -s reload
echo "TLS renewal check completed for $APP_DOMAIN"
