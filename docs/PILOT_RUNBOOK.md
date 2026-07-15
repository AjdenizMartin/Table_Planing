# Pilot Runbook

## Preparacion

1. Crear DNS `A` para `APP_DOMAIN` apuntando al VPS.
2. Copiar `.env.production.example` a `.env.production` y completar dominio, email y rutas.
3. Crear `secrets/postgres_password.txt` y `secrets/jwt_secret.txt` con valores aleatorios distintos de los ejemplos.
4. Exportar las variables de `.env.production` y ejecutar `scripts/bootstrap-tls.sh` antes del primer arranque.
5. Levantar con `docker compose --env-file .env.production -f docker-compose.prod.yml up -d --build`.
6. Verificar `docker compose --env-file .env.production -f docker-compose.prod.yml ps` y `https://$APP_DOMAIN`.

Produccion no expone PostgreSQL ni backend, no ejecuta `DevBootstrapDataInitializer` y Nginx bloquea el registro publico. Las cuentas se crean durante onboarding administrativo.

## Onboarding del restaurante

Orden obligatorio:

1. Owner, manager y staff con roles correctos.
2. Restaurante y zona horaria.
3. Salones y dimensiones de layout.
4. Mesas operativas, capacidades, tipo y coordenadas.
5. Inventario con cantidad, capacidad por unidad y preparacion.
6. Combinaciones estandar.
7. Combinaciones avanzadas, coste y requisitos.
8. Reserva de prueba por cada estado operativo.

Validar con tres usuarios que staff no puede aplicar sugerencias y manager/owner si.

## Backup

Ejecutar diariamente desde cron:

```text
15 3 * * * cd /opt/table-planning && set -a && . ./.env.production && set +a && ./scripts/backup-postgres.sh
```

La retencion se controla con `BACKUP_RETENTION_DAYS`. Copiar los dumps fuera del VPS con el mecanismo del proveedor. Una copia en el mismo disco no cubre perdida del servidor.

## Restauracion

1. Activar ventana de mantenimiento.
2. Exportar `.env.production` en la shell.
3. Ejecutar `./scripts/restore-postgres.sh backups/<archivo>.dump`.
4. Confirmar health de backend y acceso de manager.
5. Comparar conteos de restaurantes, reservas y asignaciones.
6. Registrar fecha, operador, backup y resultado.

Probar restauracion antes de UAT y mensualmente durante el piloto.

## Renovacion TLS

Renovar el certificado en una ventana breve con Certbot y recargar frontend. Verificar fecha de expiracion con `openssl s_client` o el monitor del proveedor. Mantener alertas al menos 14 dias antes del vencimiento.

## Rollback

1. Detener frontend y backend; mantener PostgreSQL.
2. Volver a la imagen/commit anterior.
3. Si la migracion no es compatible, restaurar el backup previo al despliegue.
4. Arrancar backend, comprobar Flyway y health.
5. Arrancar frontend y ejecutar el smoke de manager/staff.

Nunca editar manualmente `flyway_schema_history`.

## Incidencias

- Inventario agotado: actualizar sugerencias; no forzar SQL ni duplicar asignaciones.
- Conflicto concurrente: la segunda seleccion recibe `409`; recargar y elegir otra opcion.
- Backend no saludable: revisar logs y conectividad interna con PostgreSQL.
- Realtime caido: la operacion REST sigue siendo valida; recargar planning mientras se recupera WebSocket.
