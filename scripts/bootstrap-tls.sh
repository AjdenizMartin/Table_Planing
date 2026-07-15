#!/bin/sh
set -eu

: "${APP_DOMAIN:?APP_DOMAIN is required}"
: "${TLS_EMAIL:?TLS_EMAIL is required}"

cert_dir="${LETSENCRYPT_DIR:-./deploy/letsencrypt}"

case "$cert_dir" in
  /*) cert_mount="$cert_dir" ;;
  *) cert_mount="$(pwd)/$cert_dir" ;;
esac
mkdir -p "$cert_mount"

docker run --rm \
  -p 80:80 \
  -v "$cert_mount:/etc/letsencrypt" \
  certbot/certbot:latest certonly \
  --standalone \
  --non-interactive \
  --agree-tos \
  --email "$TLS_EMAIL" \
  -d "$APP_DOMAIN"
