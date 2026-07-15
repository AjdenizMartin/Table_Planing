# Database

## Objetivo

La base de datos debe soportar un producto multi-restaurante con consistencia operativa, trazabilidad y capacidad de evolucionar sin rediseños bruscos. PostgreSQL es la opcion principal por robustez transaccional, flexibilidad y buen soporte para consultas complejas.

## Principios de modelado

- aislamiento logico por `restaurant_id`
- integridad referencial estricta
- historico de decisiones importantes
- soporte para auditoria
- capacidad de extender reglas con `jsonb` cuando convenga

## Estado del esquema

### Tablas reales en migraciones

- `restaurant`
- `app_user`
- `role_assignment`
- `refresh_token`
- `dining_room`
- `restaurant_table`
- `table_combination`
- `table_combination_item`
- `table_combination_resource_requirement`
- `customer`
- `reservation`
- `reservation_assignment`
- `reservation_assignment_resource`
- `audit_log`
- `restaurant_rule`
- `notification`
- `scheduled_notification`
- `notification_log`
- `ai_insight`
- `storage_resource`

### Tablas planificadas pero no creadas

- `planning_slot`
- `ai_recommendation`
- `floor_plan_template`
- `daily_floor_plan`
- `table_setup_option`
- `table_setup_option_item`
- `reservation_setup_plan`
- `setup_task`

## Entidades principales

### User

- `id`
- `email`
- `password_hash`
- `name`
- `status`
- `last_login_at`
- `created_at`
- `updated_at`

### RoleAssignment

- `id`
- `user_id`
- `restaurant_id`
- `role`
- `created_at`

En la fase inicial, incluso el rol `PLATFORM_ADMIN` se modela mediante asignaciones con `restaurant_id`, pero la capa de autorizacion puede tratar ese rol como global.

Permite que un usuario tenga distintos roles segun restaurante.

### Restaurant

- `id`
- `name`
- `slug`
- `timezone`
- `phone`
- `status`
- `settings_json`
- `created_at`
- `updated_at`

### DiningRoom

- `id`
- `restaurant_id`
- `name`
- `priority`
- `accessible`
- `active`
- `layout_width`
- `layout_height`
- `created_at`
- `updated_at`

### RestaurantTable

- `id`
- `restaurant_id`
- `dining_room_id`
- `table_type` (`FIXED`, `MOVABLE`, `STORAGE`, `TEMPORARY`)
- `code`
- `label`
- `min_capacity`
- `max_capacity`
- `shape`
- `x`
- `y`
- `width`
- `height`
- `active`
- `created_at`
- `updated_at`

Las mesas `STORAGE` representan mesas guardadas fuera del salon. En la implementacion de Fase 1 pueden no tener `dining_room_id` y no deben aparecer como mesas normales del planning ni como candidatas del algoritmo basico.

### StorageResource

- `id`
- `restaurant_id`
- `resource_type` (`EXTRA_TABLE`, `EXTRA_CHAIR`, `HIGH_CHAIR`, `FOLDING_TABLE`, `TABLE_EXTENSION`, `BENCH`, `STORAGE_TABLE`, `OTHER`)
- `name`
- `quantity`
- `capacity_per_unit`
- `setup_time_minutes`
- `active`
- `notes`
- `created_at`
- `updated_at`

Representa inventario agregado de almacen, como sillas extra, mesas plegables, tronas, extensiones o bancos. `STORAGE_TABLE` se conserva por compatibilidad con datos de V14. `quantity`, `capacity_per_unit` y `setup_time_minutes` no admiten valores negativos. Las combinaciones avanzadas pueden requerir cualquier tipo de recurso; capacidad adicional es `quantity * capacity_per_unit` y una capacidad cero representa un recurso operativo sin plazas.

### TableCombination

- `id`
- `restaurant_id`
- `name`
- `min_capacity`
- `max_capacity`
- `active`
- `combination_type` (`STANDARD`, `ADVANCED`)
- `operational_cost_level` (`LOW`, `MEDIUM`, `HIGH`)
- `setup_time_minutes`
- `created_at`
- `updated_at`

V16 migra combinaciones existentes a `STANDARD`, coste `LOW`, preparacion `0` y sin requisitos de inventario.

### TableCombinationItem

- `id`
- `table_combination_id`
- `table_id`
- `order_index`

### TableCombinationResourceRequirement

- `id`
- `restaurant_id`
- `table_combination_id`
- `storage_resource_id`
- `quantity`
- `created_at`
- `updated_at`

La pareja combinacion/recurso es unica. `restaurant_id` se mantiene de forma explicita para aislamiento multi-tenant y validacion defensiva.

### Customer

- `id`
- `restaurant_id`
- `first_name`
- `last_name`
- `phone`
- `email`
- `notes`
- `tags_json`
- `mobility_needs`
- `created_at`
- `updated_at`

### Reservation

- `id`
- `restaurant_id`
- `customer_id`
- `channel`
- `status`
- `party_size`
- `reservation_date`
- `start_time`
- `end_time`
- `estimated_duration_min`
- `cleaning_buffer_min`
- `confirmed_at`
- `cancelled_at`
- `special_requests`
- `accessibility_required`
- `created_at`
- `updated_at`

### ReservationAssignment

- `id`
- `reservation_id`
- `assignment_type`
- `dining_room_id`
- `table_id`
- `table_combination_id`
- `score`
- `explanation_json`
- `assigned_by`
- `assigned_at`
- `active`
- `operational_cost_level`
- `setup_time_minutes`

