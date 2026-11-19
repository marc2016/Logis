# Proposal

## Why

New agency formats and variants show up as „nicht erkannt“ in the inbox. To improve the parsers, the mails have to be shared with whoever works on them, for example uploaded to an AI assistant. Today there is no way to get stored mails out of Logis. The mails contain guest data, so sharing them needs pseudonymization, the owner's consent and a record of every export.

## What Changes

- **BREAKING (access rule):** an admin can export mails from other users' mailboxes. This is a deliberate exception to two rules: the role `ADMIN` gives no access to domain data, and other users never see a user's inbox. The exception is limited:
  - The admin only sees mailboxes whose owners consented.
  - The admin only receives pseudonymized copies.
  - Every export is recorded.
  - It needs a fresh sign-in, like the rest of the admin area.
- **Consent in the mailbox settings.** A switch „Admins dürfen meine Mails pseudonymisiert exportieren“, off by default and revocable at any time. Revoking takes effect for the next export.
- **Export in the admin area.** A new page „Mail-Export“ under *Einstellungen*.
  - Filters: mailbox (only consenting ones), period, result and agency (source).
  - Deleted mails are never exported.
  - Before the download, a dialog warns: „Pseudonymisiert nach bestem Bemühen. Nicht erkannte Namen und Adressen können enthalten sein.“
  - The download is a ZIP with an `index.csv` and one `.eml` file per mail.
- **Pseudonymization, best effort, inside the original mail structure.**
  - Text and HTML parts, the subject and the address headers are rewritten. Forwarded and attached mails are handled the same way.
  - Attachments (PDF, images) are removed.
  - Replaced: the guest values the rules read from the mail, the guest data and booking numbers of the owner's bookings, and e-mail addresses, phone numbers and IBANs found by pattern.
  - The same original value gets the same replacement within one export.
  - Dates and amounts stay unchanged.
- **Export log.** Admins see all exports on the export page: who, when, which mailbox, how many mails, which filters. The mailbox owner sees the last export in their mailbox settings.

## Capabilities

### New Capabilities
- `mail-export`: consent, the pseudonymized admin export of stored mails with filters, its content and format, and the export log.

### Modified Capabilities
- `mail-inbox`: „Inbox“ names the consented, pseudonymized admin export as the only exception to „other users MUST NOT see a user's inbox“.

## Impact

- **Backend:**
  - `core.mail`:
    - consent on the mailbox;
    - export service: filtering, MIME rewriting, ZIP streaming;
    - admin endpoints under `/api/admin/mail-export`;
    - export log.
  - `MailHandler`: new optional method that returns the sensitive values of a mail for its owner.
  - `modules.vacationrental`: implements that method, reusing rule recognition without AI and the owner's bookings.
- **Database:** one new migration in `core`:
  - consent columns on `core.mailboxes`, false for existing rows;
  - a new table `core.mail_exports`.
  - Existing data stays unchanged.
- **Frontend:**
  - consent switch and last export in the mailbox card;
  - new admin page with filters, warning dialog, download and log.
- **Tests:**
  - backend tests for consent, filters, access, pseudonymization and the log;
  - frontend tests for both screens;
  - an e2e test: consent, export, download contains no known guest values.
