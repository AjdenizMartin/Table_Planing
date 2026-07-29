# Security

## Objetivo

Definir el modelo inicial de seguridad de la plataforma para la primera fase tecnica y las fases inmediatas siguientes. La meta es proteger acceso, datos y aislamiento por restaurante sin introducir complejidad innecesaria.

## Principios

- autenticacion centralizada
- autorizacion por rol y contexto de restaurante
- aislamiento multi-tenant obligatorio
- backend como autoridad final de permisos
- trazabilidad de acciones sensibles
- minimo privilegio por defecto

## Roles del sistema

### `PLATFORM_ADMIN`

Alcance:

- gestiona la plataforma completa
- puede crear restaurantes
- puede gestionar usuarios globales
- puede inspeccionar configuracion general

No debe usarse como atajo para logica de restaurante en UI.

Implementacion inicial:

- el modelo de persistencia sigue usando `RoleAssignment` con `restaurant_id`
- si un usuario tiene al menos un `RoleAssignment` con rol `PLATFORM_ADMIN`, la autorizacion lo trata como acceso global

### `RESTAURANT_OWNER`

Alcance:

- administra su restaurante
- configura salones, mesas, reglas e integraciones
- gestiona usuarios del restaurante
- puede operar reservas y planning

### `MANAGER`

Alcance:

- opera el dia a dia
- crea y modifica reservas
- reubica mesas
- usa planning, clientes y confirmaciones
- puede modificar configuracion operativa limitada segun politica

### `WAITER`

Alcance:

- consulta planning
- marca llegada o cambios operativos permitidos
- no debe modificar estructura del restaurante ni seguridad

## Matriz inicial de permisos

| Recurso / accion | PLATFORM_ADMIN | RESTAURANT_OWNER | MANAGER | WAITER |
|---|---|---|---|---|
| Ver restaurantes asignados | Yes | Yes | Yes | Yes |
| Crear restaurante | Yes | No | No | No |
| Editar datos del restaurante | Yes | Yes | Limited | No |
| Gestionar salones y mesas | Yes | Yes | Limited | No |
| Gestionar combinaciones | Yes | Yes | Limited | No |
| Gestionar reglas | Yes | Yes | Limited | No |
| Crear y editar clientes | Yes | Yes | Yes | Limited |
| Eliminar clientes sin reservas | Yes | Yes | Yes | No |
| Crear y editar reservas | Yes | Yes | Yes | Limited |
| Confirmar o cancelar reservas | Yes | Yes | Yes | Limited |
| Reasignar mesas manualmente | Yes | Yes | Yes | No |
| Consultar top 3 avanzado | Yes | Yes | Yes | No |
| Aprobar asignacion avanzada | Yes | Yes | Yes | No |
| Ver recursos e historial | Yes | Yes | Yes | Yes |
| Ver planning | Yes | Yes | Yes | Yes |
| Recalcular planning | Yes | Yes | Yes | No |
| Ver recomendaciones IA | Yes | Yes | Yes | No |
| Gestionar usuarios del restaurante | Yes | Yes | No | No |

`Limited` significa que la accion puede habilitarse con restricciones mas precisas a definir por endpoint o politica.

## Modelo de autenticacion

## Access token

- formato JWT
- vida corta
- enviado en header `Authorization`
- firmado por el backend

Claims sugeridos:

- `sub`
- `user_id`
- `email`
- `roles`
- `restaurant_ids`
- `active_restaurant_id` opcional
- `iat`
- `exp`

## Refresh token

- token de mayor vida util
- almacenado de forma segura
- usado para renovar `access token`
- revocable

En la implementacion inicial se modela como token persistido y opaco, revocable desde backend.

## Sesion

Flujo inicial:

1. login con email y password
2. emision de `access token`
3. emision de `refresh token`
4. renovacion mediante `/api/auth/refresh`
5. invalidacion logica en logout

## Passwords

- hash con `BCrypt` o `Argon2`
- nunca almacenar passwords en texto plano
- nunca registrar password en logs

## Multi-tenant

## Regla principal

Todo recurso de negocio pertenece a un restaurante o debe resolverse dentro de un contexto de restaurante.

## Reglas de aislamiento

- toda query funcional debe filtrar por `restaurant_id`
- no confiar solo en IDs enviados por cliente
- el backend debe validar que el usuario tiene acceso al restaurante del recurso
- los eventos WebSocket tambien deben quedar aislados por restaurante

## Resolucion del contexto de restaurante

Orden sugerido:

1. validar token del usuario
2. resolver restaurantes autorizados
3. validar recurso solicitado
4. confirmar que el recurso pertenece al restaurante esperado

