# Proposal

## Why

The end-to-end suite has 7 tests:
- sign-in with 2FA;
- passkeys;
- a booking from the calendar to the statement;
- the mail import;
- the calendar subscription;
- bulk actions on bookings;
- a smoke test.

Most of what users do has never run in a real browser against the shipped stack. That covers finances, properties, sharing, user administration, the account page, agencies, the evaluation, the booking page with a proposed change, and the inbox bulk actions. Backend (292) and frontend (130) tests cover rules and page logic, but they mock the boundaries in between. Breaks there only show in production, as the image build failure and the strict-mode clash of the last days showed.

## What Changes

- **One end-to-end test per user journey**, run on desktop and phone like the existing ones. 12 new tests:
  - **Sign-in:** leave an unfinished sign-in with „Zurück zur Anmeldung“; lockout after five failed attempts.
  - **Mein Konto:** change the password, generate new recovery codes.
  - **Admin:** create a user, first sign-in of that user, disable, reset 2FA; create and archive a category.
  - **Finances:** create an owner and accounts, enter a transaction and a transfer, check the balances; filter transactions and assign a category and a property to several at once.
  - **Properties and sharing:** create a property with units and costs and see its finances; share a property with a viewer (no contact data) and a unit with an editor (only that unit).
  - **Vacation rental:**
    - a change mail proposes a new departure, applied on the booking page;
    - rename and merge agencies;
    - the evaluation shows nights and amounts;
    - assign a unit to several inbox mails at once.
- **Shared test helpers** for owners, accounts, sharing, an admin page and a second signed-in user in the same test.
- **Coverage overview** `e2e/COVERAGE.md`: per capability, which journeys the end-to-end suite covers and which layer (backend, frontend) covers the rest. Gaps become visible.
- **Not covered end to end:** switching a module off. It changes the configuration of the whole stack, which a test sharing that stack cannot do. The backend and frontend tests for disabled modules stay its coverage, and the overview says so.

## Capabilities

### New Capabilities

### Modified Capabilities
- `end-to-end-tests`: the list of main flows grows to the user journeys above, and the suite gets a coverage overview.

## Impact

- **e2e only:** new files under `e2e/tests`, new helpers in `e2e/support`, and `e2e/COVERAGE.md`. Application code and the database stay unchanged.
- **CI:** the e2e job grows from about 1 to an estimated 3–4 minutes. The suite stays sequential (`workers: 1`), and parallel workers are left for later.
