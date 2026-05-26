# Database

## Objetivo

La base de datos debe soportar un producto multi-restaurante con consistencia operativa, trazabilidad y capacidad de evolucionar sin rediseños bruscos. PostgreSQL es la opcion principal por robustez transaccional, flexibilidad y buen soporte para consultas complejas.

## Principios de modelado

- aislamiento logico por `restaurant_id`
- integridad referencial estricta
- historico de decisiones importantes
- soporte para auditoria
- capacidad de extender reglas con `jsonb` cuando convenga

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

### TableCombination

- `id`
- `restaurant_id`
- `name`
- `min_capacity`
- `max_capacity`
- `active`
- `created_at`
- `updated_at`

### TableCombinationItem

- `id`
- `table_combination_id`
- `table_id`
- `order_index`

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

Puede ser una vista materializada futura o una tabla persistida si aporta valor operativo.

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
- `Restaurant 1..N Customer`
- `Restaurant 1..N Reservation`
- `Customer 1..N Reservation`
- `Reservation 1..N ReservationAssignment`
- `Restaurant 1..N RestaurantRule`
- `Reservation 1..N NotificationLog`
- `Restaurant 1..N AIRecommendation`
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
- combinaciones sin mesas duplicadas dentro de una misma combinacion

## Estados de reserva sugeridos

- `PENDING`
- `CONFIRMED`
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

## Futuras extensiones posibles

- tabla de `service_shift`
- tabla de `table_block`
- tabla de `reservation_event`
- tabla de `channel_inbox`
- tabla de `waitlist_entry`
