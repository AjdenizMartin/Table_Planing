# UI/UX Improvement Plan

## 1. Current problem

The application has two separate screens for managing reservations (`/reservations` and `/planning`), each with complementary but disconnected capabilities. Users must constantly navigate between them, losing operational context.

### Main symptoms

- 9 items in the main navigation + internal sub-navigation on several screens.
- PlanningPage (1,044 lines) contains all logic inline, without component separation.
- No status actions (confirm, cancel, seat) are available from planning.
- The "New reservation" button in planning navigates to another screen.
- The "Edit layout" button in planning navigates to another screen.
- HomePage displays debug data that is irrelevant to daily operations.
- There is no reusable side panel/overlay component.
- The selected date is not synchronized between planning and reservations.

---

## 2. Current screens

| # | Route | Component | Purpose | Problem |
|---|------|-----------|-----------|----------|
| 1 | `/` | HomePage | Welcome dashboard | Displays debug information that is not useful for operations |
| 2 | `/planning` | PlanningPage | Visual table floor plan | No status actions, 1,044 lines inline |
| 3 | `/reservations` | ReservationsPage | Reservation CRUD | No visual context, logically duplicates planning |
| 4 | `/customers` | CustomersPage | Customer list | Correct, but it should be accessible from planning |
| 5 | `/customers/:id` | CustomerDetailPage | Customer details | Correct |
| 6 | `/notifications` | NotificationsPage | Notification list | Accessible from the bell in the header |
| 7 | `/settings/restaurant` | RestaurantSettingsPage | Restaurant configuration | 1 of 5 settings screens |
| 8 | `/settings/dining-rooms` | DiningRoomsPage | Dining room management | Unnecessarily separate |
| 9 | `/settings/tables` | TablesPage | Table management | Unnecessarily separate |
| 10 | `/settings/layout` | TableLayoutEditorPage | Floor plan editor | Unnecessarily separate |
| 11 | `/settings/table-combinations` | TableCombinationsPage | Table combinations | Unnecessarily separate |

---

## 3. Current flow

### 3.1 View a reservation

```
PlanningPage (select reservation)
  → DetailPanel (right column, 360px)
    → Displays: name, time, party size, status, table, notes
    → Does NOT display: channel, phone number, confirmed-at, cancelled-at
    → Only available action: "Find best table" (if unassigned)
```

To view all data → go to `/reservations`, search for the date, and find the reservation.

### 3.2 Confirm / Cancel a reservation

```
PlanningPage
  → Navigate to /reservations?date=...
  → Find reservation in the list
  → Select reservation → ReservationDetailPanel opens
  → Click "Confirm" or "Cancel"
  → Return to /planning (loses scroll position, selection, and context)
```

**3 separate navigation steps for a simple action.**

### 3.3 Edit a reservation

There is no editing flow. The `PATCH /{id}` endpoint exists, but there is no UI. Users have no defined path for changing reservation data.

### 3.4 Create a reservation

```
PlanningPage
  → Click "New reservation"
  → Navigates to /reservations?mode=new&date=...
  → Completes form (name, phone number, time, party size, etc.)
  → Click "Create"
  → Returns to /planning (loses context)
```

### 3.5 Reassign table

```
PlanningPage
  → Select unassigned reservation → DetailPanel
  → Click "Find best table" (automatic assignment)
  → Or drag on FloorPlan (not yet implemented)
```

---

## 4. Recommended flow (Planning First)

### Principle

Users never leave `/planning` for daily operational tasks.

#### 4.1 View a reservation

```
PlanningPage
  → Select reservation in FloorPlan, Queue, or Timeline
  → ReservationSidePanel (right overlay, ~576px)
    → Displays: EVERYTHING (name, phone number, party size, date, time, duration,
      table, dining room, status, confirmation, channel, notes, accessibility)
    → Available actions based on status
```

#### 4.2 Confirm / Cancel / Seat / Complete

```
PlanningPage → ReservationSidePanel
  → Click "Confirm" → calls POST /confirm
  → Panel remains open with updated data
  → Planning refreshes in the background
  → Toast: "Reservation confirmed."
```

**0 navigation steps. 1 click.**

#### 4.3 Edit reservation

```
PlanningPage → ReservationSidePanel
  → Click "Edit details"
  → EditReservationModal (centered modal)
  → Edit party size, notes, accessibility
  → NO time field (protected)
  → Click "Save" → PATCH /{id}
```

#### 4.4 Create reservation

