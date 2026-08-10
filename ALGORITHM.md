# Algorithm

## Objective

The assignment engine must select the best table or combination for each reservation, not merely any available table. The objective is to optimize occupancy, future flexibility, and the operational quality of service.

## Currently implemented version

Current status: `PILOT_READY`

Currently implemented:

- effective window using `start_time`, `end_time`, and `cleaning_buffer`
- candidates comprising active tables and active predefined combinations
- hard constraints for capacity, active status, accessibility, and overlaps
- deterministic scoring with a persisted explanation
- deterministic tie-breaking
- exclusion of `STORAGE` tables as regular candidates
- defensive exclusion of combinations containing `STORAGE` tables
- separate `AUTOMATIC` and `MANUAL_SUGGESTION` modes
- manual top 3 with advanced combinations and inventory
- additional capacity from resources and temporal availability
- deterministic advanced operational penalties
- transactional application with locking and revalidation

Not yet implemented:

- deep replanning or reassignment cascades
- batch optimization for an entire service period
- advanced future-demand simulation
- availability as a separate module/API
- special setups with approval and operational tasks

## Principles

- deterministic algorithm before opaque heuristics
- mandatory explainability
- strict observance of hard constraints
- configurable and improvable scoring
- AI solely as explanatory support

## Problem to solve

When a new reservation arrives, the system must decide:

- whether it can be accepted
- which table or combination it should be assigned to
- whether a more flexible resource should be reserved
- whether a limited reassignment exists that improves the plan

## Algorithm inputs

- date
- requested time
- number of guests
- estimated duration
- cleaning time
- restaurant rules
- enabled dining rooms
- available tables
- permitted combinations
- existing reservations
- customer preferences
- accessibility requirements

## Hard constraints

These constraints must always be satisfied:

- capacity must accommodate the party size
- there must be no effective time overlap
- the cleaning buffer must be observed
- the dining room must be permitted by the rules
- accessibility must be respected when applicable
- the table or combination must be active

## Effective occupancy window

The reservation does not occupy only the customer's exact reservation time. The following must be calculated:

- actual start
- actual end

Conceptual formula:

```text
inicio_real = hora_reserva
fin_real = hora_reserva + duracion_estimada + buffer_limpieza
```

For an advanced candidate, the effective start is brought forward to account for setup:

```text
inicio_inventario = hora_reserva - setup_time_minutes
fin_inventario = fin_reserva + buffer_limpieza
```

## Selection strategy

### Step 1. Normalize request

- determine duration according to party size and rules
- apply cleaning time and margins
- load the restaurant context

### Step 2. Find candidates

`AUTOMATIC` mode searches individual tables and `STANDARD` combinations. `MANUAL_SUGGESTION` mode adds `ADVANCED` combinations, calculates required resources, and limits the ordered output to three options.

Controlled dynamic combinations may be generated in an advanced phase.

From Phase 1 of advanced planning, tables with `table_type = STORAGE` are excluded from the regular search. Standard combinations must not contain `STORAGE` tables either; the backend validates this when combinations are created or updated, and the finder defensively ignores them if they appear because of legacy data. They must be evaluated only at advanced levels with approval or a setup plan.

`StorageResource` is included only through the requirements of an advanced combination. All its types are valid. Each unit contributes `capacity_per_unit`; a zero value makes it possible to model linens, extensions, or other resources without seats. Availability subtracts quantities consumed by active assignments whose windows overlap.

Manual selection locks the required resources in the database in ID order and repeats the check before persisting. Two concurrent transactions cannot exceed `StorageResource.quantity`.

## Advanced evolution by level

The planned evolution is documented in [docs/ADVANCED_TABLE_PLANNING_DESIGN.md](./docs/ADVANCED_TABLE_PLANNING_DESIGN.md):

- Level 1: individual dining-room table.
- Level 2: standard combination.
- Level 3: combination with extra chairs.
- Level 4: stored table or storage resource.
- Level 5: special setup with manager approval.
- Level 6: suggest an alternative time only for a new request.

