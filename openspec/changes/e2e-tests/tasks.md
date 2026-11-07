# Tasks

## 1. Setup

- [ ] 1.1 Create `e2e/` with `package.json`, `playwright.config.ts` (desktop and phone projects) and `compose.e2e.yml` with test settings and GreenMail (D1, D3). Verify: the stack starts with the override where Docker is available, and `npx playwright test --list` lists both projects.
- [ ] 1.2 Add the support code: API client, TOTP helper, admin setup in `globalSetup`, a fixture with a fresh signed-in user, data builders (D2). Verify: a smoke test signs in a fresh user and sees „Kalender“ in the navigation on both projects.

## 2. Flows

- [ ] 2.1 Sign-in: first sign-in, two-factor setup, recovery codes, sign out and sign in again. Verify: test green on both projects.
- [ ] 2.2 Booking: property via API, booking through the UI, bar in the calendar row, statement with the booking. Verify: test green on both projects.
- [ ] 2.3 Mail import: send an invented booking mail to GreenMail, set up the mailbox in the UI, fetch, confirm the proposed booking, see it in the calendar. Verify: test green on both projects.
- [ ] 2.4 Subscription: create a link without guest data in the dialog, fetch the feed without a session, parse it with `ical.js`, revoke and get `404`. Verify: test green on both projects.

## 3. CI and docs

- [ ] 3.1 Add the e2e job to `.github/workflows/images.yml` before the image job, with report upload on failure (D4), and describe the local run in `README.md`. Verify: `actionlint` reports no problems and the image job needs the e2e job.
- [ ] 3.2 After the user has pushed: the workflow run is green with the e2e job. Verify: the run on GitHub.
