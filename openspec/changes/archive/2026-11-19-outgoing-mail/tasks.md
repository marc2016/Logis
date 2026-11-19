# Tasks

## 1. Data

- [x] 1.1 Add migration `V20261119.1400__core_outgoing_mail.sql` with `core.users.email`, `core.smtp_settings` and `core.email_changes` (design D1, D5, D6). Verify with a migration test that existing users keep all values and get `email = NULL`.

## 2. Outgoing mail server

- [x] 2.1 Add the SMTP settings entity and repository, `SmtpClient` with its Jakarta Mail implementation, and `MailSender` with its implementation (design D1, D2, D4). Verify in a backend test with GreenMail SMTP (authentication enabled):
  - a mail arrives with sender name and address, UTF-8 text and subject;
  - without settings `configured()` is false and `send` fails without connecting;
  - an unreachable server fails;
  - the log contains neither text nor full recipient.
- [x] 2.2 Add the admin endpoints `GET`/`PUT`/`DELETE /api/admin/smtp` and `POST /api/admin/smtp/test` (design D3). Verify in `SmtpSettingsTest`:
  - save with connection check;
  - wrong password rejected with the message, and the previous settings stay;
  - the password is never returned;
  - an empty password keeps the stored one;
  - the test mail arrives with „Testmail von Logis“;
  - a user gets 403;
  - delete turns sending off.
- [x] 2.3 Add the admin page „E-Mail-Versand“ and its navigation entry (design D7). Verify in a frontend test with a mocked API:
  - the form loads without a password;
  - saving sends the fields;
  - the server message is shown;
  - the test mail sends the entered address;
  - removing asks for confirmation.

## 3. User e-mail address

- [x] 3.1 Add `email` to `User`, the validation, `create` with a required address, `update` with an optional one, `UserView.email` and `emailMissing` in `/api/auth/me` (design D5). Verify in `AdminUsersTest`:
  - create without or with an invalid address gives the message;
  - create and edit store the address;
  - the list shows it;
  - `/me` reports `emailMissing` for a user without an address.

  Update the existing tests that create users through the API.
- [x] 3.2 Add the e-mail field to „Benutzer anlegen“ and „Bearbeiten“, and the address in the list (design D7). Verify in `UsersPage.test.tsx`.

## 4. Own address

- [x] 4.1 Add `GET /api/account/email`, `POST /api/account/email` and `POST /api/account/email/confirm` with the pending change, code, attempts, expiry and the notice to the old address (design D6). Verify in `EmailChangeTest` with GreenMail:
  - the code arrives at the new address and confirms the change;
  - a wrong code, the sixth attempt and an expired code (test clock) are rejected with the message;
  - a new request replaces the old code;
  - the old address gets the notice with the partial new address;
  - without settings the request gives 409 with the message;
  - a failing send keeps no pending change.
- [x] 4.2 Add the card „E-Mail-Adresse“ to „Mein Konto“ and the missing-address notice to the app shell (design D7). Verify:
  - in `AccountPage.test.tsx`: request, code entry, error message, the notice when sending is not set up;
  - in `AppShell.test.tsx`: the notice appears with `emailMissing` and links to „Mein Konto“.
- [x] 4.3 Run `npm run lint`, `npm test` and `npm run build` in `frontend` and verify all pass.

## 5. End to end and documentation

- [x] 5.1 Publish GreenMail's IMAP port in `e2e/compose.e2e.yml` and add a small IMAP reader to `e2e/support/mail.ts`. Then add `e2e/tests/outgoing-mail.spec.ts`:
  - the admin configures GreenMail as the outgoing server and sends a test mail, which arrives;
  - a user changes their address with the code read from the test mailbox, and the old address receives the notice.

  Add the journeys to `e2e/COVERAGE.md` with a new `outgoing-mail` row and the `authentication` and `user-management` rows. Verify the spec passes against the compose stack.
- [x] 5.2 Document the outgoing mail server, the required address for new users and the address change in the README. Verify the text matches the spec wording.
- [x] 5.3 Run `cd backend && ./mvnw test` and verify the whole suite passes.

## Workflow follow-up

- Archive the change after review (`/opsx:archive`).
- Next change: warning mails for sign-ins from a new device or location, using `MailSender`.
