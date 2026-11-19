# Tasks

## 1. Data and configuration

- [ ] 1.1 Add migration `V20261119.1200__core_remembered_devices.sql` with `core.remembered_devices` (design D11). Verify with a migration test that existing users stay unchanged and that Hibernate validates.
- [ ] 1.2 Add `logis.auth.remember-me` (`sliding` 30d, `absolute` 90d, `reuse-grace` 1m) to `AuthProperties` and `application.yml` (design D5). Verify with a properties binding test for defaults and overrides.

## 2. Remembered-device core

- [ ] 2.1 Add the user-agent parser that reduces a `User-Agent` to browser and OS families (design D4). Verify with a unit test covering current Firefox, Chrome, Edge, Safari (macOS, iOS), Samsung Internet and Android Chrome strings, plus an unknown string.
- [ ] 2.2 Add the store and `RememberedDeviceService` with issue, restore (hash check, rotation, grace window, reuse detection, family binding, durations, `sessionVersion` check) and cookie handling (design D2–D6). Verify with unit tests using a fixed `Clock`:
  - issue and restore rotate the token;
  - the previous token is accepted within 60 s without a new cookie, and rejected after 60 s with the series deleted;
  - a different browser/OS family deletes the series;
  - restore fails after 30 days unused, after 90 days in total, for a disabled user and after a `sessionVersion` change;
  - only hashes are stored.
- [ ] 2.3 Add the daily clean-up job for expired and stale-version rows (design D5). Verify with a test that calls the job and checks which rows remain.

## 3. Sign-in, restore and sign-out

- [ ] 3.1 Accept `rememberMe` on `/api/auth/login` and `/api/auth/passkey`, and call `onAuthenticated` from every endpoint that returns `AUTHENTICATED` (`/totp/confirm`, `/totp`, `/password`, the dev bypass in `/login`, `/api/auth/passkey`) (design D7). Verify in `RememberMeTest` (integration):
  - sign-in with password and code and with a software passkey sets `LOGIS_REMEMBER` only when requested;
  - a sign-in stopped at `PASSWORD_CHANGE_REQUIRED` sets no cookie;
  - a new sign-in replaces an existing device cookie.
- [ ] 3.2 Restore sessions in `SessionAuthenticationFilter` (design D8), including the hourly `last_used_at` refresh. Verify in `RememberMeTest`:
  - after discarding the session cookie, a protected endpoint and `/api/auth/me` succeed with the device cookie alone;
  - parallel requests with the same cookie all succeed;
  - a replayed old cookie gives 401 and also ends the real device;
  - a different `User-Agent` gives 401;
  - `POST` sign-in steps do not restore.
- [ ] 3.3 Make `/api/auth/logout` forget the current device and clear the cookie (design D10). Verify in `RememberMeTest` that the device cookie no longer restores after sign-out.
- [ ] 3.4 Keep the current device on own password change and end all others (design D6). Verify in `RememberMeTest` that after a password change the other device cookie gives 401 and the current one still restores. Verify in `AdminUsersTest` that disable, password reset and TOTP reset make a device cookie give 401.

## 4. Admin area and device endpoints

- [ ] 4.1 Deny `/api/admin/**` to restored sessions with 403 and ProblemDetail type `urn:logis:reauthentication-required`, and add `POST /api/auth/reauthenticate` with `SKIP_RESTORE` (design D9). Verify in `RememberMeTest`:
  - a restored admin session gets 403 with that type on `/api/admin/users`, and 200 on a domain endpoint;
  - after `/reauthenticate`, `/api/auth/me` returns `ANONYMOUS`;
  - a fresh sign-in gets 200 on `/api/admin/users`.
- [ ] 4.2 Add `GET /api/account/devices`, `DELETE /api/account/devices/{id}` and `POST /api/account/devices/sign-out-others` (design D10). Verify in `RememberMeTest`:
  - the list shows only own, current-version devices with `current` set correctly;
  - deleting a device makes its cookie give 401;
  - deleting another user's device gives 404;
  - sign-out-others ends other sessions and devices and keeps the current one.

## 5. Frontend

- [ ] 5.1 Add the checkbox „Angemeldet bleiben“ to the sign-in page and send `rememberMe` with the password and the passkey sign-in. Verify in `LoginPage.test.tsx` and `Passkeys.test.tsx` that the flag is sent as checked and is off by default.
- [ ] 5.2 Add the card „Angemeldete Geräte“ to the account page: list with browser, OS, sign-in date, last use and „Dieses Gerät“, „Abmelden“ per device, „Alle anderen Geräte abmelden“. Verify in `AccountPage.test.tsx` with a mocked API: list rendering, the current-device marker, and the calls of both actions with refreshed list.
- [ ] 5.3 Recognise `urn:logis:reauthentication-required` in `api.ts` and show „Für den Admin-Bereich bitte erneut anmelden“ with „Erneut anmelden“ on the admin pages. The button calls `/api/auth/reauthenticate` and shows the sign-in page with „Angemeldet bleiben“ pre-checked. Verify in `UsersPage.test.tsx` that the notice appears for that response and that the button calls the endpoint.
- [ ] 5.4 Run `npm run lint`, `npm test` and `npm run build` in `frontend` and verify all pass.

## 6. End to end and documentation

- [ ] 6.1 Add `e2e/tests/remember-me.spec.ts`: sign in with „Angemeldet bleiben“, delete the session cookie, reload and still see the overview; sign in on a second browser context, sign it out under „Angemeldete Geräte“ and verify it shows the sign-in page after losing its session. Add the journey to the `authentication` row of `e2e/COVERAGE.md`. Verify the spec passes against the compose stack.
- [ ] 6.2 Document `logis.auth.remember-me` and the `LOGIS_REMEMBER` cookie in the README section on sessions/security. Verify the text matches the defaults in `application.yml`.
- [ ] 6.3 Run `cd backend && ./mvnw test` and verify the whole suite passes, including the Modulith structure test.

## Workflow follow-up

- Archive the change after review (`/opsx:archive`).
- Follow-up changes: outgoing mail (SMTP, user e-mail address), then the new device/location warning.
