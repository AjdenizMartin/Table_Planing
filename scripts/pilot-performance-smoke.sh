#!/bin/sh
set -eu

: "${PILOT_BASE_URL:?PILOT_BASE_URL is required}"
: "${PILOT_RESTAURANT_ID:?PILOT_RESTAURANT_ID is required}"
: "${PILOT_RESERVATION_ID:?PILOT_RESERVATION_ID is required}"
: "${PILOT_DATE:?PILOT_DATE is required (YYYY-MM-DD)}"

if [ -z "${PILOT_ACCESS_TOKEN:-}" ]; then
  : "${PILOT_OWNER_EMAIL:?PILOT_OWNER_EMAIL is required when PILOT_ACCESS_TOKEN is absent}"
  : "${PILOT_OWNER_PASSWORD_FILE:?PILOT_OWNER_PASSWORD_FILE is required when PILOT_ACCESS_TOKEN is absent}"
  if ! command -v jq > /dev/null 2>&1; then
    echo "jq is required to obtain a pilot access token" >&2
    exit 1
  fi
  if [ ! -r "$PILOT_OWNER_PASSWORD_FILE" ]; then
    echo "Owner password file is not readable" >&2
    exit 1
  fi
  password="$(cat "$PILOT_OWNER_PASSWORD_FILE")"
  login_payload="$(jq -n --arg email "$PILOT_OWNER_EMAIL" --arg password "$password" \
    '{email: $email, password: $password}')"
  PILOT_ACCESS_TOKEN="$(curl --fail --silent --show-error \
    --header 'Content-Type: application/json' \
    --data "$login_payload" \
    "${PILOT_BASE_URL%/}/api/auth/login" | jq -er '.accessToken')"
  unset password login_payload
fi

planning_limit="${PLANNING_LIMIT_SECONDS:-2.0}"
suggestion_limit="${SUGGESTION_LIMIT_SECONDS:-1.0}"
runs="${PERFORMANCE_RUNS:-10}"
PILOT_BASE_URL="${PILOT_BASE_URL%/}"

measure() {
  label="$1"
  path="$2"
  limit="$3"
  index=1

  while [ "$index" -le "$runs" ]; do
    elapsed="$(curl --fail --silent --show-error --output /dev/null \
      --header "Authorization: Bearer $PILOT_ACCESS_TOKEN" \
      --write-out '%{time_total}' \
      "$PILOT_BASE_URL$path")"
    awk -v label="$label" -v elapsed="$elapsed" -v limit="$limit" \
      'BEGIN { printf "%s %.3fs (limit %.3fs)\n", label, elapsed, limit; if (elapsed > limit) exit 1 }'
    index=$((index + 1))
  done
}

measure "planning" "/api/restaurants/$PILOT_RESTAURANT_ID/planning?date=$PILOT_DATE" "$planning_limit"
measure "suggestions" "/api/restaurants/$PILOT_RESTAURANT_ID/reservations/$PILOT_RESERVATION_ID/assignment-suggestions" "$suggestion_limit"

echo "Performance smoke passed ($runs runs per endpoint)."
