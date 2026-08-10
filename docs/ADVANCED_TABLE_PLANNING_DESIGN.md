# Advanced Table Planning Design

## Pilot implementation status

V16 and V17 implement `STANDARD`/`ADVANCED` combinations, operational cost, preparation, inventory requirements, top 3 suggestions, explicit selection, and consumption snapshots. Approval is the manager's selection itself. `ReservationSetupPlan`, `SetupTask`, daily floor plans, and the advanced visual editor remain outside the scope of the pilot.

## 1. Real operational problem

Many restaurants do not operate with a completely fixed floor plan. During an actual service, the team may join tables, move lightweight tables, add chairs, bring out stored tables, or prepare a temporary setup for large groups. Table planning apps that only look for a free table or a simple combination often fail in these cases:

- they leave tables empty even when an operationally viable solution exists
- they block large tables for small reservations
- they do not understand that a table may physically be in storage rather than in the dining room
- they do not distinguish between a normal combination and a setup that requires staff work
- they do not alert the preparation team when the assignment requires prior action
- they optimize capacity without considering operational cost, accessibility, or future impact

The result is poorer occupancy, more improvisation during service, large reservations being rejected unnecessarily, and a greater mental burden on managers and servers.

## 2. Objective of the advanced system

The objective is to evolve the product into a daily planning engine that understands physical resources and operational cost, without sacrificing explainability or human control. The system must be able to:

- start from a restaurant's base layout
- generate or edit a daily floor plan for each service
- distinguish between fixed, movable, temporary, and stored tables
- model extra chairs and other stored resources
- evaluate normal and advanced combinations
- propose special setups when worthwhile
- create operational tasks to prepare those setups
- explain why an option is inexpensive, costly, safe, or risky

Central rule: the system must not change the time of an existing reservation. It may move the reservation between resources, join tables, or suggest preparations, but the time changes only if the customer requests it and staff edit it manually. For a new request, the system may suggest alternative times if there is no viable option at the requested time.

## 3. Main concepts

### Base layout

Stable restaurant configuration: dining rooms, standard tables, coordinates, capacities, accessibility, and priorities. It represents how the restaurant is usually arranged before daily adjustments.

### Daily floor plan

Operational version for a specific day or shift. It may copy the base layout and apply exceptions: blocked tables, moved tables, resources brought out of storage, open or closed areas, and temporary setups.

### Fixed tables

Tables that are not normally moved. They may be part of combinations, but moving them must be prohibited or carry a very high cost.

### Movable tables

Tables present in the dining room that staff can move or join. They have a lower operational cost than using storage, but still require time and coordination.

### Storage tables

Tables stored outside the dining room. They must not appear as normally available tables in the planning view. They only come into play through an advanced option or an approved setup.

### Extra chairs

Additional chairs available in storage or the back office. They make it possible to increase the capacity of a table or setup, within physical and operational limits.

### Table combinations

Configured combinations of existing tables. In the initial phase, these are standard dining-room combinations. In advanced phases, they may include cost, restrictions, orientation, and preparation requirements.

### Setup options

Configurable or generated options that describe how to accommodate a reservation: individual table, combination, table with extra chairs, storage table, temporary setup, or combination involving movement.

### Reservation setup plans

Plan selected or proposed for a specific reservation. It must indicate the resources used, cost, required approval, explanation, and status.

### Setup tasks

Operational tasks derived from a plan: retrieve a table from storage, add 4 chairs, move two tables, prepare a large tablecloth, open a secondary dining room, or confirm with a manager.

## 4. Proposed data model

### Changes to RestaurantTable

Proposed fields:

- `table_type`: `FIXED`, `MOVABLE`, `STORAGE`, `TEMPORARY`
- `dining_room_id`: nullable for `STORAGE`; required for physical tables in a dining room
- `movable`: derivable from `table_type`, but may be retained as a future rule if greater granularity is needed
- `setup_cost`: base cost of moving or preparing the table
- `storage_resource_id`: optional if a storage table is also managed as aggregated inventory

Initial rule: `STORAGE` tables do not appear as normal dining-room tables or as candidates for the basic algorithm.

### StorageResource

Aggregated restaurant inventory.

Fields:

- `id`
- `restaurant_id`
- `resource_type`: `EXTRA_TABLE`, `EXTRA_CHAIR`, `HIGH_CHAIR`, `FOLDING_TABLE`, `TABLE_EXTENSION`, `BENCH`, `STORAGE_TABLE`, `OTHER`
- `name`
- `quantity`
- `capacity_per_unit`
- `setup_time_minutes`
- `active`
- `notes`
- `created_at`
- `updated_at`

