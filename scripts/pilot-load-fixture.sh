#!/bin/sh
set -eu
umask 077

: "${PILOT_BASE_URL:?PILOT_BASE_URL is required}"
: "${PILOT_OWNER_EMAIL:?PILOT_OWNER_EMAIL is required}"
: "${PILOT_OWNER_PASSWORD_FILE:?PILOT_OWNER_PASSWORD_FILE is required}"
: "${PILOT_RESTAURANT_ID:?PILOT_RESTAURANT_ID is required}"
: "${PILOT_DATE:?PILOT_DATE is required (YYYY-MM-DD)}"

if [ "${PILOT_FIXTURE_CONFIRM:-}" != "CREATE_SYNTHETIC_DATA" ]; then
  echo "Set PILOT_FIXTURE_CONFIRM=CREATE_SYNTHETIC_DATA to acknowledge database mutation" >&2
  exit 1
fi
if ! command -v jq > /dev/null 2>&1; then
  echo "jq is required to generate the pilot fixture" >&2
  exit 1
fi
if [ ! -r "$PILOT_OWNER_PASSWORD_FILE" ]; then
  echo "Owner password file is not readable" >&2
  exit 1
fi

count="${PILOT_FIXTURE_COUNT:-150}"
output_file="${PILOT_FIXTURE_OUTPUT:-./pilot-fixture-result.env}"
base_url="${PILOT_BASE_URL%/}"

case "$count" in
  ''|*[!0-9]*)
    echo "PILOT_FIXTURE_COUNT must be an integer" >&2
    exit 1
    ;;
esac
if [ "$count" -lt 1 ] || [ "$count" -gt 5000 ]; then
  echo "PILOT_FIXTURE_COUNT must be between 1 and 5000" >&2
  exit 1
fi
password="$(cat "$PILOT_OWNER_PASSWORD_FILE")"

login_payload="$(jq -n --arg email "$PILOT_OWNER_EMAIL" --arg password "$password" \
  '{email: $email, password: $password}')"
access_token="$(curl --fail --silent --show-error \
  --header 'Content-Type: application/json' \
  --data "$login_payload" \
  "$base_url/api/auth/login" | jq -er '.accessToken')"
unset password login_payload

auth_header="Authorization: Bearer $access_token"
preflight="$(curl --fail --silent --show-error \
  --header "$auth_header" \
  "$base_url/api/restaurants/$PILOT_RESTAURANT_ID/customers?query=pilot-load-0001%40example.invalid" | jq 'length')"
if [ "$preflight" -ne 0 ]; then
  echo "Pilot fixture already appears to exist; restore the baseline backup before running again" >&2
  exit 1
fi

index=1
first_reservation_id=""
while [ "$index" -le "$count" ]; do
  sequence="$(printf '%04d' "$index")"
  customer_payload="$(jq -n \
    --arg firstName "Load $sequence" \
    --arg lastName "Pilot" \
    --arg email "pilot-load-$sequence@example.invalid" \
    '{firstName: $firstName, lastName: $lastName, email: $email, tagsJson: "[\"pilot-load\"]"}')"
  customer_id="$(curl --fail --silent --show-error \
    --header "$auth_header" \
    --header 'Content-Type: application/json' \
    --data "$customer_payload" \
    "$base_url/api/restaurants/$PILOT_RESTAURANT_ID/customers" | jq -er '.id')"

  slot=$(( (index - 1) % 48 ))
  start_minutes=$(( 660 + slot * 15 ))
  start_time="$(printf '%02d:%02d' $((start_minutes / 60)) $((start_minutes % 60)))"
  party_size=$(( 1 + ((index - 1) % 8) ))
  if [ $((index % 20)) -eq 0 ]; then
    accessibility=true
  else
    accessibility=false
  fi

  reservation_payload="$(jq -n \
    --argjson customerId "$customer_id" \
    --argjson partySize "$party_size" \
    --arg date "$PILOT_DATE" \
    --arg time "$start_time" \
    --arg request "pilot-load-fixture:$sequence" \
    --argjson accessibility "$accessibility" \
    '{customerId: $customerId, channel: "PHONE", partySize: $partySize, reservationDate: $date, startTime: $time, estimatedDurationMin: 90, cleaningBufferMin: 15, specialRequests: $request, accessibilityRequired: $accessibility}')"
  reservation_id="$(curl --fail --silent --show-error \
    --header "$auth_header" \
    --header 'Content-Type: application/json' \
    --data "$reservation_payload" \
    "$base_url/api/restaurants/$PILOT_RESTAURANT_ID/reservations" | jq -er '.id')"
  if [ -z "$first_reservation_id" ]; then
    first_reservation_id="$reservation_id"
  fi

  index=$((index + 1))
done

{
  printf 'PILOT_RESTAURANT_ID=%s\n' "$PILOT_RESTAURANT_ID"
  printf 'PILOT_RESERVATION_ID=%s\n' "$first_reservation_id"
  printf 'PILOT_DATE=%s\n' "$PILOT_DATE"
  printf 'PILOT_FIXTURE_COUNT=%s\n' "$count"
} > "$output_file"
chmod 600 "$output_file"

echo "Created $count synthetic reservations. Performance variables written to $output_file"