Safety rule: the algorithm must not change the time of existing reservations. Any time change requires a customer request and manual editing by staff.

### Step 3. Filter by hard constraints

Remove any option that:

- lacks sufficient capacity
- is already occupied
- violates dining-room rules
- fails accessibility requirements

### Step 4. Evaluate local and future impact

For each candidate:

- measure wasted capacity
- measure unusable gaps before and after
- measure the blocking of large tables
- measure the loss of future flexibility
- measure the use of non-priority dining rooms

### Step 5. Calculate score

Each option receives a total score composed of bonuses and penalties.

Advanced combinations add:

```text
operational_cost_penalty = LOW 8 | MEDIUM 24 | HIGH 48
setup_time_penalty = min(setup_time_minutes * 0.5, 30)
```

### Step 6. Select the best option

Select by descending score with stable tie-breaking by type and ID. Manual suggestions return at most three options.

### Step 7. Explain the decision

Store:

- selected candidate
- primary alternatives
- score factors
- activated rules

## Limited reassignment

Documented as planned evolution. The current version does not perform deep automatic reassignment; it supports only manual moves from planning and direct automatic assignment.

## Initial scoring formula

```text
score_total =
  w1 * capacity_fit
+ w2 * room_priority
+ w3 * future_flexibility
+ w4 * preference_match
+ w5 * accessibility_match
+ w6 * service_flow_alignment
- w7 * wasted_seats_penalty
- w8 * dead_gap_penalty
- w9 * large_table_block_penalty
- w10 * room_activation_penalty
- w11 * recombination_cost
- w12 * reassignment_cost
- w13 * fragmentation_penalty
## Meaning of factors

### Bonuses

- `capacity_fit`: rewards capacity close to the party size
- `room_priority`: rewards the preferred dining room
- `future_flexibility`: rewards preserving useful options for later
- `preference_match`: rewards customer or manager preferences
- `accessibility_match`: rewards suitable assignments
- `service_flow_alignment`: rewards operational balance

### Penalties

- `wasted_seats_penalty`: penalizes occupying excessive capacity
- `dead_gap_penalty`: penalizes leaving unusable gaps
- `large_table_block_penalty`: penalizes using large tables for small parties
- `room_activation_penalty`: penalizes opening a secondary dining room prematurely
- `recombination_cost`: penalizes unnecessary complex combinations
- `reassignment_cost`: penalizes moving existing reservations
- `fragmentation_penalty`: penalizes excessive dispersion of service

## Examples of expected behavior

### Case 1

Reservation for 2 people:

- if a table for 2 is available, it should be prioritized over a table for 6
- unless the analysis determines that the table for 2 is strategically important for another time slot

### Case 2

Reservation for 4 people at 20:30:

- if there is no direct opening, moving a small reservation may be evaluated
- the reassignment should be accepted only if it clearly improves the overall plan

### Case 3

Customer with reduced mobility:

- the dining room with stairs must be avoided
- even if availability exists there, the option must be discarded or heavily penalized according to the rule

## Explainability

Each assignment must return a summary similar to:

```text
Table M12 in the main dining room selected.
Reasons:
- capacity closely matches the party of 4
- avoids blocking a table for 6
- keeps a useful combination available at 21:00
- respects the priority of the main dining room
```

## Parameterization

Scoring weights must be configurable by default at platform level and adjustable later by restaurant, taking care not to undermine consistency.

## Evolution strategy

### Version 1

- individual tables
- predefined combinations
- heuristic scoring
- persisted explanation

### Version 2

- controlled dynamic combinations
- future-demand simulation
- weight adjustment using historical data

### Version 3

- full-service-period optimization
- batch simulations
- advanced AI-supported recommendations

## Algorithm testing requirements

- happy-path scenarios
- edge cases
- temporal conflicts
- accessibility
- dining-room priority
- unusable gaps
- reassignment
- no regressions between versions
