# Design

## Context

- **Stored mails.** `core.mails` (`StoredMail`) keeps each fetched mail per owner:
  - `raw`: the complete original message;
  - `received_at`, `sender`, `subject`;
  - the handling result: `handler`, `result`, `kind`, `source` (agency), link.
  - A deleted mail keeps its row with `result = DELETED` and no content.
- **Mailboxes.** `core.mailboxes` (`Mailbox`) holds one row per user, keyed by `owner_user_id`, and is edited only by its owner (`/api/mail/mailbox`).
- **Parsing.** `MailContent.parse(raw)` reads subject and text. It handles forwarded mails attached as `message/rfc822` and turns HTML into text.
- **Handlers.** `MailHandler` is the public extension point of `core.mail`. `BookingMailHandler` in `modules.vacationrental` handles booking mails with the owner's rights. `MailRecognizer.recognizeByRules` reads a mail into `ParsedMail` (guest, booking number, locations) without AI.
- **Bookings.** Bookings carry `Guest` (name, street, postal code, city, country, phone, e-mail) and `externalRef`. The units a user can see come from `access.visibleUnits(userId)`, the per-object sharing model.
- **Admin area.** `/api/admin/**` already requires `ROLE_ADMIN`, and since remember-me also a fresh sign-in. Admin pages are wrapped in `RequireAdmin`.

## Goals / Non-Goals

**Goals:**
- An exception to "admins see no domain data" that is narrow, consented, logged and only yields pseudonymized copies.
- Exported mails that stay useful as parser test input: same MIME structure, encodings, forwarding and HTML.
- No new dependency on the vacation rental module from `core.mail`.

**Non-Goals:**
- Guaranteed anonymization. Pseudonymization is best effort, and the warning says so.
- Pseudonymizing attachments. They are removed instead.
- Export by users of their own mails (not requested; can follow).
- Background jobs or stored export files. The ZIP is streamed and never stored.

## Decisions

### D1. Consent on the mailbox
- Columns `admin_export_allowed BOOLEAN NOT NULL DEFAULT false` and `admin_export_allowed_at TIMESTAMPTZ` on `core.mailboxes`. All existing rows get `false`.
- `MailboxService.MailboxInput` gets the flag. Only the owner's endpoint `/api/mail/mailbox` can set it, and no admin endpoint writes it.
- Consent is checked when an export starts. A mailbox without consent is treated as unknown (404), so its existence is not revealed.

### D2. Export endpoints under `/api/admin/mail-export`
- `GET /mailboxes`: consenting mailboxes with owner id, username and display name.
- `GET /agencies?owners=…`: distinct `source` values of their non-deleted mails, for the agency filter.
- `POST /` with `{ owners[], from, to, results[], sources[] }`:
  - counts first and rejects more than 2000 mails with 400 and the message from the spec;
  - otherwise streams `application/zip` with `Content-Disposition: attachment; filename="logis-mails-<date>.zip"`.
- `GET /log`: the export log, newest first.
- The frontend posts with `fetch`, reads the blob and saves it via a temporary object URL. A plain link cannot send a CSRF header.
- Alternative considered: an export job with a stored file and a download link. That means stored copies of personal data and clean-up. Rejected for one-off exports of a few hundred mails.

### D3. Sensitive values through `MailHandler`
- New default method on the public interface:
  ```java
  default Set<String> sensitiveValues(Long ownerUserId, MailContent mail, byte[] raw) { return Set.of(); }
  ```
- `BookingMailHandler` implements it with:
  - the guest fields and booking number from `recognizeByRules(mail)`. Rules only: the AI is never called for an export.
  - plus a per-owner cache of all guest values (name, street, postal code, city, phone, e-mail) and `externalRef` of the bookings on units the owner can see (`access.visibleUnits`). It is collected once per export.
- `core.mail` asks every registered handler, so later modules can add their own values. The dependency direction stays module → core.
- Values are typed as name, street, postal code, city, phone, e-mail or reference. A small record `SensitiveValue(kind, value)` replaces the plain string, so that the replacement keeps the right format (D5).

### D4. MIME rewriting with Jakarta Mail
- Parse `raw` into a `MimeMessage` and walk it recursively:
  - `text/plain` and `text/html`: read as string, replace (D5), set the content again with the original charset and keep the original `Content-Transfer-Encoding`.
  - `multipart/*`: recurse.
  - `message/rfc822`: parse the nested message, rewrite it recursively, and put it back.
  - Any other leaf part: removed. If a multipart would end up empty, the multipart is removed as well.
