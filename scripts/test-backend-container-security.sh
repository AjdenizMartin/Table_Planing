#!/bin/sh
set -eu

image="${1:-table-planning-backend:ci}"
temp_dir="$(mktemp -d)"
trap 'rm -rf "$temp_dir"' EXIT

printf '%s' 'container-test-database-password' > "$temp_dir/postgres_password"
printf '%s' 'container-test-jwt-secret-that-is-long-enough-for-production-validation-1234567890' > "$temp_dir/jwt_secret"
chmod 600 "$temp_dir/postgres_password" "$temp_dir/jwt_secret"

cat > "$temp_dir/java" <<'EOF'
#!/bin/sh
set -eu

test "$(id -u)" = "10001"
test "$(id -g)" = "10001"
test "${SPRING_DATASOURCE_PASSWORD:-}" = "container-test-database-password"
test "${APP_SECURITY_JWT_SECRET:-}" = "container-test-jwt-secret-that-is-long-enough-for-production-validation-1234567890"

for capability_field in CapInh CapPrm CapEff CapBnd CapAmb; do
  grep -q "^${capability_field}:	0000000000000000$" /proc/self/status
done
grep -q '^NoNewPrivs:	1$' /proc/self/status
EOF
chmod 755 "$temp_dir/java"

docker run --rm \
  --read-only \
  --tmpfs /tmp:size=64m,mode=1777 \
  --security-opt no-new-privileges:true \
  --cap-drop ALL \
  --cap-add DAC_READ_SEARCH \
  --cap-add SETPCAP \
  --cap-add SETGID \
  --cap-add SETUID \
  -e PATH=/test-bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
  -e SPRING_DATASOURCE_PASSWORD_FILE=/run/secrets/postgres_password \
  -e APP_SECURITY_JWT_SECRET_FILE=/run/secrets/jwt_secret \
  -v "$temp_dir/postgres_password:/run/secrets/postgres_password:ro" \
  -v "$temp_dir/jwt_secret:/run/secrets/jwt_secret:ro" \
  -v "$temp_dir/java:/test-bin/java:ro" \
  "$image"

echo "Backend container secret and privilege checks passed"
