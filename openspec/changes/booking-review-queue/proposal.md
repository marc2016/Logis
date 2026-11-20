# Proposal

## Why

Reviewing imported bookings means switching between the booking list and the booking pages:
1. open a booking „zu prüfen“;
2. confirm it;
3. go back to the list;
4. click the next one.

The bulk action „Bestätigen“ skips this, but it deliberately works only for new bookings and without looking at each one. Proposed changes must be reviewed one by one anyway. The owner wants to click through the bookings to review, one after the other, while still seeing each booking with its mail.

## What Changes

- **A review mode („Prüfen“) on the booking page.** It shows a queue of all bookings „zu prüfen“ (new ones, proposed changes and cancellations), soonest arrival first, limited by the list's filters (e.g. one unit).
- **Navigation:** the page shows „3 von 12“, „Zurück“ and „Weiter“; the arrow keys ← and → do the same while no field has the focus.
- **Decisions:** after „Bestätigen“, „Übernehmen“, „Ablehnen“ or „Verwerfen“ the next booking opens by itself. Deciding stays a click: there is no key for it.
- **Unsaved edits:** leaving a booking with unsaved edits asks first.
- **End:** when nothing is left, the page says „Alles geprüft.“ and leads back to the list.
- **Entry:**
  - the booking list offers „Prüfen (n)“ while there are bookings to review;
  - „Buchungen prüfen“ on the start page opens the review mode directly.
- **Unchanged:** proposed changes still show old and new values and need their own „Übernehmen“.

## Capabilities

### New Capabilities

### Modified Capabilities
- `bookings`: new requirement „Review mode“.
- `dashboard`: „Tile catalog“. „Buchungen prüfen“ in „Zu erledigen“ opens the review mode instead of the list.

## Impact

- **Frontend:**
  - `modules/vacationrental`: the booking page, the review panel and the booking list;
  - a new route for the start of the review mode;
  - unsaved-edit tracking in `BookingForm`;
  - the dashboard todo source of the module.
- **Backend:** none. The queue uses the existing booking list API (`review=true`, sorted by arrival).
- **Tests:** frontend tests and a new e2e journey in `e2e/tests` with a row in `e2e/COVERAGE.md`.
