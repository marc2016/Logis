# Design

## Context

`vacation-rental` is in place: `Booking` with `external_ref` unique per `(unit_id, lower(external_ref))`, a derived status, guest data, documents via `core.documents` (owner type `vacationrental.booking`, upload only through `DocumentService` with signature check for PDF/images), and `RentalAccess` on top of `UnitAccess`. Scheduling is not enabled anywhere yet. `SecretCipher` (in `shared`) encrypts secrets with the configured key; TOTP secrets already use it. There is no mail dependency.

Motivation and decisions are in proposal.md; requirements in `specs/`.

## Goals / Non-Goals

**Goals:**
- Parsing is pure and testable: a mail (subject, sender, text, HTML) goes in, a `ParsedMail` comes out. No database access in parsers.
- One effect engine decides what happens with a `ParsedMail`, no matter whether a rule or the AI produced it.
- Imported data never bypasses the booking rules: the engine calls `BookingService` with the mailbox owner's identity.

**Non-Goals:**
- Receiving mail (SMTP), OAuth mailbox login (Gmail/Microsoft OAuth), sending mails.
- Statements, invoices, bank notifications.
- Learning new rules automatically from AI results.

## Decisions

### D1: Mail in the core, booking mails in the module

```
app.logis.core.mail                 (named interface "mail")
  Mailbox, MailboxService, MailboxController       /api/mail/mailbox
  ImapClient, JakartaImapClient, MailPoller        @Scheduled, per enabled mailbox
  StoredMail, MailService, MailController          /api/mail/inbox  (list, text, retry, ignore, delete)
  MailContent       public: subject, sender, text (HTML -> "Label\tValue" lines)
  MailHandler       public interface: key(), usesAi(), handle(ownerId, MailContent, raw) -> Optional<Handled>
  Handled           public record: result, detail, kind, source, reference, subject, linkType, linkId, data, parsedBy
  Mails             public API for handlers: reprocess, find waiting mails, mark results by link

app.logis.modules.vacationrental
  BookingMailHandler implements MailHandler ("vacationrental.booking")
  FewoUndMeerParser, HoliduDsParser, MailRecognizer, AiExtractor/ClaudeAiExtractor, ParsedMail
  ImportEngine, UnitMapping                        POST /api/vacationrental/mail/{id}/assign
```

- The core stores and lists mails and offers each mail to the handlers in `@Order` order; the first one that returns a result takes it. No handler: `NOT_RECOGNISED`. A disabled module has no handler bean, so its mails stay `NOT_RECOGNISED` and can be retried later.
- Results are strings: the core owns `NOT_RECOGNISED`, `IGNORED`, `DELETED` and `FAILED`; handlers add their own (`BOOKING_CREATED`, `UNASSIGNED_UNIT`, …). The frontend shows labels contributed by the module.
- Booking-specific actions (assigning a unit) are module endpoints; the module saves the mapping and asks `Mails.reprocess` for the waiting mails. The frontend module contributes these actions to the core inbox dialog through the module contract (`mailActions`, `mailResultLabels`).
- *Alternative:* everything in the module (first implementation). Rejected by the user: mail will be needed by other parts, and polling must not depend on vacation rental being enabled.
- *Alternative:* generic AI extraction in the core. Rejected for now: the schema is booking-specific; a later handler can bring its own.

### D2: Data model (schema `vacationrental`)

```
mailboxes
  owner_user_id PK, host, port, username, password_enc, folder, use_tls BOOLEAN,
  cutoff_date DATE, enabled BOOLEAN, last_poll_at, last_error VARCHAR(255), version

imported_mails
  id, owner_user_id, message_id VARCHAR(512), received_at, sender, subject,
  raw BYTEA (the .eml), text_preview TEXT,
  agency, mail_type, parsed JSONB, parsed_by (RULE|AI|NONE),
  result (BOOKING_CREATED|CHANGE_PROPOSED|ATTACHED|DUPLICATE|UNASSIGNED_UNIT|NOT_MATCHED|
          PERIOD_TAKEN|NOT_RECOGNISED|IGNORED|DISCARDED|FAILED|DELETED),
  booking_id NULL, created_at
  UNIQUE (owner_user_id, message_id)

unit_mappings
  owner_user_id, agency, property_name, unit_id   UNIQUE (owner_user_id, agency, lower(property_name))

bookings  + agency VARCHAR(64) NULL, + pets SMALLINT NOT NULL DEFAULT 0,
          + review_state (NONE|NEW|CHANGE) NOT NULL DEFAULT 'NONE', + proposal JSONB NULL
  unique index replaced: (unit_id, lower(coalesce(agency,'')), lower(external_ref))
```

- `raw` is kept in `imported_mails` for retries and the preview; the booking gets a copy as a document (D5). Deleting a mail in the inbox clears `raw`, the preview and the parsed fields and sets `DELETED`: the guest data is gone, but the Message-ID stays known, so the next poll does not fetch the mail again.
- `proposal` holds `{type: CHANGE|CANCELLATION, fields:{…}, mailId}`. A newer proposal overwrites it.

### D3: Effect engine

```
key = (agency, bookingRef) within units the owner can see
type CONFIRMATION | SHORT_NOTICE:
   no booking with key  -> unit via mapping? no -> UNASSIGNED_UNIT
                           create booking (review NEW, agency, mail attached) via BookingService as owner
                           overlap -> PERIOD_TAKEN ; no EDITOR -> UNASSIGNED_UNIT
   booking exists       -> differences in mapped fields? no -> ATTACHED ; yes -> CHANGE_PROPOSED
type CHANGE              -> booking exists ? CHANGE_PROPOSED : NOT_MATCHED
type CANCELLATION        -> booking exists ? (already cancelled ? ATTACHED : CHANGE_PROPOSED cancel) : NOT_MATCHED
same Message-ID again    -> skipped before parsing (unique constraint)
```

