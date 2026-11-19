# Design

## Context

- **Mail in the core.** `core.mail` only receives mail today:
  - IMAP through the `ImapClient` interface and its Jakarta Mail implementation;
  - per-user mailboxes with the password encrypted by `shared.SecretCipher`;
  - a connection test on save, failing with a fixed German message.
  - `spring-boot-starter-mail` is already a dependency, but `spring.mail.*` is not configured, so no `JavaMailSender` bean exists.
- **Users and admin area.**
  - `core.users.User` has no e-mail address.
  - `AdminUserController` and `UserAdminService` create and edit users. `UsersPage.tsx` holds the create and edit dialogs.
  - `/api/admin/**` needs the role `ADMIN` and, since remember-me, a fresh sign-in.
- **Account.**
  - „Mein Konto“ (`AccountPage.tsx`) has cards for password, recovery codes, passkeys and devices. `MyAccountController` serves `/api/account`.
  - `/api/auth/me` returns the session user.
- **Network.** The backend container already reaches external mail servers (IMAP). The e2e stack has GreenMail with SMTP on port 3025 and authentication disabled.
- **Core module.** `core` is one Spring Modulith module, and its packages may use each other. `core.mail` exposes a named interface `mail` for modules.

## Goals / Non-Goals

**Goals:**
- One system-wide outgoing server, configured in the UI. No restart and no deployment variables.
- A tiny sending API that the next change (sign-in warnings) can call.
- A user address that cannot be changed without access to the new mailbox, and whose change is reported to the old one.

**Non-Goals:**
- A queue, retries or a delivery log. Callers decide what a failure means.
- HTML mails, templates per language, attachments.
- Unique e-mail addresses across users. Two accounts of one person may share an address.
- Confirming addresses entered by an admin.

## Decisions

### D1. Settings in a single-row table
- `core.smtp_settings` has `id SMALLINT PRIMARY KEY CHECK (id = 1)` and these columns:
  - `host`, `port`, `security` (`NONE`, `STARTTLS`, `SSL_TLS`);
  - `username` (nullable, for servers without authentication) and `password_enc`;
  - `from_address`, `from_name`, `updated_at`, `version`.
- No row means sending is not set up.
- Alternative considered: deployment variables (`spring.mail.*`). The user asked for configuration in the UI, and variables would need a restart.

### D2. `SmtpClient` like `ImapClient`
- Package-private interface `SmtpClient` with:
  - `test(Config)`: connect and authenticate, then close;
  - `send(Config, Message)`.
- The implementation builds a `JavaMailSenderImpl` per call from the stored settings, with timeouts of 10 s for connect and read and 20 s for write.
- Security maps to these properties:
  - `STARTTLS`: `mail.smtp.starttls.enable` and `mail.smtp.starttls.required`;
  - `SSL_TLS`: `mail.smtp.ssl.enable`;
  - `NONE`: neither.
- Server certificates are checked through the JVM trust store, with no option to skip.
- Tests use GreenMail with authentication enabled, so wrong credentials can be tested.

### D3. Admin endpoints `/api/admin/smtp`
- `GET`: settings without password, plus `hasPassword` and `configured`.
- `PUT`: save. An empty password keeps the stored one if host and username are unchanged, as for the IMAP mailbox. The save first tests the connection, with the 400 message from the spec.
- `POST /test` with `{ to }`: sends „Testmail von Logis“ through `MailSender`. On failure it answers 400 with „Die Testmail konnte nicht gesendet werden“.
- `DELETE`: removes the configuration, so sending is off.

### D4. Public sending API
- In `core.mail`, part of the named interface:
  ```java
  public interface MailSender {
      boolean configured();
      /** Throws MailSendException (unchecked) if not configured or sending failed. */
      void send(String to, String subject, String text);
  }
  ```
- The implementation:
  - reads the settings per call, so a new configuration applies without restart;
  - decrypts the password;
  - sends `text/plain; charset=UTF-8` with From = `from_name <from_address>`.
