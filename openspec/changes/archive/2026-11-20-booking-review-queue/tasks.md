# Tasks

## 1. Queue and navigation

- [x] 1.1 Add the review queue hook (live `review=true`, `sort=arrival,asc`, list filters; position, count, next/previous, the next entry after a decided booking) (design D1, D2). Verify with unit tests for position, next, previous, after-decision and the empty queue.
- [x] 1.2 Show the review bar on `BookingPage` in review mode: „x von n“, „Zurück“, „Weiter“, „Zur Liste“, and „Alles geprüft.“ when empty (design D2, D5). Give `ReviewPanel` an `onDecided` callback that moves on. Verify with `BookingReview` tests:
  - click through 3 bookings;
  - confirm moves on and the count drops;
  - a proposed change moves on only after „Übernehmen“/„Verwerfen“;
  - the last decision shows „Alles geprüft.“
- [x] 1.3 Add ←/→ in review mode, ignored while a field, dialog or popover has the focus, and the unsaved-edits dialog with `BookingForm.onDirtyChange` (design D4). Verify with tests:
  - arrow keys move;
  - an arrow in a text field does not;
  - a dirty form asks, and „Abbrechen“ keeps the edits.

## 2. Entry points

- [x] 2.1 Add „Prüfen (n)“ to the booking list with the list's filters, and the route `/ferienvermietung/pruefen` (design D3). Verify with tests:
  - the button appears only with bookings to review and carries the filters;
  - the route opens the first booking or shows „Alles geprüft.“
- [x] 2.2 Point the dashboard's „Buchungen prüfen“ to `/ferienvermietung/pruefen`. Verify: the `Dashboard` test expects the new link.

## 3. End-to-end and checks

- [x] 3.1 Add `e2e/tests/booking-review.spec.ts`:
  - two imported bookings to review (one new, one change via mail, as in `booking-proposal.spec.ts`);
  - „Prüfen (2)“, click through, confirm, apply, „Alles geprüft.“

  Add a row to `e2e/COVERAGE.md` and the review mode to `layout.spec.ts`. Verify: these specs and `booking-proposal`, `bulk-actions` and `dashboard` are green.
- [x] 3.2 Run `cd frontend && npm test && npm run lint && npm run build`; all must be green.

## Workflow follow-up

- Archive with `/opsx:archive` and sync the `bookings` and `dashboard` specs.