### ReservationAssignmentResource

- `id`
- `restaurant_id`
- `reservation_assignment_id`
- `storage_resource_id`
- `quantity`
- `resource_name_snapshot`
- `resource_type_snapshot`
- `capacity_per_unit_snapshot`
- `setup_time_minutes_snapshot`
- `created_at`

Los snapshots preservan la explicacion historica aunque cambie despues el inventario. Las asignaciones inactivas mantienen sus consumos como historial, pero solo las activas y con reserva operativa cuentan para disponibilidad.

### PlanningSlot

- `id`
- `restaurant_id`
- `date`
- `resource_type`
- `resource_id`
- `start_time`
- `end_time`
- `status`
- `reservation_id`

Planificado. No existe como tabla real en las migraciones actuales.

### RestaurantRule

- `id`
- `restaurant_id`
- `rule_type`
- `name`
- `enabled`
- `priority`
- `config_json`
- `created_at`
- `updated_at`

### NotificationLog

- `id`
- `restaurant_id`
- `reservation_id`
- `customer_id`
- `channel`
- `template_code`
- `status`
- `provider_message_id`
- `sent_at`
- `error_message`

### AIRecommendation

- `id`
- `restaurant_id`
- `reservation_id`
- `planning_date`
- `type`
- `severity`
- `title`
- `description`
- `recommendation_json`
- `status`
- `created_at`

Planificado en documentos iniciales, pero la implementacion real actual usa `AiInsight` y la tabla `ai_insight`.

### AiInsight

- `id`
- `restaurant_id`
- `date`
- `type`
- `severity`
- `title`
- `description`
- `entity_type`
- `entity_id`
- `metadata_json`
- `dismissed`
- `created_at`
- `updated_at`

### AuditLog

- `id`
- `restaurant_id`
- `entity_type`
- `entity_id`
- `action`
- `user_id`
- `metadata_json`
- `created_at`

### RefreshToken

- `id`
- `user_id`
- `token`
- `expires_at`
- `revoked_at`
- `created_at`
- `updated_at`

## Relaciones

- `Restaurant 1..N DiningRoom`
- `Restaurant 1..N RestaurantTable`
- `DiningRoom 1..N RestaurantTable`
- `Restaurant 1..N TableCombination`
- `TableCombination 1..N TableCombinationItem`
- `TableCombination 1..N TableCombinationResourceRequirement`
- `StorageResource 1..N TableCombinationResourceRequirement`
- `Restaurant 1..N Customer`
- `Restaurant 1..N Reservation`
- `Customer 1..N Reservation`
- `Reservation 1..N ReservationAssignment`
- `ReservationAssignment 1..N ReservationAssignmentResource`
- `Restaurant 1..N RestaurantRule`
- `Reservation 1..N NotificationLog`
- `Restaurant 1..N AIRecommendation`
- `Restaurant 1..N AiInsight`
- `User 1..N RefreshToken`

## Consideraciones de multi-tenant

- todas las entidades de negocio deben incluir `restaurant_id` salvo las globales
- todas las consultas deben filtrar por `restaurant_id`
- los indices principales deben comenzar por `restaurant_id` cuando aplique

## Indices iniciales recomendados

- `reservation(restaurant_id, reservation_date, start_time)`
- `reservation(restaurant_id, status)`
- `restaurant_table(restaurant_id, dining_room_id)`
- `dining_room(restaurant_id, priority)`
- `customer(restaurant_id, phone)`
- `notification_log(restaurant_id, sent_at)`
- `restaurant_rule(restaurant_id, rule_type, enabled)`

## Restricciones recomendadas

- unicidad de `restaurant.slug`
- unicidad de `restaurant_table.code` por restaurante
- unicidad de `dining_room.name` por restaurante si se desea
- `min_capacity <= max_capacity`

## Observaciones de consistencia

- El esquema real ya cubre la mayor parte del MVP operativo
- existe un subsistema de notificaciones internas (`notification`) que no estaba bien reflejado en la version anterior del documento
- `jsonb` se usa en varias tablas reales: `restaurant`, `customer`, `reservation_assignment`, `restaurant_rule`, `audit_log`, `ai_insight`
- combinaciones sin mesas duplicadas dentro de una misma combinacion

## Estados de reserva sugeridos

- `PENDING`
- `CONFIRMED`
- `ARRIVED`
- `SEATED`
- `COMPLETED`
- `CANCELLED`
- `NO_SHOW`

## Canal de reserva sugerido

- `MANUAL`
- `PHONE`
- `WEB`
- `GOOGLE`
- `INSTAGRAM`
- `FACEBOOK`
- `WHATSAPP`

## Campos `jsonb` propuestos

Usar `jsonb` de forma controlada en:

- `restaurant.settings_json`
- `restaurant_rule.config_json`
- `reservation_assignment.explanation_json`
- `customer.tags_json`
- `ai_recommendation.recommendation_json`
- `audit_log.metadata_json`

## Estrategia de migraciones

- Flyway desde el primer commit tecnico
- migraciones pequeñas y secuenciales
- no editar migraciones ya ejecutadas en entornos compartidos
- `V14` crea `storage_resource` y el primer conjunto de tipos
- `V15` amplia tipos y añade `capacity_per_unit` y `setup_time_minutes` con valor inicial `0` para preservar datos existentes

## Futuras extensiones posibles

- tabla de `service_shift`
- tabla de `table_block`
- tabla de `reservation_event`
- tabla de `channel_inbox`
- tabla de `waitlist_entry`
