# Tasks

## 1. Backend: single, streamed fetch run

- [x] 1.1 Change `ImapClient.fetchSince` to a listener (`onTotal`, `onMail`, `onSkipped`) and adapt `JakartaImapClient` to download and hand over mails one by one; verify with the GreenMail-based tests in `MailInboxTest`
- [x] 1.2 Add the in-memory fetch run per owner in `MailService` (kind, from, total, checked, created, state, error, times) and update it while processing; verify a unit/integration test sees counters after a fetch
- [x] 1.3 Refuse a second running fetch: `poll`/`fetch-older` endpoints return `409` „Es läuft bereits ein Abruf.“, `MailPoller` skips a running mailbox; verify with a test that blocks a fetch and starts a second one
- [x] 1.4 Treat a unique-constraint violation when storing a mail as already known; verify with a test that stores the same Message-ID concurrently or twice
- [x] 1.5 Reject a future date in `fetch-older` with `400` „Das Datum darf nicht in der Zukunft liegen.“; verify with a `MailInboxTest` case
- [x] 1.6 Add `GET /api/mail/mailbox/fetch-status` returning the current or last run of the user only; verify another user gets `null` and the owner sees progress and the final result

## 2. Frontend: date choice and progress

- [x] 2.1 Add the shadcn `calendar` component (`react-day-picker`) and verify `npm run build` passes
- [x] 2.2 Build a date field with `TT.MM.JJJJ` input, calendar popover with month/year dropdowns and the messages „Bitte ein gültiges Datum eingeben.“ / „Das Datum darf nicht in der Zukunft liegen.“; verify with tests in `Mail.test.tsx`
- [x] 2.3 Add quick ranges „1 Jahr“, „2 Jahre“, „5 Jahre“ and the hint that already fetched mails are skipped to `MailboxCard`; verify the button is enabled after a quick range or a typed valid date
- [x] 2.4 Show the fetch progress (from-date, „n von N geprüft, m neu“, progress bar), disable fetch buttons while running, show „Abruf abgeschlossen: m neue Mails“ or the error, and refresh the inbox during and after the run; verify with tests mocking the status endpoint
- [x] 2.5 Show the `409` message when a fetch is already running; verify in `Mail.test.tsx`

## 3. Frontend: sort and filter the inbox

- [x] 3.1 Sort the inbox in the page and reuse the existing `DataTable` sort header (no `DataTable` change needed); verify `DataTable.test.tsx` still passes
- [x] 3.2 Make all inbox columns sortable, newest first by default, with sort kept in the URL; verify in `Mail.test.tsx`
- [x] 3.3 Add filters for result (with quick filter „Offen“), kind, sender, period and subject search, combined and kept in the URL, with „Filter zurücksetzen“; verify in `Mail.test.tsx` including reload from URL
- [x] 3.4 Limit bulk actions to selected mails that are visible under the current filter; verify in `Mail.test.tsx`

## 4. End-to-end and docs

- [ ] 4.1 Extend `e2e/tests/mail-import.spec.ts` (or a new spec) with fetching older mails via a quick range, the progress/result display and the quick filter „Offen“, using invented test data only; verify the e2e suite passes
- [ ] 4.2 Update `e2e/COVERAGE.md` for `mail-inbox` and run `./mvnw test`, `npm test`, `npm run lint`, `npm run build`
