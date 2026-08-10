# Planning Panel Vision

## 1. Panel objective

The planning panel should be the product's main screen: a touch-friendly command center where the restaurant can view dining rooms, tables, reservations, availability, conflicts, and improvement opportunities during service.

The experience should convey that Restaurant Table Planning is not a reservation calendar, but an intelligent tool for organizing service as an operational puzzle.

## 2. Problems it solves

- Prevents simplistic assignments that waste large tables.
- Makes pending, unassigned, and operationally at-risk reservations visible.
- Makes it possible to understand occupancy by dining room and time slot.
- Reduces repetitive manual decisions during service.
- Gives the manager a clear, touch-friendly view for operating from a tablet.
- Prepares for future optimization suggestions without delegating critical decisions to AI.

## 3. Critical rule: change the table, not the time

The application must never automatically change the time of an existing reservation.

Allowed:

- Move a reservation between tables while retaining exactly the same `reservation_date`, `start_time`, and `end_time`.
- Move a reservation between dining rooms if the original time is retained.
- Reassign a reservation to a valid table combination.
- Optimize table assignments without altering schedules.

Not allowed:

- Drag a reservation to another time.
- Optimize by shifting reservations.
- Recommend that a customer change time as an automatic action.
- Change the time outside the explicit reservation edit form.

The frontend must clearly communicate this rule, but definitive validation must reside in the backend.

## 4. Required views

- Main visual floor plan view by dining room.
- List view of the day's reservations with search and filters.
- Read-only timeline view by table and time.
- Unassigned reservations view.
- Selected table or reservation details view.
- Future layout editor view.
- Future live service view.
- Future optimization suggestions view.

## 5. Proposed UX

The screen is organized into four areas:

- Top header: date, shift, dining room, occupancy, reservations, guests, pending items, primary actions, and realtime status.
- Left panel: the day's reservations, filters, search, pending items, and unassigned reservations.
- Central panel: touch-friendly dining room floor plan with tables, states, reservations, and alerts.
- Right panel: contextual table or reservation details with quick actions.
- Bottom timeline: reservations, buffers, and gaps by time, without horizontal dragging.

UX principles:

- Large, clear actions for tablets.
- States represented by color, label, and textual icon.
- Immediate table or reservation selection.
- Important information always visible.
- Subtle microinteractions without visual overload.
- Clear separation between service mode and edit mode.

## 6. Comparison of 2D, 2.5D, and 3D

| Option | Visual impact | Tablet | Complexity | Maintainability | Recommendation |
| --- | --- | --- | --- | --- | --- |
| Premium 2D HTML/SVG | High | Excellent | Low-medium | High | Ideal for Phase 1 |
| 2.5D isometric CSS/SVG | Very high | Very good | Medium | Good | Ideal for an advanced MVP |
| Canvas/Konva/Fabric | High | Good | Medium-high | Medium | Valuable for an advanced touch editor |
| React Flow | Medium | Medium | Medium | Good | Better for graphs, not for a floor plan |
| Three.js/R3F | Very high | Variable | High | Medium-low | Future showroom, not an operational MVP |

## 7. Final technical recommendation

First build a premium 2D floor plan with a visual language designed to evolve into 2.5D. Use native HTML/CSS/SVG for Phase 1 and keep the data in clear TypeScript models.

Do not introduce Three.js or React Three Fiber yet. Demo value is better achieved with a stable, touch-friendly, attractive interface than with a 3D scene that is difficult to operate.

## 8. Recommended libraries and rationale

- Phase 1: no new libraries. React, TypeScript, Tailwind, and TanStack Query are sufficient.
- Phase 2/3: `dnd-kit` for moving tables and reservations between tables, because it works well with React and supports axis/zone constraints.
- Optional for Phase 2/3: `react-konva` if the editor needs multi-select, snap, rotation, walls, and complex decorative objects.
- Timeline: custom CSS Grid. FullCalendar is powerful, but tends to impose calendar semantics with temporal dragging, precisely what we want to avoid.
- Future 3D: React Three Fiber only for presentation mode, not for daily operations.

