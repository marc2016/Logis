# Tasks

## 1. Data

- [ ] 1.1 Add migration `V20261119.1300__core_mail_export.sql` with the consent columns on `core.mailboxes` (default `false`) and the table `core.mail_exports` (design D1, D7). Verify with a migration test that existing mailboxes keep their values and get `admin_export_allowed = false`.

## 2. Consent

- [ ] 2.1 Add the consent flag to `Mailbox`, `MailboxService.MailboxInput` and the owner's mailbox view, including `lastExport` (design D1, D7). Verify in a backend test that the owner can switch it on and off, that it is off for a new mailbox, and that no admin endpoint can change it.
- [ ] 2.2 Add the switch and the last export line to the mailbox card (design D8). Verify in `Mail.test.tsx` that the switch sends the flag and that the last export is shown.

## 3. Sensitive values

- [ ] 3.1 Add `SensitiveValue` and the default method `sensitiveValues` to `MailHandler` (design D3). Verify with `./mvnw test -Dtest=ModularityTest` that the module structure still holds.
- [ ] 3.2 Implement `sensitiveValues` in `BookingMailHandler`: rule recognition without AI, plus guest values and booking numbers of the owner's visible bookings, cached per export (design D3). Verify in a backend test with invented sample mails:
  - a recognised Fewo & Meer confirmation yields its guest values and booking number;
  - an unrecognised mail of a known guest yields that guest's values;
  - the AI extractor is never called.

## 4. Pseudonymization and export

- [ ] 4.1 Implement `Pseudonymizer` with the replacement rules (design D5). Verify with unit tests:
  - consistent mapping;
  - name parts;
  - HTML-escaped values;
  - format-keeping phone, postal code and reference;
  - e-mail addresses with and without the From domain;
  - IBAN;
  - dates and amounts left unchanged.
- [ ] 4.2 Implement the MIME rewriting (design D4) on top of it. Verify with unit tests on the sample mails in `src/test/resources/mails`:
  - the HTML forward, the plain forward and the quoted cancellation keep their parts, charsets and transfer encodings;
  - attached mails are rewritten;
  - attachments are removed;
  - dropped headers are gone;
  - `MailContent.parse` of the result still yields the rewritten subject and text;
  - the existing rule recognition still recognises a pseudonymized Fewo & Meer confirmation.
- [ ] 4.3 Add the export service and the endpoints `GET /api/admin/mail-export/mailboxes`, `GET /agencies`, `POST /` and `GET /log`, with the ZIP, `index.csv`, the 2000-mail limit and the log rows (design D2, D6, D7). Verify in `MailExportTest` (integration):
  - only consenting mailboxes are listed;
  - an export for a mailbox without consent gives 404;
  - a user gives 403;
  - a restored admin session needs a fresh sign-in;
  - the filters combine;
  - deleted mails are missing;
  - the ZIP has one `.eml` per mail and a matching `index.csv`;
  - known guest values occur in no file;
  - more than 2000 matches give 400 with the message;
  - one log row per mailbox is written and shows up in the owner's mailbox view.

## 5. Admin page

- [ ] 5.1 Add the page „Mail-Export“ with filters, warning dialog, download and log, and add it to the admin navigation under *Einstellungen* (design D8). Verify in a frontend test with a mocked API:
  - consenting mailboxes are listed, and the empty state is shown when none consents;
  - the request body matches the chosen filters;
  - „Abbrechen“ in the warning sends no request;
  - the download is triggered;
  - the 400 message is shown;
  - the log is listed.
- [ ] 5.2 Run `npm run lint`, `npm test` and `npm run build` in `frontend` and verify all pass.

## 6. End to end and documentation

- [ ] 6.1 Add `e2e/tests/mail-export.spec.ts`:
  - a user sets up a mailbox, a booking mail with invented guest data arrives, and the user switches the consent on;
  - the admin exports, and the downloaded ZIP contains the mail without the guest name, phone and booking number;
  - the owner sees the last export.

  Add the journey to `e2e/COVERAGE.md` with a new `mail-export` row. Verify the spec passes against the compose stack.
- [ ] 6.2 Document the consent, the export and its limits (best effort, attachments removed, log) in the README sections on users and security. Verify the text matches the spec wording.
- [ ] 6.3 Run `cd backend && ./mvnw test` and verify the whole suite passes.

## Workflow follow-up

- Archive the change after review (`/opsx:archive`).
