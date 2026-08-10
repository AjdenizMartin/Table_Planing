# Database

## Objective

The database must support a multi-restaurant product with operational consistency, traceability, and the ability to evolve without disruptive redesigns. PostgreSQL is the primary choice because of its transactional robustness, flexibility, and strong support for complex queries.

## Modeling Principles

- logical isolation by `restaurant_id`
- strict referential integrity
- history of important decisions
- audit support
- ability to extend rules with `jsonb` where appropriate

## Schema Status

### Actual Tables in Migrations

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

### Planned but Uncreated Tables

- `planning_slot`
- `ai_recommendation`
- `floor_plan_template`
- `daily_floor_plan`
- `table_setup_option`
- `table_setup_option_item`
- `reservation_setup_plan`
- `setup_task`

## Main Entities

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

In the initial phase, even the `PLATFORM_ADMIN` role is modeled through assignments with `restaurant_id`, but the authorization layer may treat that role as global.

This allows a user to have different roles depending on the restaurant.

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

`STORAGE` tables represent tables stored outside the dining room. In the Phase 1 implementation, they may have no `dining_room_id` and must not appear as regular tables in the plan or as candidates for the basic algorithm.

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

Represents aggregated storage inventory, such as extra chairs, folding tables, high chairs, extensions, or benches. `STORAGE_TABLE` is retained for compatibility with V14 data. `quantity`, `capacity_per_unit`, and `setup_time_minutes` do not allow negative values. Advanced combinations may require any resource type; additional capacity is `quantity * capacity_per_unit`, and zero capacity represents an operational resource with no seats.

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

V16 migrates existing combinations to `STANDARD`, cost `LOW`, setup `0`, and no inventory requirements.

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

The combination/resource pair is unique. `restaurant_id` is retained explicitly for multi-tenant isolation and defensive validation.

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

Snapshots preserve the historical explanation even if inventory changes later. Inactive assignments retain their consumption as history, but only active assignments with an operational reservation count toward availability.

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

Planned. It does not exist as an actual table in the current migrations.

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

Planned in the initial documents, but the current actual implementation uses `AiInsight` and the `ai_insight` table.

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

## Relationships

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

## Multi-Tenant Considerations

- all business entities must include `restaurant_id` except global entities
- all queries must filter by `restaurant_id`
- primary indexes must begin with `restaurant_id` where applicable

## Recommended Initial Indexes

- `reservation(restaurant_id, reservation_date, start_time)`
- `reservation(restaurant_id, status)`
- `restaurant_table(restaurant_id, dining_room_id)`
- `dining_room(restaurant_id, priority)`
- `customer(restaurant_id, phone)`
- `notification_log(restaurant_id, sent_at)`
- `restaurant_rule(restaurant_id, rule_type, enabled)`

## Recommended Constraints

- uniqueness of `restaurant.slug`
- uniqueness of `restaurant_table.code` per restaurant
- uniqueness of `dining_room.name` per restaurant if desired
- `min_capacity <= max_capacity`

## Consistency Notes

- The actual schema already covers most of the operational MVP
- an internal notification subsystem (`notification`) exists that was not accurately reflected in the previous version of the document
- `jsonb` is used in several actual tables: `restaurant`, `customer`, `reservation_assignment`, `restaurant_rule`, `audit_log`, `ai_insight`
- combinations must not contain duplicate tables within the same combination

## Suggested Reservation Statuses

- `PENDING`
- `CONFIRMED`
- `ARRIVED`
- `SEATED`
- `COMPLETED`
- `CANCELLED`
- `NO_SHOW`

## Suggested Reservation Channels

- `MANUAL`
- `PHONE`
- `WEB`
- `GOOGLE`
- `INSTAGRAM`
- `FACEBOOK`
- `WHATSAPP`

## Proposed `jsonb` Fields

Use `jsonb` in a controlled manner in:

- `restaurant.settings_json`
- `restaurant_rule.config_json`
- `reservation_assignment.explanation_json`
- `customer.tags_json`
- `ai_recommendation.recommendation_json`
- `audit_log.metadata_json`

## Migration Strategy

- Flyway from the first technical commit
- small, sequential migrations
- do not edit migrations already executed in shared environments
- `V14` creates `storage_resource` and the initial set of types
- `V15` expands the types and adds `capacity_per_unit` and `setup_time_minutes` with an initial value of `0` to preserve existing data

## Possible Future Extensions

- `service_shift` table
- `table_block` table
- `reservation_event` table
- `channel_inbox` table
- `waitlist_entry` table