- Failures are logged at WARN with the recipient's domain and the exception class only. Mail text, subject and full address never appear in the log.

### D5. User e-mail address
- `core.users.email VARCHAR(254)`, nullable. Existing rows stay `NULL`.
- Validation: a trimmed `jakarta.mail.internet.InternetAddress` with `validate()`, one `@`, no display name, at most 254 characters. The message is „Bitte eine gültige E-Mail-Adresse angeben.“
- `UserAdminService.create` requires an address. `update` accepts an optional new address.
- `UserView` and the admin list include `email`.
- `/api/auth/me` adds `emailMissing: boolean` to the session user.
- The initial admin from the deployment configuration has no address, so it gets the notice like any existing user. No new deployment variable is added.

### D6. Own address change with a code
- Table `core.email_changes`:
  - `user_id` (PK, references users, `ON DELETE CASCADE`);
  - `new_email`, `code_hash`, `expires_at`, `attempts`.
  - At most one pending change per user; a new request replaces the row.
- `POST /api/account/email` with `{ email }`:
  - 409 with the message from the spec if `MailSender.configured()` is false;
  - otherwise a random 6-digit code (`SecureRandom`), stored as SHA-256 of user id plus code, expiring after 30 min;
  - mail to the new address with subject „Bestätigungscode für Logis“ and the code;
  - if the mail fails: no row is kept, and the answer is 400 „Die Bestätigungsmail konnte nicht gesendet werden“.
- `POST /api/account/email/confirm` with `{ code }`:
  - rejects with „Der Code ist ungültig oder abgelaufen.“ if no row exists, it is expired, or `attempts >= 5`;
  - a wrong code increments `attempts`;
  - the right code sets `users.email`, deletes the row, and sends the notice to the old address if there was one. That notice is best effort, so a failure is only logged.
- The partial address in the notice keeps the first character of the local part and the domain: „a…@example.org“.
- Confirming does not change `sessionVersion`. The address is not a credential.
- `GET /api/account/email`: current address, pending address (if any) and `canChange` (= mail is configured).

### D7. Interface
- **„E-Mail-Versand“** (`/einstellungen/e-mail-versand`, admin navigation):
  - a form with the fields from the spec; the encryption as a select;
  - „Speichern“, „Testmail senden“ with an address field (prefilled with the admin's own address), and „Konfiguration entfernen“.
- **User dialogs:** a required field „E-Mail-Adresse“ in „Benutzer anlegen“, and an optional one in „Bearbeiten“. The list shows the address under the username.
- **„Mein Konto“, card „E-Mail-Adresse“:**
  - the current address, or „Keine Adresse hinterlegt“;
  - a form for the new address, then the code input (6 digits), and „Code erneut senden“;
  - the notice from the spec when `canChange` is false.
- **App shell:** a dismissible-per-page `Alert` above the content when `emailMissing`, with the link „Mein Konto“. It reappears on the next page until an address exists.

## Risks / Trade-offs

- [Users cannot add an address themselves without a configured server] → The notice tells them to ask an admin, who can enter it in the user management.
- [Code brute force] → 5 attempts per code, 30 minutes, a new code only by request. 6 digits with 5 attempts gives a guess probability of 0.0005 % per code. Requests are limited by the normal per-IP rate limit of nginx for `/api/`.
- [A taken-over session changes the address] → It needs access to the new mailbox's code, and the old address is told. A stronger check (current password) is possible later.
- [SMTP blocking the request thread] → The timeouts in D2 bound it. Sends happen only on explicit actions in this change.
- [Settings changed while a mail is sent] → Settings are read per send, and `version` protects concurrent saves.

## Migration Plan

1. Migration `V20261119.1400__core_outgoing_mail.sql`:
   - adds `core.users.email` (nullable);
   - creates `core.smtp_settings` and `core.email_changes`.
   - No data changes. Sending stays off until an admin configures a server.
2. Rollback: the previous release ignores the column and tables.
