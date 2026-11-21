# Tasks

## 1. Notification core

- [x] 1.1 Add the migration `V<yyyyMMdd>.<HHmm>__core_notifications.sql` with `core.notification_settings`, `core.pushover_settings` and `core.pushover_user_keys` (design D3); verify with a `NotificationsMigrationTest` that it applies on a database with existing users and leaves their rows unchanged
- [x] 1.2 Create `core.notifications` with the public types `NotificationType`, `Notification`, `Notifications` and the internal `NotificationChannel` SPI and registry (D1, D2); verify that the Spring Modulith structure test passes and a unit test rejects two types with the same key
- [x] 1.3 Implement sending after commit in the background with per-channel failure isolation and filtering to active users (D4); verify with `NotificationsTest` covering: nothing sent on rollback, one failing channel does not stop the other, disabled user skipped, log line without the text
- [x] 1.4 Implement the settings store with defaults and mandatory channels, and `GET /api/notifications`, `PUT /api/notifications/{type}/{channel}` (D10); verify with `NotificationSettingsTest` covering own switches only, HTTP 400 for an unknown type and for switching off a mandatory channel, kept rows for an unregistered type
- [x] 1.5 Implement the e-mail channel on `MailSender` (long form, usable only with a configured server and an address, link appended when a public address is set); verify with a test against GreenMail that subject, text and link arrive and that no connection is tried without a server

## 2. Recipients

- [x] 2.1 Add `SharingService.usersWithRole(type, objectId, minimum)` including roles inherited from the parent type, and expose it for units (D8); verify with additions to `SharingChildTypeTest` for a property owner, a unit-only editor and a viewer

## 3. Sign-in warning on the layer

- [ ] 3.1 Register the type `core.signIn` with `mail` mandatory and send the warning through `Notifications`, keeping subject and mail text (D5); verify that `SignInAlertsTest` and `sign-in-alerts.spec.ts` pass unchanged
- [x] 3.2 Add the short form (browser, operating system, location, no client address); verify with a test that a user without an address gets the Pushover notification through a fake channel and that the text contains no part of the address

## 4. Pushover channel

- [x] 4.1 Confirm the request and response shapes of `messages.json` and `users/validate.json` against the Pushover API documentation, in particular how an invalid token and an invalid user key are reported; record the result in design D7 and, if a token cannot be checked alone, stop and adjust the spec requirement „Check the token on save“ with the user
- [x] 4.2 Implement `PushoverClient` with the HTTP implementation, timeouts, shortening with „…“ and `logis.pushover.base-url` (D7); verify with a test against a local stub server covering success, invalid token, invalid user, 429 and 5xx
- [x] 4.3 Implement the admin settings service and `GET|PUT|DELETE /api/admin/pushover` with the encrypted token and the check on save; verify with `PushoverSettingsTest` covering HTTP 403 for ordinary users, token never returned, both error messages, previous token kept after a rejected save
- [x] 4.4 Implement the user key service and `GET|PUT|DELETE /api/account/pushover` plus `POST /api/account/pushover/test`; verify with `PushoverKeyTest` covering unknown key rejected, key never returned, own key only, test notification text and its failure message
- [x] 4.5 Implement the Pushover channel (short form, usable only with token and valid key, `invalid_since` set when Pushover rejects the key); verify with a test that exactly token, key, title, short text and link are posted and that a revoked key stops later sends

## 5. Mail inbox events

- [x] 5.1 Publish `MailImported` only for mails of a regular poll received within two days, and move the attention results to the backend (`MailHandler.attentionResults()`, exposed by the inbox API, used by the quick filter „Offen“) (D6); verify with `MailInboxTest` that older fetches and reprocessing publish nothing, and that `Mail.test.tsx` still passes for „Offen“
- [x] 5.2 Register `core.mailAttention` and send one notification per poll with the count, senders and subjects only in the long form; verify with a test for one and for three mails and for a poll with only handled mails
- [x] 5.3 Register `core.mailFetchFailed` and send it on the change from a successful to a failed regular poll; verify with a test for repeated failures, recovery and a manual fetch, and that the text holds no server error text

## 6. Vacation rental events

- [x] 6.1 Register `vacationrental.bookingReview` and `vacationrental.changeReview`, listen to `MailImported` and send to users with at least `EDITOR` on the unit (D6); verify with `BookingNotificationsTest` covering new booking, proposed cancellation, viewer excluded, admin without a role excluded
- [x] 6.2 Build both forms for the review notifications with invented booking values; verify with a test that guest name, amount and booking number appear in the long text and in neither title nor short text
- [x] 6.3 Add the migration `V<yyyyMMdd>.<HHmm>__vacationrental_notification_digests.sql` and the digest job with the 18:00 schedule and the start-up catch-up (D9); verify with the test clock: sent once, not twice after a restart, sent after a start at 19:10, not sent the next morning
- [x] 6.4 Register `vacationrental.arrivals` and `vacationrental.departures` and build one digest per user; verify with a test covering two units, a user with one unit, cancelled bookings, bookings „zu prüfen“ and owner uses left out, unit names only in the short text
- [x] 6.5 Verify with additions to `ModulesDisabledTest` that with `vacationrental` disabled its types are not offered, nothing is sent and stored switches survive

## 7. Frontend

- [x] 7.1 Add the API client and the card „Benachrichtigungen“ on `/konto` with grouped rows, one switch per channel, disabled switches with hints and mandatory switches; verify with `NotificationsCard.test.tsx` against a mocked API
- [x] 7.2 Add the card for the own Pushover key with save, remove, „Testnachricht senden“, the invalid-key notice and „Pushover ist noch nicht eingerichtet“; verify with `PushoverCard.test.tsx`
- [x] 7.3 Add the admin page `/einstellungen/pushover` with the sidebar entry „Pushover“ and the note about what is sent to Pushover; verify with `PushoverPage.test.tsx` and the sidebar assertion in `AppShell.test.tsx`
- [ ] 7.4 Check both pages at phone and tablet widths by adding them to `layout.spec.ts`; verify the layout check passes at 390, 820 and 1024 px

## 8. End to end and documentation

- [ ] 8.1 Add a stand-in for the Pushover API to `e2e/compose.e2e.yml` that accepts both endpoints and lets tests read the received messages, and point the backend at it; verify the stack starts and the stand-in answers
- [ ] 8.2 Add `e2e/tests/notifications.spec.ts`: admin stores the token, user stores a key and sends a test, switches on both channels for „Neue Buchung zu prüfen“, a booking mail with invented values arrives, the stand-in holds a message without the guest name and the mail holds it; verify the test passes on desktop and phone
- [ ] 8.3 Add rows for `notifications` and `pushover` to `e2e/COVERAGE.md` and extend the rows of `sign-in-alerts`, `bookings` and `mail-inbox`; verify every new capability has a row
- [ ] 8.4 Describe the Pushover set-up, the outbound connection to `api.pushover.net` and the role of `LOGIS_PUBLIC_URL` for links in the README; verify the described steps against the running app
- [ ] 8.5 Run `cd backend && ./mvnw test`, `cd frontend && npm test && npm run lint && npm run build` and the e2e suite; verify all pass, and check that no test file, spec or commit holds values from real mails or bookings

## Workflow follow-up

- Archive the change with `/opsx:archive` once it is reviewed and merged.
