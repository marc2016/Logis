# Design

## Context

The stack in `compose.yml` consists of nginx (frontend image, the only published port), the backend, Postgres and a backup service; secrets come from `.env`. Every sign-in requires a password and a TOTP code; the initial admin comes from `LOGIS_ADMIN_*`. Mailboxes are configured per user in the UI, with host, port and a TLS switch. The workflow `.github/workflows/images.yml` (change `container-images`) runs the backend and frontend tests and then publishes multi-arch images. Throwaway Playwright scripts from earlier changes already sign in by computing TOTP codes and create data through the API. Motivation is in proposal.md.

## Goals / Non-Goals

**Goals:**
- A small, stable smoke suite over the four main flows on two screen sizes.
- The same command locally and in CI.

**Non-Goals:**
- Visual regression (pixel comparison).
- Covering every edge case; those stay in the integration and component tests.
- Testing the AI extraction or real mail providers.

## Decisions

### D1: Layout and runner

```
e2e/
  package.json            @playwright/test, ical.js
  playwright.config.ts    projects: desktop (1280x800), phone (390x844, isMobile)
  compose.e2e.yml         override: test .env values, GreenMail on the egress network
  support/                api client, sign-in with TOTP, data builders
  tests/*.spec.ts         auth, booking-calendar, mail-import, subscription
```

`baseURL` from `LOGIS_E2E_URL` (default `http://localhost:8080`). Own `package.json` so the frontend does not carry Playwright. `retries: 1` in CI, `trace: 'retain-on-failure'`, `screenshot: 'only-on-failure'`.

### D2: Sign-in and test data

A fixture creates a fresh user per test through the admin API (the admin's TOTP secret is set up once in `globalSetup` and kept in a file under `e2e/.auth/`, ignored by git). TOTP codes are computed with `node:crypto` (RFC 6238). Because a code can only be used once, the helper waits for the next 30-second window when needed. Properties, units and bookings are created through the API; only the flow under test goes through the UI. Names carry a random suffix.

### D3: Stack for the tests

`docker compose -f compose.yml -f e2e/compose.e2e.yml up -d --build --wait`. The override sets test values (database password, admin, encryption key, `LOGIS_AI_ENABLED=false`, port 8080 on localhost) and adds `greenmail/standalone` on the `egress` network with plain IMAP and SMTP. Tests deliver sample mails (invented guests only) via SMTP from the runner through a published GreenMail SMTP port, and configure the mailbox with host `greenmail`, TLS off.

### D4: Workflow

```
test-backend --+
test-frontend -+--> e2e (build amd64 with GHA cache, compose up, playwright, upload report on failure)
                                   |
                                   v
                     images (multi-arch, push) -- only on push
```

The e2e job runs on pull requests too. It uses the same buildx GHA cache scopes as the image job, so the second build mainly reuses layers. The Playwright browser comes from `npx playwright install --with-deps chromium` on the runner.

## Risks / Trade-offs

- [Flaky timing (polling, TOTP windows)] → Waiting on UI state instead of fixed sleeps; one retry in CI; TOTP helper avoids reusing a code.
- [Longer CI run] → About 5–8 extra minutes; layers are cached and the image job reuses them.
- [Sample mails with personal data] → Only invented guests and addresses in the fixtures.
- [Order with the open change `container-images`] → This change edits the same workflow; archive `container-images` first.