- Headers:
  - Subject: decoded, replaced and encoded again.
  - From, To, Cc, Reply-To: display names and addresses are replaced, keeping the address domain of From (D5).
  - `Received`, `Return-Path`, `Delivered-To`, `X-Original-To`, `DKIM-Signature` and `ARC-*` are dropped. They carry owner and server addresses and have no value for parsing.
  - `Message-ID` is replaced by a random id.
- `saveChanges()` and `writeTo` produce the new `.eml`. If a mail cannot be parsed or rewritten, its `.eml` is left out and its `index.csv` row says „nicht exportierbar“. A partial original is never exported.

### D5. Replacement rules
A `Pseudonymizer` lives per export and keeps the mappings, so the same value gets the same replacement everywhere.

- **Known values** (D3), longest first, case-sensitive, at word boundaries:
  - name → „Gast N“. The single name parts of three or more letters are also mapped, to „GastN“, so that „Herr Mustermann“ is covered.
  - street → „Musterstraße N“; city → „Musterstadt N“.
  - postal code, phone and reference → same format: each digit is replaced by a random digit and each letter by a random letter, separators are kept.
  - e-mail → `gastN@example.org`.
  - In HTML parts, the HTML-escaped form of each value (`&uuml;`, `&amp;`) is replaced too.
- **Patterns**, applied after the known values:
  - e-mail addresses → `personN@example.org`, except addresses on the domain of the mail's From header. Agency addresses such as `noreply@<agency>` stay, because parsers and agency mapping depend on them.
  - phone numbers (`+` or `0` followed by at least 7 digits with spaces, `/`, `-` or parentheses) → same format.
  - IBANs → same format with a valid-looking country prefix kept.
- Dates and amounts are never replaced. The phone pattern requires a leading `+` or `0` and at least 7 digits, so dates (`23.12.2026`) and amounts (`1.234,56`) do not match.

### D6. ZIP and index
- `java.util.zip.ZipOutputStream` writes directly to the response.
- File names: `NNNN-<result>-<source>.eml`, with result and source in kebab-case ASCII.
- `index.csv`:
  - UTF-8 with BOM, `;`-separated, so that German Excel opens it;
  - columns: Datei, Eingang, Absender, Betreff, Ergebnis, Art, Vermittler, Postfach;
  - result and kind as their codes, plus the German result label from the core where known.

### D7. Export log
- New table `core.mail_exports`: `id`, `admin_user_id`, `owner_user_id`, `created_at`, `mail_count`, `filters JSONB`. One row per exported mailbox, written once the ZIP has been written completely.
- The owner's mailbox view gets `lastExport { at, mailCount, adminDisplayName }`.
- The admin log returns rows with admin and owner display names.
- No mail content and no pseudonym mappings are stored.

### D8. Interface
- **Mailbox card:**
  - a switch „Admins dürfen meine Mails pseudonymisiert exportieren“ with a short explanation;
  - below it „Zuletzt exportiert am … · N Mails · von …“ when there is an export.
- **Admin page „Mail-Export“** (`/einstellungen/mail-export`, in the admin navigation under *Einstellungen*):
  - mailbox checkboxes (all consenting ones checked);
  - Von and Bis date fields;
  - result toggles using the inbox labels;
  - agency selection from `GET /agencies`;
  - „Exportieren“ opens the warning dialog, and „Herunterladen“ starts the download;
  - below: the log table.
- If no mailbox consents, the page says „Kein Postfach ist für den Export freigegeben.“

## Risks / Trade-offs

- [Names and addresses of guests without a booking, in unrecognised mails, stay in clear text] → This is inherent to best effort. The warning before the download says so, and the e2e test checks the cases that are covered.
- [A guest's city or name equals a word of the mail, such as a property name, and is replaced there too] → The exported mail is less faithful in that spot. Parsing tests built from it may need a manual fix. Accepted.
- [Rewriting changes the MIME bytes, such as line lengths and boundaries] → Parsers work on the decoded content, so structure and encodings are what matters, and both are kept.
- [Admin account compromise exposes consenting mailboxes] → Only pseudonymized data leaves. The fresh sign-in and the log apply, and owners see the last export.
- [Large exports load mails into memory] → Mails are streamed one by one, and the 2000-mail limit caps the run time.

## Migration Plan

1. Migration `V20261119.1300__core_mail_export.sql`: adds the consent columns with default `false` and creates `core.mail_exports`. No existing data changes, and nobody consents until they switch it on.
2. Rollback: the previous release ignores the new columns and table.

## Open Questions

- Whether owners should also get the export for their own mails. This is not needed for the parser work and can be added on top of the same service later.