## 9. Proposed frontend structure

```text
frontend/src/features/planning/
  api/
  components/
    PlanningHeroHeader.tsx
    ReservationQueue.tsx
    FloorPlanCanvas.tsx
    TableCard.tsx
    ReservationDetailPanel.tsx
    ReadOnlyTimeline.tsx
    PlanningStatusLegend.tsx
  pages/
    PlanningPage.tsx
  types.ts
  utils/
    planningStatus.ts
```

Future:

```text
features/floor-plan/
features/live-service/
features/optimization/
```

## 10. Required backend endpoints

Already exist:

- `GET /api/restaurants/{restaurantId}/planning?date=YYYY-MM-DD`
- `POST /api/restaurants/{restaurantId}/planning/recalculate`
- `POST /api/restaurants/{restaurantId}/planning/move-reservation`
- `POST /api/restaurants/{restaurantId}/reservations/{reservationId}/assign`

Required for future phases:

- `POST /api/restaurants/{restaurantId}/planning/validate-table-move`
- `POST /api/restaurants/{restaurantId}/planning/simulate`
- `GET /api/restaurants/{restaurantId}/planning/live`
- `PATCH /api/restaurants/{restaurantId}/tables/{tableId}/layout`
- `POST /api/restaurants/{restaurantId}/tables/{tableId}/block`
- `POST /api/restaurants/{restaurantId}/tables/{tableId}/unblock`
- `GET /api/restaurants/{restaurantId}/optimization/suggestions?date=YYYY-MM-DD`

Duplicate endpoints must not be created if the existing ones cover the use case.

## 11. Required data model

Currently required:

- `DiningRoom`
- `RestaurantTable`
- `Reservation`
- `ReservationAssignment`
- `PlanningDayResponse`
- `PlanningConflict`

Future:

- `TableBlock`
- `TableStatusEvent`
- `LayoutObject`
- `OptimizationSuggestion`
- `PlanningSimulation`
- `WaitlistEntry`

## 12. Implementation phases

1. Functional and stable planning.
2. Touch-friendly visual dining room and table editor.
3. Drag and drop reservations between tables while retaining the time.
4. Premium 2.5D/isometric view.
5. Live service mode.
6. Visual optimization and suggestions without time changes.
7. Tablet polish and usage modes.

## 13. What to include in the MVP

- Demo login.
- Daily planning with date and dining room.
- Visual floor plan by dining room.
- Reservation list and unassigned reservations.
- Clear visual states.
- Details panel.
- Read-only timeline.
- Existing automatic assignment action.
- Clear error messages.

## 14. What to leave for future phases

- Drag and drop.
- Advanced editor for walls, objects, and rotation.
- Full 2.5D.
- Comprehensive live service mode.
- What-if simulations.
- Before/after comparison.
- WhatsApp.
- Generative AI.
- Occupancy prediction.

## 15. Technical risks

- Slow or blocking planning backend if queries load too many relationships.
- Accidental drag and drop that changes the time if a generic calendar is used.
- Excessive visual elements that reduce clarity on tablets.
- Duplicated state across the floor plan, timeline, and side panel.
- Frontend business rules that do not match the backend.
- Reassignments without tenant/role validation.

Mitigation:

- Backend as the source of truth.
- Read-only timeline.
- Mutations only through reassignment endpoints.
- Clear shared types.
- Small, testable components.
- Do not introduce heavy libraries before they are needed.

## 16. How to validate it in a demo

- Log in with `demo@restaurant.com`.
- Open `Planning`.
- Change the date and dining room.
- View 3 demo dining rooms and tables.
- Select a table and reservation.
- View pending and unassigned reservations.
- Show that the timeline does not allow schedule changes.
- Explain that optimization only moves reservations between tables.
- Show states, occupancy, and guests.