```
PlanningPage
  → Click "New reservation" in HeroHeader
  → CreateReservationPanel (overlay or modal, same pattern)
  → Complete minimum form (customer, time, party size)
  → Click "Create" → POST /reservations + auto-assign
  → Planning refreshes
```

#### 4.5 Reassign table

```
PlanningPage → ReservationSidePanel
  → Click "Reassign table"
  → POST /assign (existing)
  → Panel updates with the new table
  → FloorPlan refreshes
```

---

## 5. Required new components

| Component | Purpose | Based on |
|---|---|---|
| `ReservationSidePanel` | Side overlay with full details + quick actions | `InsightPanel` pattern |
| `EditReservationModal` | Modal for editing basic data (without time) | New |
| `PanelPrimitive` | Reusable slide-over/drawer with backdrop (future) | Extract from `InsightPanel` + `ReservationSidePanel` |

## 6. Existing reusable components

| Component | Location | Reused for |
|---|---|---|
| `ReservationDetailPanel` (actions) | `frontdesk/components/` | Confirm/cancel/seat/complete/no-show logic |
| `StatusPill` | `frontdesk/components/` | Display status in side panel |
| `StatusMessage` | `restaurant-config/components/` | Error/success messages |
| `ConfigCard` | `restaurant-config/components/` | Information sections |
| `Field` (TextField, SelectField...) | `restaurant-config/components/` | Forms |
| `getErrorMessage` | `restaurant-config/utils/` | API error parsing |
| `notify()` | `notifications/components/` | Success toast |
| `formatReservationCustomerName` | `frontdesk/utils/` | Full name |
| `formatReservationStatus` | `frontdesk/utils/` | Legacy status label formatting |
| `normalizeTimeForInput` | `frontdesk/utils/` | HH:mm format |

## 7. Screens to simplify

| Screen | Problem | Proposal |
|---|---|---|
| **HomePage (`/`)** | Displays session debug data; not useful | Redirect to `/planning` |
| **AppLayout nav** | 9 items; too many | Reduce to: Planning, Customers, Notifications, Config (grouped settings) |
| **ReservationsPage** | Duplicates planning functionality | Convert into an auxiliary search/historical archive view |
| **Settings (5 pages)** | Too fragmented | Group into 1–2 screens with tabs or sections |
| **PlanningPage** | 1,044 lines inline | Extract components into separate files |
| **OperationsShell** | Redundant sub-navigation (Customers/Reservations) | Remove once reservations are integrated into planning |
| **ConfigShell** | Settings sub-navigation | Keep but simplify |

## 8. Required quick actions

### From ReservationSidePanel:

| Action | Endpoint | Status |
|---|---|---|---|
| Confirm reservation | `POST /{id}/confirm` | ✅ Exists |
| Cancel reservation | `POST /{id}/cancel` | ✅ Exists |
| Mark arrived | `POST /{id}/arrived` | ✅ Exists |
| Mark seated | `POST /{id}/seat` | ✅ Exists |
| Mark finished | `POST /{id}/complete` | ✅ Exists |
| Mark no-show | `POST /{id}/no-show` | ✅ Exists |
| Send reminder | `POST /{id}/notifications/reminder` | ✅ Exists |
| Send confirmation SMS | `POST /{id}/notifications/confirmation` | ✅ Exists |
| Reassign table | `POST /{id}/assign` | ✅ Exists |
| Open edit modal | `PATCH /{id}` | ✅ Exists + implemented |

## 9. Technical risks

| Risk | Impact | Mitigation |
|---|---|---|
| The side panel overlays FloorPlan and may feel disruptive | Medium | Translucent backdrop, smooth animation, easy dismissal (click outside, Escape) |
| Multiple queries when opening a panel (reservation detail + customer) | Low | Queries are fast (GET by ID) and can be cached with TanStack Query |
| Inconsistency between planning summary and reservation detail | Medium | Use GET /reservations/{id} as the panel's source of truth; use planning summary only for visual display |
| Mutations from planning do not invalidate all caches | High | Always invalidate `["planning", ...]`, `["reservations", ...]`, and `["aiInsights", ...]` |
| Type mismatch between `PlanningReservationSummaryResponse` and `ReservationResponse` | Medium | Convert/map in the panel; do not mix types |
| Mobile: side panel does not work in portrait orientation | Low | Use a centered modal at <1024px instead of a side panel |
| The "Edit details" button could allow time changes if not protected | High | By design, `EditReservationModal` does NOT include date/time fields. Validation is reinforced in the backend |

