# Tasks

## 1. Data

- [ ] 1.1 Add migration `V20261119.1500__core_sign_in_alerts.sql` with `core.sign_ins` and the columns `country` and `city` on `core.remembered_devices` (design D2, D6). Verify with a migration test that existing users and remembered devices keep all values and get `NULL` locations.

## 2. GeoIP

- [ ] 2.1 Add `com.maxmind.db:maxmind-db` (exact latest 3.x version), `GeoLocator`, `Location` and `MmdbGeoLocator` with the private-range rules (design D4). Add `MmdbTestWriter` in the tests. Verify with unit tests on a written test file:
  - 203.0.113.7 gives DE, „Deutschland“, „Hamburg“;
  - 198.51.100.7 gives ES;
  - private, loopback, CGNAT, benchmarking and unknown addresses give empty;
  - a missing file gives empty.
- [ ] 2.2 Add `GeoIpUpdater` with the settings, the download at startup and on the 3rd of each month, month fallback, gzip, temp-and-rename, reader swap and skip-if-current (design D5). Verify with a test against a local HTTP server (JDK `HttpServer`):
  - download and swap;
  - 404 for the current month falls back to the previous month;
  - a failure keeps the old file and logs;
  - an up-to-date file is not downloaded again;
  - `enabled=false` downloads nothing.

## 3. Sign-in monitoring

- [ ] 3.1 Add the device cookie, `SignInMonitor` with the decision, the history row and the clean-up, and call it from `AuthController.completed` (design D1–D3). Verify in `SignInAlertsTest` (integration, stub `GeoLocator`, GreenMail):
  - the first sign-in sends nothing;
  - a second sign-in in the same browser sends nothing;
  - a sign-in without the device cookie sends one mail with time, browser, OS and location, and without the client address;
  - a new country with a known device sends one mail;
  - a local address never counts as new location;
  - a user without an address and an unconfigured mail server send nothing;
  - an unreachable mail server does not fail the sign-in;
  - passkey sign-in is monitored too;
  - the stored row has no IP;
  - rows older than 180 days are deleted by the clean-up.

## 4. Remembered devices

- [ ] 4.1 Store country and city on issue and restore, reject a restore from another known country, and return the location in the device list (design D6). Verify in `RememberMeTest`:
  - restore from another country gives 401 and forgets the device;
  - an unknown country on either side is not compared;
  - the device list shows city and country name.
- [ ] 4.2 Show the location in „Angemeldete Geräte“. Verify in `AccountPage.test.tsx` that „Hamburg, Deutschland“ appears, and nothing when the location is unknown. Run `npm run lint`, `npm test` and `npm run build` in `frontend` and verify all pass.

## 5. Deployment, end to end and documentation

- [ ] 5.1 Create `/var/lib/logis/geoip` for `logis` in the backend Dockerfile, mount the `geoip` volume and pass `LOGIS_GEOIP_ENABLED` in `compose.yml`, set it to `false` in `e2e/e2e.env`, and add the dev-profile directory (design D7). Verify with `docker compose config` that the volume and variable are present.
- [ ] 5.2 Add `e2e/tests/sign-in-alerts.spec.ts`:
  - set up the outgoing mail server;
  - sign in a user once;
  - sign in again in a fresh browser context;
  - read the warning mail „Neue Anmeldung bei Logis“ from the test mailbox;
  - sign in a third time in the same context and verify no second mail.

  Add a `sign-in-alerts` row to `e2e/COVERAGE.md`. Verify the spec passes against the compose stack.
- [ ] 5.3 Document the warning mails, the GeoIP download with volume and switch, and the DB-IP credit in the README. Verify the text matches the spec wording.
- [ ] 5.4 Run `cd backend && ./mvnw test` and verify the whole suite passes.

## Workflow follow-up

- Archive the change after review (`/opsx:archive`).