- "Differences" compare only fields the mail carries (a short-notice mail has no rent, so rent never differs). Guest fields compare trimmed and case-insensitive.
- Confirmations for a booking the user already entered by hand (same number, no agency) count as the same booking: lookup falls back to `agency IS NULL`, and the agency is set on confirm.
- Each mail is processed in its own transaction; a failure marks it `FAILED` with the message and does not stop the poll.

### D4: Polling

- `@EnableScheduling` in `VacationRentalConfiguration`; `MailPoller` runs every 5 minutes (`logis.mailimport.interval`, default `PT5M`) and handles mailboxes one after another.
- IMAP: `SEARCH SINCE <max(cutoff, last_poll - 1 day)>` on the folder, fetch headers first, skip known Message-IDs, then fetch bodies. Read-only folder (`Folder.READ_ONLY`), so flags are not changed.
- "Fetch older mails from date" runs the same search once with that date, asynchronously; the inbox shows progress by the growing list.
- Connection and auth errors are stored in `last_error` and shown on the settings page; three failures in a row disable nothing, but the page shows a warning.

### D5: Documents for mails

`core.documents.Documents` gets `store(ownerType, ownerId, fileName, contentType, bytes)` for server-created files. It skips the upload signature check but accepts only a whitelist including `message/rfc822`. File name: `<agency> <type> <bookingRef>.eml`. The booking dialog lists it like other documents; downloading gives the original mail. The inbox shows `text_preview` (jsoup text of the HTML part, or the plain part).

### D6: AI fallback

- Config: `logis.ai.enabled` (default false), `logis.ai.api-key`, `logis.ai.model` (default `claude-opus-5-5`). Only the administrator sets these, via environment.
- Input: subject, sender and text (max 20,000 characters, HTML converted to text). Output: JSON matching `ParsedMail` via a JSON schema / tool definition; validated like rule output; missing booking number or dates → `NOT_RECOGNISED`.
- The settings page shows whether AI is active and the notice that unrecognised mails including guest data are sent to Anthropic.

### D7: Review in the interface

- Booking DTO gains `agency`, `pets`, `reviewState`, `proposal`. Booking list filter `review=true` and column „Vermittler“; rows awaiting review get a badge „zu prüfen“; calendar bars get a dashed border.
- `BookingDialog`: banner „Aus E-Mail übernommen – bitte prüfen“ with *Bestätigen* / *Verwerfen*; for proposals a table old → new with *Übernehmen* / *Verwerfen*.
- Endpoints: `POST /api/vacationrental/bookings/{id}/confirm`, `…/discard`, `…/proposal/apply`, `…/proposal/discard`.
- New page *Ferienvermietung > Posteingang*: settings card (mailbox, cut-off date, status, last error, AI notice) and the inbox on `DataTable` with result badges and actions (Einheit zuordnen, Erneut verarbeiten, Ignorieren, Mail anzeigen, Ältere Mails abrufen).

### D8: Tests

- **Parsers:** unit tests with the five sample mails from exploration, anonymised (fictitious guests), stored as test resources: Fewo & Meer confirmation (adults only and with a child), short-notice notice, two cancellations, Holidu/DS confirmation.
- **Polling:** GreenMail IMAP server in tests: cut-off date, Message-ID dedup, read-only folder, wrong password message.
- **Engine:** integration tests for every row of D3, overlap → `PERIOD_TAKEN`, unit mapping and retry, rights (no EDITOR on the unit → `UNASSIGNED_UNIT`), manual booking without agency matched by number.
- **AI:** `AiMailParser` behind an interface; tests use a stub returning fixed JSON; no network in tests.
- **Frontend:** component tests for the review banner, proposal diff and apply, the inbox actions, and the settings form (password never shown).

## Risks / Trade-offs

- [Agencies change their mail layout] → Rules fail closed: an unrecognised mail goes to AI or „nicht erkannt“, never to a wrong booking. Parser tests pin the known layouts.
- [Guest data in stored mails] → Same visibility as booking documents (only the mailbox owner sees the inbox; booking documents need `EDITOR`). „Ignorieren“ followed by „Löschen“ removes the raw mail. Manual deletion as for guest data.
- [AI sends personal data to a third party] → Off by default, explicit notice, only for mails no rule recognises.
- [IMAP password in the database] → Encrypted with `SecretCipher`; never returned by the API; a new password must be entered to change host or user.
- [The backend can now reach the internet] → Only outbound, through the separate `egress` network; no port is published there, and db and backup stay isolated. Without it, mail import cannot work in the container stack.
- [Duplicate bookings from manual entry plus mail] → Lookup by booking number also matches bookings without agency (D3).

## Migration Plan

`V20261108.1200__vacationrental_mail_import.sql`: new tables, new booking columns with defaults, replaced unique index. Existing bookings keep `agency = NULL`, `review_state = 'NONE'`. New dependencies are added to `pom.xml`. In `compose.yml` the backend joins a new network `egress` (not internal, nothing published) for outbound IMAP and the optional Claude API, and receives the `LOGIS_AI_*` variables; db and backup stay on `internal` only.

## Open Questions

- A Holidu/DS cancellation or change mail and a Fewo & Meer change mail are not known yet; until rules exist, the AI fallback (if enabled) handles them. Add rules once samples arrive.
