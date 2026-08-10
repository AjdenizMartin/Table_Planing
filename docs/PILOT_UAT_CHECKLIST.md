# Pilot UAT Checklist

## Data and access

- [ ] Domain and HTTPS validated.
- [ ] Restaurant, time zone, dining areas, and 40 tables reviewed.
- [ ] Inventory and 20 combinations reviewed by the manager.
- [ ] Owner, manager, and staff access the system with individual accounts.
- [ ] Demo credentials and public registration are unavailable.
- [ ] Repeated onboarding does not duplicate users or change passwords.

## Manager workflow

- [ ] Create a reservation and confirm that the time does not change.
- [ ] Open the top 3 without creating or modifying an assignment.
- [ ] Compare capacity, score, cost, preparation, and resources.
- [ ] Apply an advanced combination and view it in planning.
- [ ] Confirm the actor, timestamp, explanation, and resources in the history.
- [ ] Reassign and verify that the previous resource consumption is released.
- [ ] Simulate two simultaneous selections against limited inventory.

## Staff workflow

- [ ] View planning, table, preparation, and assigned resources.
- [ ] Change arrival, seated, and completed statuses according to permissions.
- [ ] Confirm that the action to approve suggestions is not displayed.
- [ ] Complete the workflow in Chrome on a physical Android tablet.
- [ ] Validate touch input, the virtual keyboard, rotation, and reconnection after suspending the tablet.

## Resilience

- [ ] Run 150 reservations/40 tables/20 combinations.
- [ ] Planning responds in under 2 seconds on the VPS.
- [ ] Suggestions respond in under 1 second on the VPS.
- [ ] Daily backup created and copied off the VPS.
- [ ] Restic validates the encrypted repository without errors.
- [ ] Restoration completed and documented.
- [ ] Rollback rehearsed with the previous version.
- [ ] Two concurrent shifts completed using the manual procedure as a fallback.
- [ ] CI fully green and zero open critical incidents.

Signatures: owner, manager, technical lead, and approval date.