## 10. Phased plan

### Phase 1 (this sprint) — Quick actions in planning

**Objective:** View full details and execute status actions from planning.

| Task | Files |
|---|---|
| Create `ReservationSidePanel` | `features/planning/components/ReservationSidePanel.tsx` |
| Add `sendReservationConfirmation()` to API | `features/frontdesk/api/frontdeskApi.ts` |
| Integrate panel into PlanningPage | `features/planning/pages/PlanningPage.tsx` |
| Create `EditReservationModal` | `features/planning/components/EditReservationModal.tsx` |
| Document | `docs/UI_UX_IMPROVEMENT_PLAN.md` |

**Criteria:** View full details; confirm, cancel, seat, complete, mark as no-show, reassign, send SMS reminders, and edit basic data from planning.

---

### Phase 2 — Creation panel in planning

**Objective:** Create new reservations without leaving planning.

| Task | Files |
|---|---|
| Create `CreateReservationPanel` (modal/overlay) | `features/planning/components/CreateReservationPanel.tsx` |
| Reuse `ReservationForm` logic | Extract validation + submit into a shared hook |
| "New reservation" button in HeroHeader → opens panel | `PlanningPage.tsx` |
| Auto-assign after creation | Existing flow |

**Criteria:** Create reservation, assign table automatically, view result in FloorPlan. No navigation.

---

### Phase 3 — Navigation simplification

**Objective:** Reduce navigation friction.

| Task | Files |
|---|---|
| HomePage redirects to `/planning` | `features/home/HomePage.tsx` → `router.tsx` |
| Reduce AppLayout navigation to 4–5 items | `components/layout/AppLayout.tsx` |
| Group settings into tabs (1–2 pages) | `features/restaurant-config/pages/*` |
| Extract inline components from PlanningPage | `features/planning/components/*` |

**Criteria:** Clean navigation, planning is the landing page, planning components are extracted.

---

### Phase 4 — Extract PanelPrimitive

**Objective:** Provide a reusable side panel component for the entire app.

| Task | Files |
|---|---|
| Extract `SlideOver` from `InsightPanel` and `ReservationSidePanel` | `src/components/ui/SlideOver.tsx` |
| Refactor `InsightPanel` to use it | `features/ai/components/InsightPanel.tsx` |
| Refactor `ReservationSidePanel` to use it | `features/planning/components/ReservationSidePanel.tsx` |

**Criteria:** Reusable SidePanel with animation, backdrop, dismissal via Escape/click outside, and responsive behavior.

---

### Phase 5 — Global state synchronization

**Objective:** The selected date and active reservation persist across views.

| Task | Files |
|---|---|
| Move `selectedDate` to context or URL search params | Context or router |
| Synchronize planning and reservations | Both consume the same source |
| Persist `selectedReservationId` in URL | `planning?date=...&reservationId=...` |

**Criteria:** Change date in planning → same date in reservations. Select reservation → URL reflects selection.

---

## 11. General acceptance criteria

- [x] I can view full reservation details from planning
- [x] I can confirm a reservation from planning
- [x] I can cancel a reservation from planning
- [x] I can mark a reservation as seated/finished/no-show from planning
- [x] I can mark a reservation as arrived from planning
- [x] I can send an SMS reminder from planning
- [x] I can edit basic data from planning
- [x] I can reassign a table from planning
- [x] The panel opens/closes with a smooth animation
- [x] The panel is responsive (side panel on desktop, modal on mobile)
- [x] Actions display loading/error/success states
- [x] Planning refreshes after each action
- [x] The reservation time never changes accidentally
- [x] `npm run build` works

---

## Appendix A: Files reviewed

### Frontend (59 .ts/.tsx files across 8 features)

