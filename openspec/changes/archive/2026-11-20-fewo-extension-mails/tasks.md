# Tasks

## 1. Recognition

- [x] 1.1 Add `MailType.EXTENSION` and the nullable `original` period to `ParsedMail`, with `usable()` accepting an extension without a booking number (design D2). Verify: existing `MailParsersTest` and `MailRecognizerTest` pass unchanged.
- [x] 1.2 Add the extension rule to `FewoUndMeerParser` (design D1). Verify with `MailParsersTest` cases on invented mails:
  - extension before the stay, with an em dash;
  - extension after the stay with „( 3 ) Erwachsene/ ( 0 ) Kinder“;
  - a forwarded notice;
  - a notice from a sender that is not Fewo & Meer;
  - a mail missing the original period;
  - the locations of the read values.
- [x] 1.3 Refuse `EXTENSION` from the AI in `MailRecognizer.toParsed` (design D4). Verify with a `MailRecognizerTest` case.

## 2. Matching and labels

- [x] 2.1 Handle `EXTENSION` in `ImportEngine.apply` (design D3) and add the label „Verlängerung“ to `ImportEngine.label`. Verify with `MailImportTest` cases using an invented sample mail in `src/test/resources/mails`:
  - proposal of the merged period with the kind `EXTENSION`;
  - more adults in the proposal;
  - direct update of a booking that is not reviewed yet;
  - already extended → attached;
  - no booking with the original period → `NOT_MATCHED`;
  - unmapped property → `UNASSIGNED_UNIT`;
  - a stored „Nicht erkannt“ notice becomes `CHANGE_PROPOSED` after „Erneut verarbeiten“.
- [x] 2.2 Add the kind label „Verlängerung“ to `BookingMailHandler.KIND_LABELS` and the frontend's `mailKindLabel`, with a frontend inbox test showing „Änderung vorgeschlagen“ and „Verlängerung“. Verify: `MailLabelsTest` and the frontend test pass.

## 3. Verification

- [x] 3.1 Run `cd backend && ./mvnw test` and `cd frontend && npm test && npm run lint && npm run build`. All must be green.
- [x] 3.2 Run the booking-mail e2e tests (`mail-import`, `booking-proposal`, `inbox-bulk`, `inbox-fetch`) against a stack with the new images. They must be green. The review of a proposed change is an existing journey, so `e2e/COVERAGE.md` gets no new row.
- [x] 3.3 Check that the sample mail and tests contain no values from the real export: names, property, phone numbers, staff names, dates.

## Workflow follow-up

- Archive with `/opsx:archive` and sync the `booking-mails` spec.