## Autorizacion

Se recomienda combinar:

- autorizacion declarativa en endpoints
- validacion de dominio en servicios

Esto evita que un endpoint aparente estar protegido pero permita cruces indebidos de restaurante o accion.

## Reglas de acceso por modulo

### Auth

- login publico
- refresh controlado
- `me` autenticado

### Restaurant

- lectura y escritura limitadas por rol
- `PLATFORM_ADMIN` con alcance global
- `RESTAURANT_OWNER` con alcance sobre su restaurante

### DiningRoom, Table y TableCombination

- escritura solo para owner o manager autorizado
- lectura para roles operativos del restaurante

### Customer y Reservation

- lectura y escritura para roles operativos
- `WAITER` con acceso restringido a acciones del servicio
- sugerencias y seleccion avanzada solo para owner, manager y platform admin
- historial y recursos asignados visibles para roles operativos del restaurante

### Planning

- lectura para todos los roles operativos
- recalculo solo para manager, owner o platform admin

### Notification

- disparo y consulta de logs segun rol
- acceso restringido a datos sensibles como telefonos

### AI

- lectura de recomendaciones para owner y manager
- acciones de aplicacion de sugerencias sujetas a permisos de planning

### Customers

- `PLATFORM_ADMIN`, `RESTAURANT_OWNER` y `MANAGER` pueden eliminar clientes
- `WAITER` mantiene acceso de lectura y no ve la accion de borrado
- el servicio valida el restaurante objetivo y bloquea el borrado si hay reservas asociadas
- cada borrado aplicado genera una entrada de auditoria

## Seguridad de API

- HTTPS obligatorio en produccion
- CORS restringido a orígenes permitidos
- validacion de payloads con `Spring Validation`
- limitacion de tasa en login y mensajeria
- errores sin fuga de informacion sensible

Implementacion del piloto:

- Nginx termina TLS y redirige HTTP a HTTPS
- login limitado a 5 solicitudes por minuto por IP, con burst controlado
- CORS backend parametrizado y restringido al origen HTTPS del piloto
- perfil `prod` sin controlador de registro, build frontend sin ruta/CTA de alta y bloqueo adicional en Nginx
- Actuator expone solo `health` y no se publica a Internet
- PostgreSQL y backend viven en una red Docker interna sin puertos host
- secretos de PostgreSQL y JWT se montan como archivos, nunca en la imagen
- perfil `prod` no activa el bootstrap demo por estar limitado a `@Profile("dev")`
- el entrypoint del backend inicia con capacidades limitadas para leer secretos `0600`, cambia inmediatamente al UID/GID `10001` y ejecuta Java sin capacidades Linux, con filesystem de solo lectura y `no-new-privileges`
- Nginx aplica HSTS, CSP, proteccion contra framing y restricciones de permisos del navegador
- logs Docker rotados y monitor operativo con alerta webhook opcional sin incluir secretos

La limitacion por IP en Nginx es adecuada para el piloto de un VPS. Antes de una exposicion publica amplia debe complementarse con observabilidad, bloqueo progresivo por cuenta y proteccion del proveedor perimetral.

## Seguridad de frontend

- no almacenar secretos en frontend
- evitar meter reglas de permisos solo en UI
- ocultar acciones no autorizadas, pero sin confiar en eso como control real

## Auditoria

Deben registrarse al menos:

- login exitoso y fallido relevante
- creacion y modificacion de restaurante
- cambios en mesas y salones
- creacion, confirmacion, cancelacion y no-show de reservas
- reasignaciones y recalculos de planning
- envios de notificaciones

## Riesgos iniciales y mitigacion

### Riesgo: fuga entre restaurantes

Mitigacion:

- filtros por `restaurant_id`
- tests de aislamiento
- validacion de pertenencia de recurso

### Riesgo: privilegios excesivos

Mitigacion:

- permisos minimos por rol
- endpoints con reglas explicitas
- revisiones de seguridad por modulo

### Riesgo: refresh token comprometido

Mitigacion:

- revocacion
- rotacion futura
- expiracion razonable

### Riesgo: exponer telefonos o notas internas

Mitigacion:

- serializacion cuidada
- DTOs
- control fino de permisos

## Alcance de la primera fase tecnica

La primera fase debe dejar listo:

- login
- `access token`
- `refresh token`
- endpoint `me`
- contexto de restaurante
- autorizacion basica por rol
- base para auditoria

No es necesario todavia:

- SSO
- MFA
- politicas avanzadas de device management
- delegacion compleja entre usuarios