```
src/
├── app/router.tsx                         (12 routes, navigation root)
├── components/layout/AppLayout.tsx         (9 navigation items, header, restaurant selector)
├── features/
│   ├── ai/
│   │   ├── components/InsightBar.tsx
│   │   ├── components/InsightPanel.tsx     (reusable slide-over used as a pattern)
│   │   └── hooks/useAiInsights.ts
│   ├── auth/
│   │   ├── context/AuthContext.tsx
│   │   └── pages/LoginPage.tsx
│   ├── frontdesk/
│   │   ├── api/frontdeskApi.ts            (CRUD + status transitions)
│   │   ├── components/
│   │   │   ├── OperationsShell.tsx         (redundant sub-navigation)
│   │   │   ├── ReservationDetailPanel.tsx  (complete status actions)
│   │   │   ├── ReservationForm.tsx         (creation with validation)
│   │   │   └── StatusPill.tsx
│   │   ├── pages/
│   │   │   ├── CustomersPage.tsx
│   │   │   ├── CustomerDetailPage.tsx
│   │   │   └── ReservationsPage.tsx       (341 lines, logical duplicate)
│   │   ├── utils/frontdeskUtils.ts
│   │   └── types.ts
│   ├── home/HomePage.tsx                  (debug, not operational)
│   ├── notifications/
│   │   ├── components/NotificationToast.tsx (notify() global)
│   │   └── pages/NotificationsPage.tsx
│   ├── planning/
│   │   ├── api/planningApi.ts
│   │   ├── components/PlanningGrid.tsx     (dead code, not imported)
│   │   ├── pages/PlanningPage.tsx          (1,044 lines, EVERYTHING inline)
│   │   └── types.ts
│   ├── realtime/
│   │   └── RealtimeProvider.tsx
│   └── restaurant-config/
│       ├── api/configApi.ts
│       ├── components/
│       │   ├── ConfigShell.tsx
│       │   ├── ConfigCard.tsx
│       │   ├── Field.tsx
│       │   └── StatusMessage.tsx
│       ├── hooks/useActiveRestaurant.ts
│       ├── pages/
│       │   ├── RestaurantSettingsPage.tsx
│       │   ├── DiningRoomsPage.tsx
│       │   ├── TablesPage.tsx
│       │   ├── TableLayoutEditorPage.tsx
│       │   └── TableCombinationsPage.tsx
│       ├── utils/errorMessage.ts
│       └── types.ts
└── services/api/client.ts                 (ApiClient + ApiError)
```

### Backend (relevant to endpoints)

```
backend/.../reservation/
├── api/ReservationController.java          (status endpoints)
├── api/ReservationResponse.java            (complete DTO)
├── domain/ReservationStatus.java           (enum: PENDING, CONFIRMED, SEATED, COMPLETED, CANCELLED, NO_SHOW)
└── service/ReservationService.java         (ensureTransitionAllowed, confirm, cancel, seat, complete, noShow)

backend/.../notification/
└── api/ReservationSmsNotificationController.java  (POST /notifications/confirmation)

backend/.../planning/
└── api/PlanningController.java             (GET planning, POST recalculate, POST move-reservation)
```

---

## Appendix B: Identified problems

### Critical

1. **Two parallel systems**: PlanningPage (visual read-only view) and ReservationsPage (actions only, without context). Users must constantly navigate between them.
2. **1,044 lines in PlanningPage**: All code is inline, without component separation, making it impossible to maintain in the long term.
3. **No status actions in planning**: The DetailPanel within planning only displays information and has a single "Find best table" button. Confirm, cancel, seat, and complete actions do not exist.

### High

4. **Useless HomePage**: Displays session debug data. It provides no operational value.
5. **Overloaded navigation**: 9 items in AppLayout + sub-navigation in OperationsShell and ConfigShell = too many options.
6. **Fragmented settings**: 5 separate configuration screens that could be grouped together.
7. **Type mismatch**: Planning uses `PlanningReservationSummaryResponse` (with `reservationId`), while frontdesk uses `ReservationResponse` (with `id`). Components cannot be shared across features without mapping.

### Medium

8. **No reusable side panel**: `InsightPanel` is a one-off. Each new overlay is implemented from scratch.
9. **OperationsShell duplicates navigation**: It has a sidebar with "Customers" and "Reservations" even though AppLayout already has those links.
10. **PlanningGrid.tsx is dead code**: Defined but not imported; it takes up space.
11. **The date is not synchronized**: Changing the date in planning does not affect the date in reservations, and vice versa.

### Low

12. **Missing animations/transitions**: Status changes and panel openings are instantaneous, with no visual feedback.
13. **Inconsistent responsive behavior**: Some screens have responsive layouts; others do not.
14. **Missing keyboard support**: Some buttons do not support keyboard interaction.

---

## Appendix C: What to implement first

### Priority 1: ReservationSidePanel with actions

Add to planning the status actions that currently exist only in frontdesk. This is the highest-impact change with the lowest risk.

