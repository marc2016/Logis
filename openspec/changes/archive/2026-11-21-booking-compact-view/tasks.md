# Tasks

## 1. Settings store

- [x] 1.1 Add the migration for `core.user_preferences` and `core/preferences` with `GET`, `PUT` and `DELETE` and the limits (design D1). Verify with `PreferencesTest`:
  - read, set and overwrite;
  - delete;
  - own settings only;
  - bad key, value over 4 KB and more than 100 keys give 400 with „Die Einstellung ist ungültig.“;
  - updating at the limit works;
  - signed out gives 401.

  Verify the migration with a migration test (only additions; cascade on user delete).
- [x] 1.2 Add `usePreference` with one load and optimistic set (design D2). Verify with unit tests:
  - the fallback before loading and without a value;
  - a set is applied at once and sent;
  - a failed save keeps the value on the page.

## 2. List columns

- [x] 2.1 Make `DataTable` read and write hidden columns through `usePreference`, taking over and removing the old `localStorage` entry (design D3). Verify with `DataTable.test.tsx`:
  - a hidden column is saved as a setting;
  - a stored setting hides it;
  - the old browser entry is taken over once and removed.

## 3. Compact view

- [x] 3.1 Add `compact` to `BookingForm` (shown fields, differing or invalid fields stay, owner use) and the view switch on the booking page with the setting `vacationrental.bookingView` (design D4). Verify with `BookingPage` tests:
  - switch to compact and see only the five fields;
  - remembered for the next booking;
  - a differing guest name stays visible;
  - an owner use shows only the dates;
  - saving in compact keeps the hidden values;
  - the review mode keeps the view;
  - the new-booking dialog shows all fields.

## 4. End-to-end and checks

- [x] 4.1 Add `e2e/tests/booking-compact.spec.ts`:
  - choose „Kompakt“, change the rent and save;
  - open another booking in a new browser context of the same user and see „Kompakt“;
  - hide a list column and see it hidden after signing in again.

  Add a row to `e2e/COVERAGE.md` and the compact view to `layout.spec.ts`. Verify: these specs and `bulk-actions`, `inbox-bulk` and `booking-review` are green.
- [x] 4.2 Run `cd backend && ./mvnw test` and `cd frontend && npm test && npm run lint && npm run build`; all must be green.

## Workflow follow-up

- Archive with `/opsx:archive`; this creates the `user-preferences` spec and syncs `bookings`.
