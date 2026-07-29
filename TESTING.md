# Testing

## Objetivo

Definir una estrategia de pruebas coherente con una plataforma donde la logica de negocio, el aislamiento multi-tenant y el algoritmo de asignacion son criticos.

## Principios

- testear primero la logica que aporta valor real
- combinar pruebas rapidas de unidad con pruebas de integracion realistas
- proteger especialmente permisos, tiempos y algoritmo
- evitar una suite lenta o fragil desde el inicio

## Piramide de testing recomendada

### 1. Unit tests

Para:

- utilidades puras
- reglas de validacion
- calculos temporales
- scoring del algoritmo
- resolucion de permisos aislada

Objetivo:

- alta velocidad
- feedback rapido
- cobertura fuerte de edge cases

### 2. Integration tests

Para:

- servicios de aplicacion
- repositorios JPA
- flujos HTTP con Spring Boot
- seguridad real de endpoints
- integracion con Flyway

Objetivo:

- validar cableado de modulos
- detectar errores de configuracion o persistencia

### 3. End-to-end selectivos

Para fases posteriores:

- login
- crear reserva
- ver planning
- mover reserva

No son prioridad absoluta antes de estabilizar backend y modelo de datos.

## Herramientas recomendadas

### Backend

- `JUnit 5`
- `Spring Boot Test`
- `MockMvc`
- `Spring Security Test`
- `Testcontainers`
- `AssertJ`

### Frontend

- `Vitest`
- `React Testing Library`
- `Playwright` con Chromium para flujos criticos en 768 y 1024 px
- tests del selector ES/EN y persistencia de idioma
- tests de interfaz para acciones destructivas y permisos visibles

## Testcontainers

Se recomienda usar `Testcontainers` desde fases tempranas para:

- PostgreSQL real
- Redis si se incorpora en pruebas relevantes

Beneficios:

- pruebas mas fieles que mocks de base de datos
- validacion real de migraciones Flyway
- menor riesgo de diferencias entre local y CI

## Ejecucion de suites

La suite backend se separa en pruebas rapidas y pruebas de integracion para evitar que Docker bloquee el feedback diario.

Comandos:

- `mvn test` desde `backend/`: ejecuta unit tests y excluye `*IntegrationTest`.
- `mvn test -Pintegration-tests` desde `backend/`: ejecuta tambien `*IntegrationTest`; requiere Docker/Testcontainers disponible.
- `mvn clean compile` desde `backend/`: valida compilacion sin ejecutar tests.
- `npm test` desde `frontend/`: ejecuta pruebas de componentes y contratos de formulario.
- `npm run build` desde `frontend/`: valida TypeScript y bundle estatico.
- `npm run e2e` desde `frontend/`: ejecuta manager/staff en escritorio y tablet con API de red controlada.

Nota local:

- si Docker CLI responde pero Testcontainers falla con `Could not find a valid Docker environment`, revisar el socket activo de Docker Desktop y la compatibilidad de `docker-java`/Testcontainers con la version de Docker Desktop instalada
- en macOS con Docker Desktop puede ser necesario exponer correctamente el socket usado por el contexto `desktop-linux`; hasta resolverlo, `mvn test` sigue siendo la suite obligatoria de feedback rapido
- en JDKs recientes, Mockito inline puede fallar si el runtime no permite auto-attach de agentes; la suite usa `mock-maker-subclass` en tests para evitar depender de ese mecanismo cuando solo se mockean interfaces y colaboradores no finales.

Regla practica:

- cambios en algoritmo, reglas, SMS, audit o AI deben tener unit tests rapidos cuando sea posible
- flujos HTTP, seguridad real, Flyway y JPA deben validarse con integration tests
- si Docker no esta disponible, no se debe bloquear la suite rapida por tests de integracion

## Tipos de pruebas por modulo

### Auth y Security

Casos importantes:

- login exitoso
- login fallido
- refresh valido
- refresh invalido o revocado
- acceso a endpoint protegido sin token
- acceso con rol insuficiente
- acceso a recurso de otro restaurante

### Restaurant, DiningRoom y Table

Casos importantes:

- crear restaurante valido
- evitar duplicados prohibidos
- crear salon y mesa con capacidades validas
- impedir editar recursos de otro restaurante

### Reservation

Casos importantes:

- crear reserva valida
- rechazar reserva con datos invalidos
- detectar solapamiento segun ventana efectiva
- cambiar estados correctamente
- cancelar y liberar recurso asociado

### Planning

Casos importantes:

- devolver planning del dia por restaurante
- aislar salones correctamente
- recalculo restringido a roles permitidos
- no mostrar mesas `STORAGE` como mesas operativas del planning

### Storage Inventory

Casos importantes:

- crear recurso de almacen activo
- rechazar cantidades negativas
- rechazar `capacityPerUnit` y `setupTimeMinutes` negativos
- exigir `name` y `resourceType` al crear
- filtrar por `resourceType`
- filtrar por `active`
- actualizar nombre, tipo, cantidad, capacidad, tiempo, notas y estado
- desactivar y reactivar sin borrar fisicamente
- filtrar por restaurante
- no exponer recursos de otro restaurante
- no modificar recursos de otro restaurante
- impedir reducir o desactivar inventario por debajo de consumos futuros
- mantener inventario fuera del modo automatico