Use: record resources such as extra chairs, folding tables, high chairs, extensions, or benches. `STORAGE_TABLE` remains a type compatible with V14. In the pilot, these resources are consumed only when an advanced combination is explicitly selected; they never enter automatic assignment.

### FloorPlanTemplate

Base or alternative floor plan template.

Fields:

- `id`
- `restaurant_id`
- `name`
- `description`
- `active`
- `default_template`
- `created_at`
- `updated_at`

Use: support layouts by season, terrace, event, or shift.

### DailyFloorPlan

Operational floor plan for a day.

Fields:

- `id`
- `restaurant_id`
- `business_date`
- `service_period`
- `floor_plan_template_id`
- `status`: `DRAFT`, `PUBLISHED`, `LOCKED`
- `created_by`
- `published_at`
- `created_at`
- `updated_at`

Use: freeze daily decisions without altering the base layout.

### TableSetupOption

Available or calculated setup option.

Fields:

- `id`
- `restaurant_id`
- `name`
- `setup_type`: `SINGLE_TABLE`, `STANDARD_COMBINATION`, `EXTRA_CHAIRS`, `STORAGE_RESOURCE`, `SPECIAL_SETUP`
- `min_capacity`
- `max_capacity`
- `requires_manager_approval`
- `estimated_setup_minutes`
- `operational_cost`
- `active`
- `explanation_template`
- `created_at`
- `updated_at`

### TableSetupOptionItem

Resources that make up an option.

Fields:

- `id`
- `table_setup_option_id`
- `resource_type`: `TABLE`, `TABLE_COMBINATION`, `STORAGE_RESOURCE`, `EXTRA_CHAIR`
- `resource_id`
- `quantity`
- `role`: `PRIMARY`, `EXTENSION`, `CHAIR`, `STORAGE`
- `order_index`

### ReservationSetupPlan

Plan proposed, approved, or applied for a reservation.

Fields:

- `id`
- `restaurant_id`
- `reservation_id`
- `table_setup_option_id`
- `status`: `PROPOSED`, `APPROVED`, `REJECTED`, `APPLIED`, `CANCELLED`
- `score`
- `operational_cost`
- `requires_manager_approval`
- `approved_by`
- `approved_at`
- `explanation_json`
- `created_at`
- `updated_at`

### SetupTask

Operational work to be performed by staff.

Fields:

- `id`
- `restaurant_id`
- `reservation_setup_plan_id`
- `reservation_id`
- `task_type`: `MOVE_TABLE`, `ADD_CHAIRS`, `FETCH_STORAGE_TABLE`, `OPEN_ROOM`, `PREPARE_SPECIAL_SETUP`, `MANAGER_APPROVAL`
- `title`
- `description`
- `status`: `PENDING`, `IN_PROGRESS`, `DONE`, `CANCELLED`
- `assigned_to`
- `due_at`
- `completed_by`
- `completed_at`
- `created_at`
- `updated_at`

## 5. Algorithm evolution

### Level 1: individual table

Use only active dining-room tables, excluding `STORAGE`, while respecting capacity, overlaps, accessibility, and dining-room priority.

### Level 2: standard combination

Use configured combinations of active dining-room tables. It must remain deterministic and explainable.

### Level 3: combination with extra chairs

Allow normal capacity to be exceeded within a safe margin if enough extra chairs are available and the table/dining room allows it.

### Level 4: storage table

Consider `STORAGE_TABLE` resources or `STORAGE` tables only as an advanced option. It must generate an operational cost and a preparation task.

### Level 5: special setup requiring approval

Create a `ReservationSetupPlan` with `PROPOSED` status when the solution requires moving several tables, opening a secondary dining room, retrieving items from storage, or preparing an unusual setup. It is not applied automatically without approval.

### Level 6: suggest an alternative time for a new request

Only for new requests, if there is no viable solution at the requested time. It must never modify existing reservations automatically.

## 6. Proposed scoring

The score must combine capacity quality, future impact, and operational cost:

