# Planning Panel Implementation Plan

## Phase 1: Functional and stable planning

Objective: turn `PlanningPage` into a premium, touch-friendly, and stable main screen without drag-and-drop or time changes.

Tasks:

- Create an operational header with date, shift, dining room, occupancy, reservations, guests, and realtime status.
- Create a left panel with the day's reservations, search, filters, and unassigned reservations.
- Create a premium 2D visual floor plan for each dining room.
- Create visual states for tables and reservations.
- Create a right-side table/reservation details panel.
- Create a read-only bottom timeline.
- Retain the existing automatic assignment actions and manual movement via selector if they are already safe.
- Clearly show that times cannot be changed from planning.
- Handle loading/error/empty states.
- Validate `npm run build`.

Completion criteria:

- The demo user logs in and opens planning.
- Dining rooms, tables, and reservations are visible.
- Confirmed, pending, seated, completed, and unassigned reservations are distinguishable.
- The screen is usable on a tablet.
- There is no interaction that can accidentally change times.

## Phase 2: Touch-friendly visual dining room and table editor

Objective: separate service mode from edit mode and allow the floor plan to be configured from a tablet.

Current status:

- Direct table movement implemented with native Pointer Events.
- Automatic save on release via `PATCH /api/restaurants/{restaurantId}/tables/{tableId}/layout`.
- Local rollback if the backend rejects the save.
- 10px snap-to-grid available in the editor.

Tasks:

- Create `features/floor-plan`.
- Extract the current editor into dedicated components if complexity increases.
- Expand edit mode with multi-select and alignment.
- Allow tables to be created, duplicated, deactivated, and edited.
- Allow shape, capacity, and dimensions to be changed.
- Prepare undo/redo.
- Retain layout saving through the existing table endpoint.
- Evaluate `dnd-kit` against `react-konva`.

Criteria:

- The manager can configure a dining room without modifying code.
- Edit mode does not affect active reservations.

## Phase 3: Drag and drop reservations between tables while retaining the original time

Objective: visually reassign reservations without allowing time changes.

Tasks:

- Introduce `dnd-kit`.
- Allow reservations to be dragged only onto tables or combinations.
- Lock the time axis and timeline dragging.
- Create visual pre-validation.
- The backend validates capacity, overlap, buffer, accessibility, tenant, and time immutability.
- Display the reason for rejection.
- Confirm significant moves.

Criteria:

- A reservation can be moved between tables without changing its time.
- A reservation cannot be dragged to another time.
- Every move is processed by the backend.

## Phase 4: Premium 2.5D/isometric view

Objective: increase visual impact while maintaining stability.

Tasks:

- Apply subtle perspective/isometric styling to tables and zones.
- Improve shadows, depth, and labels.
- Create a presentation/demo mode.
- Maintain large touch targets.
- Avoid true 3D except for an isolated proof of concept.

Criteria:

- The view is impressive in demos without sacrificing usability.
- It continues to work well on tablets.

## Phase 5: Live service mode

Objective: provide a fast screen for servers and managers during service.

Tasks:

- Create `features/live-service`.
- Show tables currently occupied.
- Show arrivals in 15/30/60 minutes.
- Show delays, pending items, and cleaning status.
- Quick actions: arrived, seated, finished, no-show, reminder.
- Integrate WebSocket.

Criteria:

- The team can operate the shift from a tablet with few taps.

## Phase 6: Visual optimization and suggestions without time changes

Objective: show actionable opportunities without altering schedules.

Tasks:

- Create `features/optimization`.
- Show suggestions for changing tables, using dining rooms, and creating combinations.
- Show capacity before/after.
- Prohibit automatic time-change recommendations.
- The manager accepts or dismisses suggestions.

Criteria:

- Each suggestion explains its impact and respects the original time.

## Phase 7: Tablet UI/UX polish

Objective: turn the panel into a premium experience.

Tasks:

- Refine the palette, states, animations, and responsive behavior.
- Improve touch accessibility.
- Create a large-screen mode.
- Create a simplified server mode.
- Create realistic demo data.
- Perform manual testing on tablets.

Criteria:

- The demo is smooth, clear, and visually memorable.
- No serious console errors.
