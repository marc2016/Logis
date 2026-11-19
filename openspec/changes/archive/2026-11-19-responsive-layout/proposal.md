# Proposal

## Why

On tablet widths, and in places on phones, controls run into each other. A review of every page at 360–1280 px (signed in as the dev user `anna`, sidebar open) found:

- **Bookings filter bar** („Buchungen“): from 1024 px up to about 1300 px, all eight filters sit in one row. The „Objekt“ and „Einheit“ selects overlap, the status select shows two chevrons, the dates read „mm/c“ and the search field „Buchu“.
- **Transactions filter** („Transaktionen“) at 1024 px: „Von“ and „Bis“ are cut off („mm/dd/y“).
- **Inbox filter** („Posteingang“) at 1024–1180 px: „Eingang von“ and „Eingang bis“ are only 74–100 px wide.
- **Booking page** at 1024 px: the form and the source panel sit side by side, so the form gets about 260 px and its date fields are cut off.
- **Sharing dialog** („Freigaben: …“) on the phone: the role select, the remove button, the user picker and „Einladen“ stick out past the right edge of the dialog.

The cause is the same everywhere: layouts switch to more columns at a *viewport* breakpoint (`lg` = 1024 px), but the open sidebar takes about 256 px, so the content area is only about 770 px wide at that point. The other pages and dialogs looked fine; wide tables scroll sideways inside their frame, which stays as it is.

## What Changes

- Multi-column filter bars and page layouts switch columns based on the width of the content area, not of the window, so they behave the same with the sidebar open or collapsed.
- The bookings filter bar wraps to as many rows as needed instead of forcing eight columns.
- Date inputs and selects in filter bars keep a minimum width at which their value is readable.
- The booking page shows form and source side by side only when the content area is wide enough; below that it keeps the tabs „Daten“ and „Quelle“.
- The sharing dialog fits on a phone: rows with role select and remove button stay inside the dialog, the invite controls stack.
- A layout check in the end-to-end suite opens the main pages at phone, tablet and tablet-landscape width and fails when controls overlap or stick out.

## Capabilities

### New Capabilities

_None._

### Modified Capabilities

- `app-shell`: new requirement that pages and dialogs fit the content area at phone and tablet widths, without overlapping or cut-off controls.
- `end-to-end-tests`: new requirement for a layout check at phone and tablet widths.

## Impact

- Frontend only, no backend, API or database changes.
- `frontend/src/app/layout/AppShell.tsx` (content area becomes a size container).
- `frontend/src/modules/vacationrental/pages/BuchungenPage.tsx`, `frontend/src/modules/vacationrental/pages/BookingPage.tsx`, `frontend/src/core/ledger/transactions/TransactionsPage.tsx`, `frontend/src/core/mail/InboxFilters.tsx`, `frontend/src/components/ShareDialog.tsx`; other `lg:`/`xl:` grids in pages reviewed for the same issue.
- New `e2e/tests/layout.spec.ts`, entry in `e2e/COVERAGE.md`.
