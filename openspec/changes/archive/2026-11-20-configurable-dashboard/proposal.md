# Proposal

## Why

The start page „Übersicht“ is a placeholder („Hier entsteht die Übersicht über Konten, Objekte und Buchungen.“). Users open Logis and first have to navigate to see what needs attention: mails waiting for a unit, bookings to review, upcoming arrivals, account balances. Different users care about different units and accounts, so one fixed page would not fit everyone.

## What Changes

- **The start page becomes a dashboard of tiles.** Each user arranges their own tiles:
  - add, remove and reorder them;
  - set each tile's settings, e.g. which unit or which accounts;
  - a tile type may appear more than once.
- **The arrangement is stored per user in the backend**, so it is the same on every device. Users who never customised it see a default.
- **Tiles of the first version:**
  - „Zu erledigen“: inbox mails that need action, and bookings to review;
  - „Nächste An- und Abreisen“: 7, 14 or 30 days; one unit or all;
  - „Belegung“: current year; one unit or all; optionally the previous year;
  - „Einnahmen“: settled net this year compared with the same period last year; one unit or all;
  - „Kontostände“: all or selected accounts;
  - „Letzte Transaktionen“: 5 or 10; one account or all.
- **Modules contribute their own tiles.** The vacation rental tiles exist only while that module is active.
- **Tiles show only what the user may see.** A tile whose setting points to something no longer visible says so instead of showing data.

## Capabilities

### New Capabilities
- `dashboard`: the configurable start page. It covers:
  - the tile catalog;
  - per-user arrangement and settings;
  - defaults;
  - visibility of tile data.

### Modified Capabilities

## Impact

- **Backend:**
  - a new core table for the per-user arrangement (new Flyway migration in `core`; existing data is untouched);
  - an API to read and save the arrangement.
- **Frontend:**
  - `core/dashboard` with the tile framework, the edit mode and the core tiles;
  - `LogisModule` gets `dashboardWidgets`;
  - `modules/vacationrental` gets its tiles.
- **Tests:**
  - backend tests for storage and validation;
  - frontend tests per tile and for the edit mode;
  - a new e2e test in `e2e/tests` and a row in `e2e/COVERAGE.md`, since this is a new user journey.
