# Design

## Context

- **Suite setup.** The suite runs against the stack from `compose.yml` plus `e2e/compose.e2e.yml` (GreenMail).
  - It runs sequentially (`workers: 1`), with one retry in CI, in the projects `desktop` (1280 px) and `phone` (390 px).
  - `global-setup.ts` signs the admin in once and stores its session in `.auth/admin-state.json`.
- **Fixtures.**
  - The fixture `user` creates a fresh user through the admin API and signs it in in the page's context. `api` shares that context.
  - Every test sends its own `X-Forwarded-For` (`clientIp()`), because nginx limits sign-in requests per IP (10/min, burst 5).
- **Helpers.**
  - `data.ts`: `holidayHome`, `booking`, `inDays`.
  - `mail.ts`: `deliver` over SMTP and `fewoConfirmation`.
- **Lessons from the last runs.**
  - Dates relative to today made a test fail around month ends. New tests use fixed dates in a far year (2030 and later).
  - Unanchored text matches broke when a checkbox repeated a guest name. New tests use `exact: true` or scope the locator to a row or region.

## Goals / Non-Goals

**Goals:**
- One test per journey from the spec, independent of others and of today's date.
- Helpers that keep each test short and focused on the journey under test.

**Non-Goals:**
- Covering every spec scenario end to end. Rules and edge cases stay in backend and frontend tests.
- Parallel workers. Those come only if the run gets too long.
- Switching modules off end to end, because it needs another stack configuration.

## Decisions

### D1. Test files by area
These files are added to `e2e/tests/`:
- `sign-in.spec.ts`: back to sign-in, lockout.
- `account.spec.ts`: password, recovery codes.
- `admin.spec.ts`: user lifecycle, categories.
- `finances.spec.ts`: owners, accounts, transactions and transfer, filter and bulk assign.
- `properties.spec.ts`: property with units and costs, finances.
- `sharing.spec.ts`: viewer and editor.
- `booking-proposal.spec.ts`: change mail and apply.
- `agencies.spec.ts`: rename and merge.
- `evaluation.spec.ts`: Auswertung.
- `inbox-bulk.spec.ts`: assign a unit to several mails.

The existing files stay as they are.

### D2. Helpers
- `support/data.ts` gets:
  - `owner(api, name)`, which creates a legal entity of kind GBR;
  - `account(api, ownerId, name, opening)`;
  - `share(api, type, id, username, role)`, which calls the sharing endpoint by username;
  - `statement(api, unitId, provider, from, to)`;
  - `booking` with an optional expected rent (already added in `bulk-actions`).
- `support/fixtures.ts` gets:
  - `adminPage`: a page in a new context with `ADMIN_STATE` and its own client IP, for admin journeys in the UI;
  - `secondUser(browser, baseURL)`: creates and signs in a further user in its own browser context and returns `{ user, page, api }`. It is needed for sharing and closed by the test.
- `support/mail.ts` gets an optional message id and a changed departure for `fewoConfirmation`, so a change mail can follow a confirmation with the same booking number.

### D3. Rate limit and lockout
- The lockout test signs in five times with a wrong password and then once with the right one. That is six requests against `/api/auth/login`, within the burst. Each attempt still gets a fresh `X-Forwarded-For` through `page.setExtraHTTPHeaders`, as `auth.spec.ts` does. That way the test checks the user lockout of the application and never runs into the IP limit of nginx.
- Admin journeys that sign a new user in through the UI do the same.

### D4. Fixed data
- Bookings and statements use years 2030 and later, one unit per test (`holidayHome`), and booking numbers from `unique`. So tests never collide in the shared database, and they do not depend on today.
- The evaluation test asserts the figures of a month it filled itself: nights, occupancy, settled net and planned rent.

### D5. Coverage overview
- `e2e/COVERAGE.md` is a table with one row per capability from `openspec/specs/`. The columns are the end-to-end tests (file and test name), the frontend tests and the backend tests that cover the rest, and remarks.
- Rows without end-to-end coverage name the reason, for example `deployment` (headers and startup checks run in backend tests and the image build) and `module-system` (stack configuration).
- `CLAUDE.md` gets a line that a new user-facing journey needs an e2e test and an entry in `e2e/COVERAGE.md`.

## Risks / Trade-offs

- [Longer CI runs] → From about 1 to an estimated 3–4 minutes, still small next to the image build. Parallel workers stay possible later, because tests do not share data.
- [Flaky UI waits] → Use web-first assertions (`toBeVisible`, `toHaveText`), no fixed sleeps, and locators by role and name. One CI retry stays.
- [Admin state reused across tests] → Admin journeys only create their own users and categories with `unique` names and never change the admin itself.
