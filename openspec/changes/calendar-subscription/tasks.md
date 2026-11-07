# Tasks

## 1. Backend

- [x] 1.1 Add the migration for `vacationrental.calendar_links` and the entity with token generation and hashing (D1). Verify: a test creates a link, finds it by its token and not by a different one, and the stored hash differs from the token.
- [x] 1.2 Add the endpoints to list, create and revoke links with access checks (D2). Verify: integration tests cover create with URL returned once, list without token, revoke only by the creator, and refusal for a unit the caller cannot see.
- [x] 1.3 Add the public feed `/api/ical/{token}.ics` and permit it in the security configuration (D2, D3). Verify: integration tests without a session check the events with and without guest data, the absence of the guest name without guest data, cancelled bookings left out, the departure day included, `404` for unknown and revoked tokens, and `404` after the creator lost access.
- [ ] 1.4 Run `cd backend && ./mvnw test`. Verify: everything is green, including the modularity test.

## 2. Frontend

- [ ] 2.1 Add the button „Abonnieren“ and the dialog with list, creation, one-time URL with „Kopieren“ and „Widerrufen“ (D4). Verify: component tests cover creating a link without guest data, showing its URL, and revoking it.
- [ ] 2.2 Run `npm test && npm run lint && npm run build`. Verify: everything is green.

## 3. Acceptance

- [ ] 3.1 Fetch a feed from the running app and check it with an iCalendar parser (e.g. `ical.js`), and take a phone screenshot (390 px) of the dialog. Verify: the parser reads all events with the expected dates and titles, and the screenshot shows the link once.
