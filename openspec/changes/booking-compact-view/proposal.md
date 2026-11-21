# Proposal

## Why

The booking page shows all of a booking's fields, about twenty of them:
- unit, agency and booking number;
- the guest's address, phone and e-mail;
- the documents.

When checking or correcting a booking, the owner mostly changes arrival, departure, adults, children and the rent. The header above the form already shows the rest. A compact view shows only these fields.

The owner wants such choices remembered per user, on every device, and expects more settings to follow. The column choice of the lists is remembered only per browser today.

## What Changes

- **Compact view:** the booking page offers „Kompakt“ and „Alle Angaben“.
  - „Kompakt“ shows only arrival, departure, adults, children and the expected rent; an owner use shows arrival and departure only.
  - A hidden field whose value differs from its document, or that has an error, stays visible.
  - The default is „Alle Angaben“.
  - The choice is remembered per user and also applies in the review mode.
  - The dialog for a new booking always shows all fields.
- **A store for user settings in the core:**
  - per user and key, a small JSON value;
  - loaded once and changed one key at a time;
  - keys are namespaced per module, so modules add settings without backend work.
- **List columns move into the store:** the column choice of every list (bookings, inbox, property finances, transactions) is remembered per user instead of per browser. A choice already stored in the browser is taken over on first use and then removed from the browser.

## Capabilities

### New Capabilities
- `user-preferences`: per-user settings, and the remembered column choice of lists as its first use.

### Modified Capabilities
- `bookings`: new requirement „Compact view“.

## Impact

- **Backend:**
  - a new core table `core.user_preferences` (new Flyway migration; no existing data changes);
  - `GET /api/preferences` and `PUT /api/preferences/{key}`.
- **Frontend:**
  - a `usePreference` hook in the core;
  - `DataTable` reads and writes column choices through it;
  - the booking page gets the view switch, and `BookingForm` a compact mode.
- **Tests:**
  - backend tests;
  - frontend tests;
  - an e2e journey and a row in `e2e/COVERAGE.md`.
