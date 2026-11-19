# Tasks

## 1. Recognition

- [x] 1.1 Add `ticketReceipt(MailContent)` to `FewoUndMeerParser` and expose it through `MailRecognizer` (design D1), leaving out lines quoted with „>“ (see design, Risks). Verify with parser tests on invented mails:
  - a receipt;
  - a receipt forwarded by the owner;
  - a staff reply in the same ticket without the receipt text;
  - a staff reply that quotes a receipt;
  - a receipt from a sender that is not Fewo & Meer.

## 2. Handling

- [x] 2.1 In `BookingMailHandler.handle`, check for a receipt before `recognize` and report `IGNORED`, kind `TICKET_RECEIPT`, agency „Fewo & Meer“ and the ticket ID (design D2). Verify with a backend test:
  - an incoming receipt shows as `IGNORED` with kind and reference, and no booking exists;
  - with a stub AI extractor, the AI is not called;
  - a stored „Nicht erkannt“ receipt becomes `IGNORED` after „Erneut verarbeiten“.
- [x] 2.2 Add the kind label „Eingangsbestätigung“ to `mailKindLabel`, and to the backend kind labels if they exist. Verify with a frontend test that the inbox shows „Ignoriert“ and „Eingangsbestätigung“ for such a mail.

## 3. Verification

- [x] 3.1 Run `cd backend && ./mvnw test`, and `cd frontend && npm test && npm run lint && npm run build`. All must be green.
- [x] 3.2 Run the existing booking-mail e2e tests against a stack with the new images. They must be green. No new user journey, so `e2e/COVERAGE.md` stays as it is.
- [x] 3.3 Check that no value from the real export appears in tests or specs.

## Workflow follow-up

- Archive with `/opsx:archive` and sync the `booking-mails` spec.
