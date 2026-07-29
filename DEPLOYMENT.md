# Deployment

## Objetivo

Desplegar el monolito modular en un unico VPS para el piloto, con PostgreSQL persistente, frontend estatico, HTTPS y recuperacion documentada.

## Entornos

### Desarrollo

`docker-compose.yml` levanta PostgreSQL, backend con perfil `dev` y Vite. Expone frontend en `5173` y backend en `8080`. El bootstrap demo solo existe en este perfil.

```bash
docker compose up --build
```

### Piloto

`docker-compose.prod.yml` contiene:

- PostgreSQL 16 sin puerto publico y volumen persistente.
- backend Spring Boot con perfil `prod`, filesystem de solo lectura, health check y secretos montados. El entrypoint usa capacidades minimas para leerlos y cambia inmediatamente al usuario no privilegiado, sin conservar capacidades.
- frontend compilado con Node y servido por Nginx.
- proxy de mismo origen para `/api` y `/ws`.
- HTTP redirigido a HTTPS, TLS 1.2/1.3 y HSTS.
- limitacion de login y registro publico ausente tanto en backend como en frontend productivos.
- CSP y cabeceras contra framing, sniffing y acceso a camara, microfono o geolocalizacion.
- Nginx como unico servicio en la red perimetral; backend y PostgreSQL exclusivamente en la red interna.
- capacidades eliminadas antes de ejecutar Java en backend, rotacion de logs y reinicio `unless-stopped`.

## Configuracion

- `.env.production.example`: nombres de variables sin secretos.
- `secrets/postgres_password.txt`: password PostgreSQL, ignorado por Git.
- `secrets/jwt_secret.txt`: secreto JWT aleatorio largo, ignorado por Git.
- `secrets/restic_password.txt` y credenciales S3: cifrado y acceso al backup externo.
- `frontend/nginx/default.conf.template`: TLS y proxy.
- `frontend/Dockerfile.prod`: build estatico multi-stage.

No usar los valores demo ni el compose de desarrollo en el VPS.

## Primer despliegue

1. Instalar Docker Engine, Compose plugin, `jq`, `restic` y configurar firewall para SSH, 80 y 443.
2. Crear DNS para el dominio del piloto.
3. Hacer checkout del tag indicado por `PILOT_RELEASE_TAG` y preparar `.env.production` con permisos `0600` y secretos independientes.
4. Ejecutar el preflight de SO, tag, DNS, secretos, disco, puertos y Compose:

```bash
./scripts/production-preflight.sh
```

5. Obtener certificado con `scripts/bootstrap-tls.sh`.
6. Validar configuracion:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml config --quiet
```

7. Construir y arrancar:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build
```

8. Verificar estado:

```bash
docker compose --env-file .env.production -f docker-compose.prod.yml ps
curl --fail https://reservas.example.com/api/system/ping
```

9. Ejecutar el onboarding administrativo descrito en `docs/PILOT_RUNBOOK.md`. No habilitar temporalmente `/api/auth/register` ni insertar usuarios mediante SQL.

Flyway aplica V1-V17 al arrancar el backend. El backend no se considera saludable hasta terminar migraciones y responder `/actuator/health` dentro de la red.

## Actualizacion

1. Crear backup previo.
2. Descargar la revision aprobada por CI.
3. Construir las nuevas imagenes.
4. Reiniciar backend y verificar health/migraciones.
5. Reiniciar frontend.
6. Ejecutar smoke manager/staff y rendimiento.

No mezclar una actualizacion de imagen con cambios manuales de esquema.

## Backup y restauracion

`scripts/backup-postgres.sh` genera de forma atomica un dump custom con permisos privados y elimina copias locales vencidas. `scripts/backup-offsite.sh` cifra y replica esos dumps mediante Restic hacia almacenamiento S3 compatible.

`scripts/restore-postgres.sh <backup.dump>` detiene backend, restaura con `pg_restore --clean --if-exists` y vuelve a arrancarlo. Toda restauracion requiere ventana de mantenimiento y comprobacion funcional posterior.

El procedimiento completo, renovacion TLS, incidencias y rollback vive en [docs/PILOT_RUNBOOK.md](./docs/PILOT_RUNBOOK.md).

## Observabilidad minima

- health checks Docker de base de datos, backend y frontend
- logs rotados de PostgreSQL, Spring Boot y Nginx mediante `docker compose logs`
- `scripts/pilot-ops-check.sh` para contenedores, HTTPS, registro bloqueado, TLS, disco y backups local/externo
- alerta webhook opcional mediante `OPS_ALERT_WEBHOOK_URL_FILE`

El check operativo se activa despues de crear y verificar el primer backup externo; antes de ese momento debe fallar para impedir una falsa sensacion de recuperabilidad.

Actuator externo no se publica; el health del backend se consulta dentro de Compose.

## Clientes Android

Ubuntu ejecuta el servidor. Las tablets Android acceden por Chrome a la URL HTTPS y no necesitan Docker ni una APK. El piloto requiere validar en un dispositivo real tactil, teclado virtual, rotacion, zona horaria y reconexion WebSocket. No hay soporte offline en esta version.

## Limites del piloto

- un VPS y un PostgreSQL persistente
- sin Kubernetes, Redis, microservicios ni multi-region
- recuperacion mediante backup/restore, no alta disponibilidad
- SMS real deshabilitado hasta configurar proveedor y secretos propios
