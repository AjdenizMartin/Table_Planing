# API

## Objetivo

Este documento define la primera superficie REST de la plataforma Restaurant Table Planning. No describe implementacion funcional completa, sino el contrato inicial esperado para la fase tecnica base y las siguientes fases de dominio.

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

## Endpoints iniciales

### 1. System

Uso interno o tecnico para verificar estado base del backend.

- `GET /api/system/ping`
- `GET /actuator/health`

### 2. Auth

Responsabilidad:

- login
- refresh token
- sesion actual
- logout logico

Endpoints:

- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `POST /api/auth/logout`
- `GET /api/auth/me`

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

- `POST /api/restaurants`
- `GET /api/restaurants`
- `GET /api/restaurants/{restaurantId}`
- `PUT /api/restaurants/{restaurantId}`
- `GET /api/restaurants/{restaurantId}/settings`
- `PUT /api/restaurants/{restaurantId}/settings`

### 4. Dining Rooms

Responsabilidad:

- gestionar salones o zonas del restaurante
- definir prioridad y accesibilidad

Endpoints:

- `POST /api/restaurants/{restaurantId}/dining-rooms`
- `GET /api/restaurants/{restaurantId}/dining-rooms`
- `GET /api/dining-rooms/{diningRoomId}`
- `PUT /api/dining-rooms/{diningRoomId}`
- `DELETE /api/dining-rooms/{diningRoomId}`

### 5. Tables

Responsabilidad:

- gestionar mesas
- capacidad minima y maxima
- estado y posicion visual

Endpoints:

- `POST /api/dining-rooms/{diningRoomId}/tables`
- `GET /api/dining-rooms/{diningRoomId}/tables`
- `GET /api/tables/{tableId}`
- `PUT /api/tables/{tableId}`
- `DELETE /api/tables/{tableId}`
- `PUT /api/tables/{tableId}/position`
- `POST /api/tables/{tableId}/enable`
- `POST /api/tables/{tableId}/disable`

### 6. Table Combinations

Responsabilidad:

- configurar combinaciones validas de mesas
- habilitar o deshabilitar combinaciones

Endpoints:

- `POST /api/restaurants/{restaurantId}/table-combinations`
- `GET /api/restaurants/{restaurantId}/table-combinations`
- `GET /api/table-combinations/{combinationId}`
- `PUT /api/table-combinations/{combinationId}`
- `DELETE /api/table-combinations/{combinationId}`

### 7. Customers

Responsabilidad:

- crear y mantener ficha de cliente
- almacenar preferencias e incidencias operativas

Endpoints:

- `POST /api/customers`
- `GET /api/customers`
- `GET /api/customers/{customerId}`
- `PUT /api/customers/{customerId}`

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

- `POST /api/reservations`
- `GET /api/reservations`
- `GET /api/reservations/{reservationId}`
- `PUT /api/reservations/{reservationId}`
- `POST /api/reservations/{reservationId}/assign`
- `POST /api/reservations/{reservationId}/reassign`
- `POST /api/reservations/{reservationId}/confirm`
- `POST /api/reservations/{reservationId}/cancel`
- `POST /api/reservations/{reservationId}/arrived`
- `POST /api/reservations/{reservationId}/no-show`

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

### 9. Availability and Planning

Responsabilidad:

- consultar disponibilidad
- exponer planning operativo
- recalcular o simular decisiones

Endpoints:

- `GET /api/availability`
- `GET /api/planning`
- `GET /api/planning/{date}/rooms/{roomId}`
- `POST /api/planning/recalculate`
- `POST /api/planning/simulate`

Ejemplo de query para disponibilidad:

```text
GET /api/availability?restaurantId=10&date=2026-05-30&time=20:30&partySize=4
```

### 10. Rules

Responsabilidad:

- gestionar reglas operativas configurables por restaurante

Endpoints:

- `GET /api/restaurants/{restaurantId}/rules`
- `POST /api/restaurants/{restaurantId}/rules`
- `GET /api/rules/{ruleId}`
- `PUT /api/rules/{ruleId}`
- `DELETE /api/rules/{ruleId}`

Tipos iniciales previstos:

- prioridad de salon
- accesibilidad
- duracion por tamaño de grupo
- buffer de limpieza
- margen de retraso
- politica de combinaciones

### 11. Notifications

Responsabilidad:

- disparar recordatorios y confirmaciones
- consultar trazas de envio

Endpoints:

- `POST /api/reservations/{reservationId}/send-confirmation`
- `POST /api/reservations/{reservationId}/send-reminder`
- `GET /api/notifications/logs`

### 12. AI

Responsabilidad:

- exponer analisis explicativo
- listar recomendaciones
- sugerir mejoras operativas

Endpoints:

- `POST /api/ai/planning/analyze`
- `GET /api/ai/recommendations`
- `POST /api/ai/recommendations/{recommendationId}/apply-suggestion`

Importante:

- la IA no asigna mesas por si sola
- las recomendaciones deben apoyarse en datos y reglas del sistema

## Orden recomendado de implementacion de API

### Fase 1

- `GET /api/system/ping`
- `POST /api/auth/login`
- `POST /api/auth/refresh`
- `GET /api/auth/me`

### Fase 2

- endpoints de `restaurants`
- endpoints de `dining-rooms`
- endpoints de `tables`
- endpoints de `table-combinations`

### Fase 3

- endpoints de `customers`
- endpoints de `reservations`

### Fase 4 y posteriores

- `availability`
- `planning`
- `rules`
- `notifications`
- `ai`

## Notas de implementacion futura

- no exponer logica del algoritmo directamente en controladores
- separar DTOs de entidades
- documentar contratos con OpenAPI mas adelante
- mantener naming coherente con `DATABASE.md`, `ARCHITECTURE.md` y `ALGORITHM.md`
