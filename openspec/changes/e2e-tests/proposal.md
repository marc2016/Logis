# Proposal

## Why

Unit and integration tests check the backend and the components in isolation. Whether the shipped images work together (nginx, backend, database, sign-in with two-factor codes, the public feed path) and whether the phone layout holds up has so far only been checked by hand with throwaway scripts. Images are about to be published automatically, so the main flows need an automatic check against the real stack before an image goes out.

## What Changes

- A Playwright suite in `e2e/` tests the main flows in a real browser against the production stack from `compose.yml`:
  - sign-in with two-factor setup and recovery codes,
  - property and booking up to the calendar and the statement,
  - mail import from a test mail server up to the confirmed booking,
  - calendar subscription: create a link, fetch and read the feed, revoke it.
- Every test runs on a desktop screen (1280 px) and on a phone screen (390 px).
- Each test creates its own users and data, so tests do not depend on each other or on their order.
- The GitHub workflow gets an end-to-end job: it builds the images for amd64, starts the stack with a test mail server and runs the suite. Images are only published when it passes.
- Locally the suite runs with one command against a running stack.

## Capabilities

### New Capabilities

- `end-to-end-tests`: Automatic browser tests of the main flows against the shipped stack, as a gate for publishing images.

### Modified Capabilities

(none)

## Impact

- **New:** `e2e/` (Playwright config, tests, helpers for sign-in and TOTP), `e2e/compose.e2e.yml` (test settings and GreenMail).
- **Changed:** `.github/workflows/images.yml` (end-to-end job, image job depends on it), `README.md` (how to run the suite).
- **Depends on:** the open change `container-images` (workflow and images); it should be archived first or together.
- **Not changed:** application code. If a flow cannot be tested without a change (for example a missing label), that is raised separately.
- **Assumptions** (proposed during exploration, not explicitly confirmed; easy to change):
  - Chromium only; Safari and Firefox are not tested.
  - The AI extraction stays disabled in the tests; only mails the rules recognise are imported.
  - Screenshots and traces are kept as workflow artifacts only for failed tests.
