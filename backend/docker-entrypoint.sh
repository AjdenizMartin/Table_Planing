#!/bin/sh
set -eu

read_secret() {
  variable_name="$1"
  file_variable_name="${variable_name}_FILE"
  eval "file_path=\${$file_variable_name:-}"

  if [ -n "$file_path" ]; then
    if [ ! -r "$file_path" ]; then
      echo "Secret file for $variable_name is not readable" >&2
      exit 1
    fi
    export "$variable_name=$(cat "$file_path")"
  fi
}

read_secret SPRING_DATASOURCE_PASSWORD
read_secret APP_SECURITY_JWT_SECRET

exec setpriv \
  --reuid=10001 \
  --regid=10001 \
  --clear-groups \
  --bounding-set=-all \
  --inh-caps=-all \
  --ambient-caps=-all \
  --no-new-privs \
  java -jar /app/app.jar "$@"
