# Pilot Runbook

## Preparacion

1. Preparar Ubuntu 24.04 LTS con 2 vCPU, 4 GB RAM, 80 GB SSD, Docker, Compose, `jq` y `restic`.
2. Restringir SSH a claves y publicar solo 80/443.
3. Crear DNS `A` para `APP_DOMAIN` apuntando al VPS.
4. Copiar `.env.production.example` a `.env.production`, completar dominio, tag, email, rutas y repositorio Restic, y aplicar `chmod 600 .env.production`.
5. Crear todos los archivos bajo `secrets/` con valores aleatorios y permisos `0600`.
6. Crear `logs/`, hacer checkout de `v0.1.0-rc.6` y no desplegar una rama flotante.
7. Ejecutar `./scripts/production-preflight.sh`; debe validar Ubuntu, tag, DNS, secretos, disco, puertos y Compose.
8. Exportar `.env.production` y ejecutar `scripts/bootstrap-tls.sh` antes del primer arranque.
9. Validar Compose y levantar el tag aprobado:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml config --quiet
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
docker compose --env-file .env.production -f docker-compose.prod.yml ps
curl --fail "https://$APP_DOMAIN/api/system/ping"
```

Produccion no expone PostgreSQL ni backend, no ejecuta datos demo y Nginx devuelve `404` para el registro publico.

## Onboarding administrativo

El alta inicial no usa una API publica. Preparar un directorio ignorado por Git:

```bash
mkdir -p onboarding
cp docs/pilot-onboarding.example.json onboarding/manifest.json
chmod 600 onboarding/manifest.json
for role in owner manager staff; do
  { printf 'Aa1!'; openssl rand -base64 24 | tr -d '\n'; printf '\n'; } > "onboarding/${role}_password.txt"
  chmod 600 "onboarding/${role}_password.txt"
done
```

Editar nombres y emails, conservando rutas `/run/onboarding/...` dentro del manifiesto. Ejecutar:

```bash
./scripts/pilot-onboard.sh
```

El comando copia los archivos `0600` a un volumen Docker privado y efimero propiedad del UID `10001`, usa `prod,onboarding` y elimina el volumen al terminar. Valida un unico owner, roles permitidos, zona horaria, conflictos y permisos. Acepta `RESTAURANT_OWNER`, `MANAGER`, `WAITER` y el alias `STAFF`, que se persiste como `WAITER`. Es transaccional, no resetea contrasenas existentes y audita actor, usuarios y restaurante. Una repeticion puede omitir `passwordFile` para usuarios ya verificados.

Despues, cargar desde la UI y en este orden: salones, 40 mesas, inventario, combinaciones estandar, 20 combinaciones totales y reservas de prueba. Confirmar con las tres cuentas que staff (`WAITER`) no puede aprobar sugerencias.

## Backup externo

Inicializar una sola vez el repositorio cifrado:

```bash
set -a && . ./.env.production && set +a
export AWS_ACCESS_KEY_ID="$(cat "$AWS_ACCESS_KEY_ID_FILE")"
export AWS_SECRET_ACCESS_KEY="$(cat "$AWS_SECRET_ACCESS_KEY_FILE")"
restic init
```

Ejecutar diariamente desde cron:

```text
15 3 * * * cd /opt/table-planning && set -a && . ./.env.production && set +a && ./scripts/backup-postgres.sh && ./scripts/backup-offsite.sh >> logs/backup.log 2>&1
```

La retencion por defecto es 14 diarias, 8 semanales y 12 mensuales. Una copia en el mismo VPS no cuenta como recuperacion.

## Observabilidad operativa

Despues de crear el primer backup local y externo, ejecutar `./scripts/pilot-ops-check.sh`. Comprueba los tres contenedores, HTTPS, registro bloqueado, vencimiento TLS, disco y backups local/Restic. Configurar `OPS_ALERT_WEBHOOK_URL_FILE` con un archivo `0600` para enviar fallos sin incluir secretos.

Ejecutar cada hora y registrar la salida:

```text
5 * * * * cd /opt/table-planning && set -a && . ./.env.production && set +a && ./scripts/pilot-ops-check.sh >> logs/ops-check.log 2>&1
```

## Rendimiento

Crear primero un backup base. La carga es deliberadamente mutante y debe restaurarse al terminar:

```bash
export PILOT_BASE_URL="https://$APP_DOMAIN"
export PILOT_OWNER_EMAIL="owner@example.com"
export PILOT_OWNER_PASSWORD_FILE="./onboarding/owner_password.txt"
export PILOT_RESTAURANT_ID="<id>"
export PILOT_DATE="<YYYY-MM-DD>"
export PILOT_FIXTURE_CONFIRM=CREATE_SYNTHETIC_DATA
./scripts/pilot-load-fixture.sh
set -a && . ./pilot-fixture-result.env && set +a
./scripts/pilot-performance-smoke.sh
```

El gate exige diez respuestas de planning por debajo de 2 s y diez de sugerencias por debajo de 1 s. Restaurar el backup base tras guardar resultados.

## Restauracion

1. Activar ventana de mantenimiento y exportar `.env.production`.
2. Ejecutar `./scripts/restore-postgres.sh backups/<archivo>.dump`.
3. Confirmar health, login de manager y conteos de restaurantes, reservas y asignaciones.
4. Registrar fecha, operador, backup y resultado.

Probar restauracion antes de UAT y mensualmente durante el piloto.

## Renovacion TLS

El challenge usa el webroot servido por Nginx. Programar una comprobacion diaria:

```text
20 4 * * * cd /opt/table-planning && set -a && . ./.env.production && set +a && ./scripts/renew-tls.sh >> logs/tls.log 2>&1
```

Mantener ademas una alerta externa al menos 14 dias antes del vencimiento.

## Rollback

1. Detener frontend y backend; mantener PostgreSQL.
2. Volver al tag anterior.
3. Si las migraciones no son compatibles, restaurar el backup previo.
4. Arrancar backend, comprobar Flyway/health y despues frontend.
5. Ejecutar smoke de manager y staff. Nunca editar `flyway_schema_history`.

## Incidencias

- Inventario agotado: actualizar sugerencias; no forzar SQL.
- Conflicto concurrente: la segunda seleccion recibe `409`; recargar y elegir otra opcion.
- Backend no saludable: revisar logs y conectividad interna con PostgreSQL.
- Realtime caido: REST sigue siendo valido; recargar planning mientras se recupera WebSocket.
- Android sin conexion: volver al procedimiento manual; esta version no ofrece modo offline.
