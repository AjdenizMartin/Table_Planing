# API

## Objetivo

Este documento distingue entre la API realmente implementada hoy y la API planificada para fases futuras.

## Principios de la API

- API REST sobre `Spring Boot`
- versionado inicial simple con prefijo `/api`
- autenticacion por JWT
- aislamiento multi-tenant obligatorio
- validacion de entrada en backend
- respuestas consistentes y trazables
- WebSocket para tiempo real, no para operaciones de escritura

## Convenciones generales

### Base path

```text
/api
```

### Formato

- `application/json` para request y response
- fechas en `ISO-8601`
- timestamps en UTC

### Autenticacion

Salvo excepciones publicas definidas, los endpoints requieren:

```text
Authorization: Bearer <access_token>
```

### Contexto de restaurante

La aplicacion es multi-restaurante. El backend debe resolver el contexto de restaurante a partir de:

- claims del token
- permisos del usuario
- recurso objetivo

En fases futuras se puede admitir un header explicito como apoyo, por ejemplo:

```text
X-Restaurant-Id: <uuid o id>
```

Pero nunca debe sustituir la validacion de permisos.

## Estados HTTP sugeridos

- `200 OK`
- `201 Created`
- `204 No Content`
- `400 Bad Request`
- `401 Unauthorized`
- `403 Forbidden`
- `404 Not Found`
- `409 Conflict`
- `422 Unprocessable Entity`

## Estructura de error sugerida

```json
{
  "code": "VALIDATION_ERROR",
  "message": "Request validation failed",
  "details": [
    {
      "field": "partySize",
      "message": "must be greater than 0"
    }
  ],
  "timestamp": "2026-05-26T13:00:00Z"
}
```

## Estado actual

- `IMPLEMENTED`: existe controlador y flujo base funcional
- `PARTIAL`: existe de forma limitada o con alcance menor al plan original
- `PLANNED`: documentado pero no presente en el backend actual

## Endpoints

### 1. System

Uso interno o tecnico para verificar estado base del backend.

- `GET /api/system/ping` - `IMPLEMENTED`
- `GET /actuator/health` - `IMPLEMENTED`

### 2. Auth

Responsabilidad:

- login
- refresh token
- sesion actual
- logout logico

Endpoints:

- `POST /api/auth/login` - `IMPLEMENTED`
- `POST /api/auth/refresh` - `IMPLEMENTED`
- `POST /api/auth/logout` - `IMPLEMENTED`
- `GET /api/auth/me` - `IMPLEMENTED`
- `POST /api/auth/register` - `DEV_ONLY`; el controlador no se registra con perfil `prod` y Nginx devuelve `404`

Payloads iniciales esperados:

`POST /api/auth/login`

```json
{
  "email": "owner@restaurant.com",
  "password": "********"
}
```

Respuesta conceptual:

```json
{
  "accessToken": "jwt-access-token",
  "refreshToken": "opaque-or-jwt-refresh-token",
  "expiresIn": 900,
  "user": {
    "id": 1,
    "name": "Owner Name",
    "email": "owner@restaurant.com"
  },
  "restaurants": [
    {
      "id": 10,
      "name": "Main Restaurant",
      "role": "RESTAURANT_OWNER"
    }
  ]
}
```

### 3. Restaurants

Responsabilidad:

- alta y edicion de restaurantes
- consulta de configuracion general
- administracion multi-tenant

Endpoints:

