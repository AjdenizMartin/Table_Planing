# Architecture

## Resumen

La solucion se diseña como un monolito modular con frontend desacoplado. La prioridad es maximizar velocidad de desarrollo, coherencia transaccional y claridad del dominio, manteniendo espacio suficiente para evolucionar a integraciones y capacidades avanzadas.

## Objetivos arquitectonicos

- soportar uno o varios restaurantes en una unica plataforma
- permitir configuracion operativa sin tocar codigo
- mantener consistencia transaccional en reservas y asignaciones
- ofrecer actualizacion en tiempo real del planning
- separar claramente algoritmo, negocio y UI
- permitir crecimiento gradual hacia un SaaS comercial

## Arquitectura de alto nivel

```text
Tablet / Web PWA (React + TypeScript)
        ↓
REST API + WebSocket Gateway (Spring Boot)
        ↓
Application Services / Domain Modules
        ↓
Planning Engine + Optimization Engine
        ↓
PostgreSQL
        ↓
Redis (cache, locks, realtime helpers)
        ↓
Servicios externos
  - Twilio SMS
  - WhatsApp Cloud API
  - Proveedor IA via Spring AI
```

## Estilo arquitectonico

### Backend

Se recomienda un monolito modular con separacion por dominios:

- `auth`
- `user`
- `restaurant`
- `diningroom`
- `table`
- `customer`
- `reservation`
- `planning`
- `optimization`
- `availability`
- `notification`
- `rules`
- `ai`
- `audit`

Ventajas:

- transacciones simples y seguras
- menor complejidad operativa
- mas velocidad de entrega para MVP
- dominio fuertemente cohesionado

### Frontend

Aplicacion SPA/PWA en React orientada a tablet:

- vistas operativas rapidas
- planning visual por hora y mesa
- formularios de alta frecuencia de uso
- sincronizacion con backend por REST y WebSocket

## Componentes principales

### 1. Frontend React PWA

Responsabilidades:

- autenticacion y contexto de restaurante
- dashboard diario
- planning visual
- gestion de reservas y clientes
- configuracion de salones, mesas y reglas
- consumo de eventos en tiempo real

### 2. API Spring Boot

Responsabilidades:

- exponer endpoints REST
- aplicar autenticacion y autorizacion
- coordinar servicios de dominio
- emitir eventos WebSocket
- integrar servicios externos

### 3. Planning Engine

Responsabilidades:

- construir el planning diario
- calcular ventanas efectivas de ocupacion
- detectar conflictos y huecos
- preparar informacion operativa para UI

### 4. Optimization Engine

Responsabilidades:

- buscar mesas y combinaciones validas
- calcular puntuaciones
- justificar decisiones
- probar recolocaciones acotadas

### 5. Persistence Layer

Responsabilidades:

- almacenar configuracion, reservas, asignaciones y logs
- asegurar integridad de datos
- soportar auditoria y multi-tenant

### 6. Notification Layer

Responsabilidades:

- confirmaciones y recordatorios
- reintentos y logs de envio
- abstraccion de proveedores

### 7. AI Assistance Layer

Responsabilidades:

- explicar decisiones del algoritmo
- detectar patrones suboptimos
- sugerir reorganizaciones
- no tomar decisiones autonomas de asignacion

## Principios clave

### 1. El algoritmo manda, la IA explica

La asignacion de mesas debe depender de reglas y scoring determinista. La IA nunca debe sustituir el motor principal.

### 2. Multi-tenant logico desde el inicio

Cada recurso de negocio debe estar vinculado a `restaurant_id`. Toda query, permiso y evento debe respetar ese aislamiento.

### 3. Configuracion sin codigo

El restaurante debe poder crear:

- salones
- mesas
- combinaciones
- prioridades
- reglas operativas

### 4. Explicabilidad

Cada asignacion debe poder responder:

- que opcion se eligio
- que alternativas se descartaron
- que reglas influyeron

### 5. Tiempo real con backend como fuente de verdad

WebSocket se usa para sincronizacion visual. La validacion y consistencia siguen viviendo en el backend.

## Seguridad

- JWT con access token y refresh token
- autorizacion basada en roles
- filtro obligatorio por restaurante
- auditoria en cambios criticos
- rate limiting para login y mensajeria

## Roles previstos

- `PLATFORM_ADMIN`
- `RESTAURANT_OWNER`
- `MANAGER`
- `WAITER`

## Modulos del backend

| Modulo | Responsabilidad |
|---|---|
| `auth` | login, JWT, refresh y permisos |
| `user` | usuarios, perfiles y membresias |
| `restaurant` | datos globales del restaurante |
| `diningroom` | salones, zonas, prioridades |
| `table` | mesas, capacidades, estados, layout |
| `customer` | clientes y preferencias |
| `reservation` | reservas, estados y ciclo de vida |
| `planning` | vista operativa del dia |
| `optimization` | scoring, candidatos y recolocacion |
| `availability` | consulta de disponibilidad |
| `notification` | SMS, WhatsApp y logs |
| `rules` | reglas configurables del negocio |
| `ai` | recomendaciones y explicaciones |
| `audit` | trazabilidad de acciones |

## Infraestructura recomendada

### Desarrollo

- Docker Compose
- backend
- frontend
- PostgreSQL
- Redis

### Produccion inicial

- VPS o plataforma tipo Render, Railway o Fly.io
- Nginx como reverse proxy
- backend empaquetado en contenedor
- frontend servido como assets estaticos
- PostgreSQL gestionado o autoadministrado

## Observabilidad prevista

- logs estructurados
- trazas de eventos clave
- metricas de asignacion y ocupacion
- auditoria funcional

## Evolucion futura

Se podra separar a futuro:

- servicio de notificaciones
- integraciones con canales externos
- analitica avanzada

Pero no se recomienda fragmentar antes de validar el producto y el algoritmo central.
