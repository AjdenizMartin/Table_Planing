#!/bin/sh
set -eu

: "${APP_DOMAIN:?APP_DOMAIN is required}"
: "${TLS_EMAIL:?TLS_EMAIL is required}"

cert_dir="${LETSENCRYPT_DIR:-./deploy/letsencrypt}"
mkdir -p "$cert_dir"

docker run --rm \
  -p 80:80 \
  -v "$(pwd)/$cert_dir:/etc/letsencrypt" \
  certbot/certbot:latest certonly \
  --standalone \
  --non-interactive \
  --agree-tos \
  --email "$TLS_EMAIL" \
  -d "$APP_DOMAIN"