**Files:**
- `frontend/src/features/planning/components/ReservationSidePanel.tsx` (new)
- `frontend/src/features/planning/components/EditReservationModal.tsx` (new)
- `frontend/src/features/planning/pages/PlanningPage.tsx` (modify)
- `frontend/src/features/frontdesk/api/frontdeskApi.ts` (add sendConfirmation)

### Priority 2: Simplify navigation

Reduce friction before adding more functionality.

### Priority 3: Extract PlanningPage components

Split the 1,044-line monolith into manageable files.

### Priority 4: Creation panel in planning

Complete the loop: manage the entire reservation lifecycle from planning.

---

## Appendix D: Progress

### Sprint 4: Complete quick actions in ReservationSidePanel (completed)

**Objective:** Make all status actions available from the planning side panel.

**New in the backend:**
- ✅ `ReservationStatus.ARRIVED` added with transitions: `PENDING/CONFIRMED → ARRIVED`, `ARRIVED → SEATED/CANCELLED/NO_SHOW`
- ✅ `ReservationService.arrived()` — endpoint `POST /{id}/arrived`
- ✅ `NotificationTemplateCode.RESERVATION_REMINDER` — new template
- ✅ `SmsNotificationService.sendReservationReminder()` — endpoint `POST /{id}/notifications/reminder`
- ✅ Backend compiles without errors

**New in the frontend:**
- ✅ `ReservationSidePanel.tsx` (~436 lines) — side overlay with full details + actions
- ✅ `EditReservationModal.tsx` — modal for editing party size, notes, and accessibility (without time)
- ✅ `ARRIVED` in frontend types + StatusPill + labels
- ✅ Escape key handler in side panel and modal
- ✅ Loading state for each individual action
- ✅ Clear error/success messages with toast notifications
- ✅ Query invalidation after each action

**Functional actions in ReservationSidePanel:**
- ✅ Confirm reservation → `POST /{id}/confirm`
- ✅ Cancel reservation → `POST /{id}/cancel`
- ✅ Mark arrived → `POST /{id}/arrived`
- ✅ Mark seated → `POST /{id}/seat`
- ✅ Mark finished → `POST /{id}/complete`
- ✅ Mark no-show → `POST /{id}/no-show`
- ✅ Send confirmation SMS → `POST /{id}/notifications/confirmation`
- ✅ Send reminder SMS → `POST /{id}/notifications/reminder`
- ✅ Reassign table → `POST /{id}/assign`
- ✅ Edit details → `EditReservationModal` → `PATCH /{id}`

**New files:**
- `frontend/src/features/planning/components/EditReservationModal.tsx`

**Modified files:**
- `frontend/src/features/planning/components/ReservationSidePanel.tsx` — edit → modal wiring, Escape key
- `frontend/src/features/frontdesk/api/frontdeskApi.ts` — `arrivedReservation()`, `sendReservationReminder()`
- `frontend/src/features/frontdesk/types.ts` — `ARRIVED` status
- `frontend/src/features/frontdesk/components/StatusPill.tsx` — ARRIVED styling
- `frontend/src/features/frontdesk/utils/frontdeskUtils.ts` — ARRIVED label
- `frontend/src/features/planning/pages/PlanningPage.tsx` — ARRIVED visuals
- `backend/.../reservation/domain/ReservationStatus.java` — ARRIVED
- `backend/.../reservation/service/ReservationService.java` — `arrived()`, transition rules
- `backend/.../reservation/api/ReservationController.java` — POST /arrived
- `backend/.../notification/domain/NotificationTemplateCode.java` — RESERVATION_REMINDER
- `backend/.../notification/service/SmsNotificationService.java` — reminder message
- `backend/.../notification/api/ReservationSmsNotificationController.java` — POST /reminder

**Next (Phase 2):**
- Create reservations from planning (`CreateReservationPanel`)
- Extract reusable `SlideOver` from `InsightPanel` + `ReservationSidePanel`

---

## Appendix E: What NOT to modify yet

- **Assignment algorithm** — It works, has tests, and does not need UX changes at this time.
- **Reservation backend endpoints** — They all exist, including `/arrived` and `/send-reminder`.
- **Reservation status logic** — `ensureTransitionAllowed` in the backend is correct and complete.
- **Notification logic** — The reminder scheduler works. We only add a call to `/notifications/confirmation`.
- **3D / drag-and-drop** — They are not on the immediate roadmap.
- **Multi-tenant** — The current architecture is correct and does not need changes.
- **External integrations** — WhatsApp, Google, etc. are a future phase.