- `POST /api/restaurants` - `IMPLEMENTED`
- `GET /api/restaurants` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/settings` - `PLANNED`
- `PUT /api/restaurants/{restaurantId}/settings` - `PLANNED`

### 4. Dining Rooms

Responsabilidad:

- gestionar salones o zonas del restaurante
- definir prioridad y accesibilidad

Endpoints:

- `POST /api/restaurants/{restaurantId}/dining-rooms` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/dining-rooms` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/dining-rooms/{diningRoomId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/dining-rooms/{diningRoomId}` - `IMPLEMENTED`
- `DELETE /api/restaurants/{restaurantId}/dining-rooms/{diningRoomId}` - `IMPLEMENTED`

### 5. Tables

Responsabilidad:

- gestionar mesas
- capacidad minima y maxima
- estado y posicion visual

Endpoints:

- `POST /api/restaurants/{restaurantId}/tables` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/tables` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/tables/{tableId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/tables/{tableId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/tables/{tableId}/layout` - `IMPLEMENTED`
- `DELETE /api/restaurants/{restaurantId}/tables/{tableId}` - `IMPLEMENTED`
- `POST /api/tables/{tableId}/enable` - `PLANNED`
- `POST /api/tables/{tableId}/disable` - `PLANNED`

Fase 1 de planificacion avanzada anade `tableType` a mesas:

- `FIXED`
- `MOVABLE`
- `STORAGE`
- `TEMPORARY`

Las mesas `STORAGE` se registran como inventario operativo y no aparecen como mesas normales del planning.

### 6. Table Combinations

Responsabilidad:

- configurar combinaciones validas de mesas
- habilitar o deshabilitar combinaciones

Endpoints:

- `POST /api/restaurants/{restaurantId}/table-combinations` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/table-combinations` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/table-combinations/{combinationId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/table-combinations/{combinationId}` - `IMPLEMENTED`
- `DELETE /api/restaurants/{restaurantId}/table-combinations/{combinationId}` - `IMPLEMENTED`

Los contratos de alta y edicion aceptan `combinationType`, `operationalCostLevel`, `setupTimeMinutes` y `resourceRequirements: [{storageResourceId, quantity}]`. Los campos omitidos conservan compatibilidad como `STANDARD`, `LOW`, `0` y lista vacia. Una combinacion estandar no puede consumir inventario ni tener preparacion previa.

### 6b. Storage Resources

Responsabilidad:

- gestionar inventario de almacen como sillas extra o mesas guardadas
- validar cantidades disponibles para futuras opciones de montaje

Endpoints:

- `POST /api/restaurants/{restaurantId}/storage-resources` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/storage-resources?resourceType={type}&active={boolean}` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/storage-resources/{resourceId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/storage-resources/{resourceId}` - `IMPLEMENTED`
- `DELETE /api/restaurants/{restaurantId}/storage-resources/{resourceId}` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/storage-resources/{resourceId}/availability-check` - `IMPLEMENTED`

Tipos de recurso actuales:

- `EXTRA_TABLE`
- `EXTRA_CHAIR`
- `HIGH_CHAIR`
- `FOLDING_TABLE`
- `TABLE_EXTENSION`
- `BENCH`
- `STORAGE_TABLE`
- `OTHER`

Los filtros `resourceType` y `active` son opcionales y combinables. Todas las consultas y modificaciones se resuelven por `restaurantId`; un identificador de recurso perteneciente a otro restaurante devuelve `NOT_FOUND`.

Campos editables mediante `PATCH`:

- `resourceType`
- `name`
- `quantity`
- `capacityPerUnit`
- `setupTimeMinutes`
- `notes`
- `active`

`quantity`, `capacityPerUnit` y `setupTimeMinutes` deben ser enteros no negativos. `name` y `resourceType` son obligatorios al crear. Los dos campos operativos añadidos en Sprint 1 aceptan omision al crear y se inicializan a `0` para mantener compatibilidad con clientes anteriores.

`DELETE` no borra fisicamente el registro: mantiene compatibilidad con el endpoint existente y realiza una desactivacion logica. La UI usa `PATCH` con `active=false` o `active=true` para desactivar y reactivar.

No se permite reducir o desactivar un recurso por debajo de su pico de consumo futuro ya comprometido. El calculo considera asignaciones activas y las ventanas de preparacion, servicio y limpieza.

### 7. Customers

Responsabilidad:

- crear y mantener ficha de cliente
- almacenar preferencias e incidencias operativas

Endpoints:

- `POST /api/restaurants/{restaurantId}/customers` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/customers` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/customers/{customerId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/customers/{customerId}` - `IMPLEMENTED`

Filtros previstos:

- `phone`
- `email`
- `name`
- `tags`

### 8. Reservations

Responsabilidad:

- alta y mantenimiento de reservas
- cambio de estado
- confirmacion, cancelacion y no-show
- asignacion automatica o manual

Endpoints:

- `POST /api/restaurants/{restaurantId}/reservations` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/reservations` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/reservations/{reservationId}` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/reservations/{reservationId}` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/assign` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/reservations/{reservationId}/assignment-suggestions` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/assignment-selection` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/reservations/{reservationId}/assignment-history` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/confirm` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/cancel` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/seat` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/complete` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/no-show` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/reassign` - `PLANNED`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/arrived` - `IMPLEMENTED`

`assignment-suggestions` devuelve como maximo tres candidatos deterministas y no escribe en base de datos. Cada opcion incluye mesas, capacidad, score, coste, preparacion, inventario requerido/disponible y explicacion estructurada.

`assignment-selection` recibe:

```json
{
  "candidateType": "TABLE_COMBINATION",
  "candidateId": 42
}
```

La seleccion se revalida en transaccion, bloquea los recursos de inventario en orden estable, vuelve a comprobar solapes y solo entonces desactiva la asignacion anterior y persiste la nueva. Un conflicto concurrente devuelve `409` y no sobrevende inventario.

Owner, manager y platform admin pueden consultar y aplicar sugerencias. Staff puede consultar planning, recursos asignados e historial, pero no solicitar ni aprobar sugerencias. El endpoint automatico `assign`, confirmacion automatica y recalculo diario siguen limitados a mesas y combinaciones `STANDARD`.

Filtros previstos:

- `restaurantId`
- `date`
- `status`
- `channel`
- `customerId`

Payload conceptual para crear reserva:

```json
{
  "restaurantId": 10,
  "customerId": 200,
  "partySize": 4,
  "reservationDate": "2026-05-30",
  "startTime": "20:30:00",
  "channel": "MANUAL",
  "specialRequests": "Quiet table if possible",
  "accessibilityRequired": false
}
```

### 9. Planning

- `GET /api/restaurants/{restaurantId}/planning?date=YYYY-MM-DD` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/planning/recalculate` - `IMPLEMENTED`
- `POST /api/restaurants/{restaurantId}/planning/move-reservation` - `IMPLEMENTED`
- `GET /api/availability` - `PLANNED`
- `POST /api/planning/simulate` - `PLANNED`

### 10. Rules

- `POST /api/restaurants/{restaurantId}/rules` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/rules` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/rules/{ruleId}` - `IMPLEMENTED`
- `DELETE /api/restaurants/{restaurantId}/rules/{ruleId}` - `IMPLEMENTED`

### 11. Notifications

- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/notifications/confirmation` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/notifications` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/notifications/unread-count` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/notifications/{notificationId}/read` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/notifications/read-all` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/notifications/logs` - `IMPLEMENTED`

### 12. AI Insights

- `GET /api/restaurants/{restaurantId}/ai/insights?date=YYYY-MM-DD` - `IMPLEMENTED`
- `GET /api/restaurants/{restaurantId}/ai/insights/summary?date=YYYY-MM-DD` - `IMPLEMENTED`
- `PATCH /api/restaurants/{restaurantId}/ai/insights/{insightId}/dismiss` - `IMPLEMENTED`

## Notas de implementacion futura

- no exponer logica del algoritmo directamente en controladores
- separar DTOs de entidades
- documentar contratos con OpenAPI mas adelante
- mantener naming coherente con `DATABASE.md`, `ARCHITECTURE.md` y `ALGORITHM.md`
- la IA no asigna mesas por si sola; en el estado actual solo analiza y explica planning