- `capacity_fit`: rewards capacity that fits the group
- `wasted_seats_penalty`: penalizes wasted seats
- `room_priority`: rewards main or preferred dining rooms
- `large_table_block_penalty`: penalizes blocking large tables for small groups
- `dead_gap_penalty`: penalizes dead gaps before or after
- `move_table_cost`: penalizes moving physical tables
- `storage_usage_cost`: penalizes retrieving resources from storage
- `setup_time_cost`: penalizes lengthy preparations close to the arrival time
- `manager_approval_cost`: penalizes options that require approval
- `future_reservation_impact`: penalizes reduced flexibility for future reservations

The explanation must separate hard constraints, operational costs, and commercial reasons.

## 7. Safety rules

- Do not change the times of existing reservations automatically.
- Do not use storage resources without confirmation or an approved plan.
- Do not create setups that are impossible due to capacity, space, accessibility, or inventory.
- Do not exceed the available quantity of extra chairs or tables.
- Respect declared accessibility in dining rooms and reservations.
- Respect `restaurant_id` in all entities, queries, events, and permissions.
- Maintain traceability of approvals, rejections, and completed tasks.
- The frontend may suggest actions, but validation lives in the backend.

## 8. Proposed UX

### Configure storage tables

In table configuration, allow the `STORAGE` type. These tables are shown as inventory, not as tables placed in a dining room. The UI must warn that they do not appear as available in the daily planning view until a setup is approved.

### Configure extra chairs

Create a storage inventory section with resources of type `EXTRA_CHAIR`. It must show total quantity, active status, and internal instructions.

### Create visual combinations

In a later phase, the editor must allow tables to be selected from the floor plan and saved as a combination, indicating whether it requires moving tables or only joining them.

### Show special setups in the planning view

The planning view must distinguish between:

- normal assignment
- standard combination
- option with extra chairs
- setup pending approval
- approved setup with pending tasks

### Notify staff

`SetupTask` entries must appear in the operational dashboard, internal notifications, and reservation details. They must have a clear status and an optional assignee.

### Confirm or reject an advanced option

When an option requires approval, the manager sees the cost, resources, tasks, and explanation. The manager may approve, reject, or choose a simpler option.

## 9. Implementation phases

### Phase 1: table types and storage inventory

Add `tableType` to tables, create `StorageResource`, a minimal CRUD API, and a configuration UI. The algorithm must only exclude `STORAGE` from normal candidates.

### Phase 2: advanced combinations

Extend combinations with cost, type, restrictions, and limited use of extra chairs.

### Phase 3: setup options

Model reusable options and their items. Allow them to be configured and listed.

### Phase 4: tiered algorithm

Progressively evaluate levels 1 through 5, maintaining determinism and explainability. Level 6 is only for new requests.

### Phase 5: setup tasks

Generate operational tasks from approved plans.

### Phase 6: planning UI

Show plans, costs, approvals, and tasks within the daily planning view.

### Phase 7: advanced visual editor

Visual editor for combinations, setups, and daily floor plans. 3D is not required for the MVP.

## 10. Recommended MVP

Implement Phase 1 first:

- `tableType` in `RestaurantTable`
- `StorageResource` with quantity and availability validation
- basic UI for viewing `STORAGE` tables and resources such as extra chairs
- exclusion of `STORAGE` tables from the planning view and normal candidates
- updated documentation

This provides value quickly because the restaurant can begin recording real resources without yet changing the algorithm's critical behavior. It also prepares the model for advanced phases without risking existing reservations.

## 11. Technical risks

- Algorithm complexity if too many levels are combined at once.
- Data misconfigured by the restaurant: unrealistic capacities, duplicate resources, or incorrect quantities.
- Physically impossible setups if dimensions, access routes, and dining-room constraints are not modeled.
- UI that is too complex for fast tablet operation.
- Over-optimization before validating real needs.
- Risk of breaking the current assignment behavior if storage resources enter as normal tables.
- Lack of traceability if setups are applied without a plan, approval, or task.

## Proposed first safe implementation

The first safe implementation is Phase 1, limited to the model, API, minimal UI, and documentation:

- add `tableType` with values `FIXED`, `MOVABLE`, `STORAGE`, `TEMPORARY`
- create `StorageResource` with `resourceType`, `name`, `quantity`, `active`, and `notes`
- allow `STORAGE` tables to be created, but exclude them from the planning view and the normal algorithm
- expose a storage inventory API per restaurant
- add a quantity availability check in preparation for future phases
- show in configuration that extra resources and stored tables exist

Automatic special setups, approvals, tasks, time changes, AI, WhatsApp, 3D, and deep optimization are not yet implemented.