### Advanced Assignments

Casos cubiertos:

- migracion compatible de combinaciones existentes
- capacidad efectiva con recursos de cualquier tipo
- exclusion avanzada en `AUTOMATIC`
- inclusion, ranking y penalizaciones en `MANUAL_SUGGESTION`
- sugerir sin mutar asignaciones
- aplicar y persistir snapshots de inventario
- detectar solapes de inventario
- revalidar bajo bloqueo transaccional
- aislar restaurante y restringir aprobacion a owner/manager/admin

### Notification

Casos importantes:

- registrar log de envio
- reintento segun estado
- no llamar al proveedor externo si SMS esta deshabilitado
- no exponer datos sensibles a roles no permitidos

### AI

Casos importantes:

- generar recomendacion explicativa desde datos validos
- preservar dismissed al regenerar insights equivalentes
- no permitir que la IA actue como fuente primaria de asignacion

## Testing del algoritmo

Este es uno de los puntos mas importantes del producto.

### Objetivos del testing del algoritmo

- validar restricciones duras
- proteger el scoring
- asegurar decisiones coherentes
- evitar regresiones al retocar pesos o reglas

### Escenarios minimos recomendados

- reserva de 2 no ocupa mesa de 6 si existe mesa adecuada
- no se asigna mesa con conflicto horario
- se respeta buffer de limpieza
- se evita salon no prioritario si no hace falta
- se respeta accesibilidad
- se penaliza bloqueo de mesa grande
- se detecta hueco muerto relevante
- recolocacion de un salto mejora el resultado
- una mesa `STORAGE` no se considera candidata directa
- una combinacion que contenga una mesa `STORAGE` no se considera candidata basica

### Enfoque

- tests de unidad para funciones de score y filtros
- tests de integracion para escenarios completos de planning
- datasets pequeños y legibles

## Tests de API

Se recomienda cubrir:

- status codes correctos
- validaciones de payload
- serializacion de fechas
- contratos base de respuesta
- errores controlados

Casos:

- `POST /api/auth/login`
- `GET /api/auth/me`
- `POST /api/restaurants`
- `POST /api/reservations`
- `GET /api/planning`

## Tests de permisos

Deben ser explicitos y no quedar implicitos dentro de otros tests.

Casos minimos:

- `WAITER` no puede editar configuracion
- `MANAGER` puede crear reservas
- `RESTAURANT_OWNER` puede editar su restaurante
- `WAITER` no puede eliminar clientes
- manager puede eliminar clientes sin reservas
- no se puede eliminar un cliente de otro restaurante
- un cliente con reservas asociadas devuelve conflicto y conserva sus datos
- usuario de restaurante A no puede acceder a datos de restaurante B
- `PLATFORM_ADMIN` puede operar globalmente segun politica

## Tests de WebSocket

No son prioridad en la primera linea de codigo, pero deben planificarse.

Cobertura sugerida:

- conexion autenticada
- suscripcion por restaurante
- emision de evento al crear reserva
- emision de evento al cancelar reserva
- no recibir eventos de otro restaurante

## Estrategia de datos de prueba

- factories o builders para entidades
- fixtures pequenas y legibles
- evitar dependencias ocultas entre tests
- cada test debe preparar su propio contexto o uno controlado

## CI recomendada

Pipeline actual:

1. validar formato y compilacion
2. ejecutar unit tests
3. ejecutar integration tests
4. ejecutar tests frontend y build estatico
5. ejecutar Playwright en escritorio y tablet
6. construir imagenes Docker backend, frontend dev y frontend prod

Antes del piloto tambien se ejecuta `PilotOnboardingIntegrationTest`: comprueba creacion auditada, repeticion idempotente, rollback por conflicto y rechazo de archivos de contrasena sin permisos `0600`.

Playwright cubre viewports tablet con Chromium, pero no sustituye el UAT sobre una tablet Android fisica. Ese gate manual incluye tactil, teclado virtual, rotacion, zona horaria y reconexion WebSocket.

## Validacion de produccion

- `ProductionRegistrationIntegrationTest` arranca con perfil `prod` y exige `404` en `/api/auth/register`.
- los tests frontend verifican que un build productivo no puede habilitar la ruta de registro.
- el build productivo no debe contener credenciales demo.
- `scripts/production-preflight.sh` rechaza un checkout sin tag exacto, secretos inseguros, DNS ausente, poco disco o Compose invalido.
- `scripts/pilot-ops-check.sh` falla ante contenedores no saludables, HTTPS/registro/TLS incorrectos, disco alto o backups ausentes.
- la imagen backend debe ejecutar como usuario no root y arrancar con filesystem de solo lectura y capacidades eliminadas.

## Definition of done para testing

Antes de cerrar una funcionalidad importante:

- existe cobertura de casos felices
- existen casos de error relevantes
- se han probado permisos
- se han probado restricciones multi-tenant
- si afecta algoritmo, hay casos de no regresion

## Alcance de la primera fase tecnica

La primera fase debe dejar preparados:

- tests de contexto Spring
- tests basicos de seguridad
- primer setup de `Testcontainers` para PostgreSQL
- base para `MockMvc`

No hace falta aun:

- suite e2e completa
- cobertura exhaustiva del frontend
- simulaciones avanzadas del algoritmo
