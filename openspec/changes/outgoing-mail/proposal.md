# Proposal

## Why

Logis can only receive mail, not send it. The planned warning about sign-ins from a new device or location needs to reach users by e-mail, and so will later notices. That needs two things:
- an outgoing mail server that the admin configures;
- an e-mail address per user that the user controls. Whoever takes over an account must not be able to silently redirect these warnings.

## What Changes

- **Outgoing mail server (SMTP), configured by the admin.** A new admin page „E-Mail-Versand“ under *Einstellungen* with these fields:
  - server, port, encryption (none, STARTTLS, SSL/TLS);
  - username and password;
  - sender address and sender name.
  
  Behaviour:
  - The password is stored encrypted and never returned.
  - Saving tests the connection and rejects unreachable servers or wrong credentials.
  - „Testmail senden“ sends a test mail to an address the admin enters.
  - Without a configuration, Logis sends nothing, and everything else works as before.
- **E-mail address per user.**
  - The admin enters it when creating a user. It is **required for new users**.
  - The admin can change it in the user management.
  - Users created before this change have no address. After signing in they see the notice „Bitte hinterlege eine E-Mail-Adresse unter Mein Konto.“ until they add one.
- **Own address under „Mein Konto“, with confirmation.**
  - The user enters a new address. Logis sends a 6-digit code to it, and the address only changes once the code is entered.
  - The code is valid for 30 minutes and allows 5 attempts.
  - After the change, the old address receives the notice „Deine E-Mail-Adresse in Logis wurde geändert.“
  - Without an outgoing mail server, users cannot change their address themselves. They see „Der E-Mail-Versand ist nicht eingerichtet. Bitte wende dich an einen Admin.“
- **Sending service in the core.** A small public API for later senders (warning mails, notices): plain-text mails in German. Failed sends are logged without mail content and reported to the caller.
- **Out of scope:**
  - the warning about new devices or locations (next change);
  - HTML mails;
  - a queue with retries.

## Capabilities

### New Capabilities
- `outgoing-mail`: the admin's SMTP configuration with connection check and test mail, and sending plain-text mails from the system.

### Modified Capabilities
- `user-management`: „Create users“ requires an e-mail address, and „Edit users“ lets the admin change it.
- `authentication`: new requirements for the own e-mail address under „Mein Konto“ (change with code, notice to the old address) and for the notice to users without an address.

## Impact

- **Backend:**
  - `core.mail`: SMTP settings (entity, encrypted password through `SecretCipher`), connection check, `MailSender` as public API (`JavaMailSenderImpl` built from the stored settings), admin endpoints under `/api/admin/smtp`.
  - `core.users`: e-mail address on `User`, admin create and edit with the address.
  - Own address change: `/api/account/email` (request code, confirm code), the pending change with a hashed code.
  - `/api/auth/me` reports whether the user has an address.
- **Database:** one new migration in `core`:
  - nullable `core.users.email`; existing users keep no address;
  - a new table `core.smtp_settings` with a single row;
  - a new table `core.email_changes` for pending changes.
  - Existing data stays unchanged.
- **Frontend:**
  - admin page „E-Mail-Versand“;
  - e-mail field in the user dialogs;
  - card „E-Mail-Adresse“ under „Mein Konto“;
  - notice in the app shell for users without an address.
- **E2E:** the test stack already runs GreenMail with SMTP. The e2e test configures it as the outgoing server and reads the code from the test mailbox via IMAP.
- **Dependencies:** none new; `spring-boot-starter-mail` is already present.
