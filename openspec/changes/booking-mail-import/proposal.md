# Proposal

## Why

Booking confirmations, short-notice notices and cancellations arrive by email from the rental agencies (Fewo & Meer, Holidu via DS Destination Solutions). Today every booking is typed in by hand from these emails. This change lets Logis fetch the emails from a mailbox, read them, and prepare bookings and changes, so that the user only has to confirm them. The email stays attached to the booking as its source.

Guiding decisions from exploration:

- **Mail first, bank import later:** The roadmap item `core-bank-import` turned out to be about these emails, not about bank statements. Importing bank transactions stays a separate, later change.
- **Only booking emails:** confirmations, short-notice („Last-Minute“) notices, changes and cancellations. Statements, invoices and bank notifications are out of scope.
- **IMAP fetch:** Logis polls a mailbox or folder that the user configures, starting from a cut-off date. Logis does not receive email itself.
- **Rules first, AI as fallback:** Known agencies and mail types are read by fixed rules, and guest data stays on the server. Unknown mails go to an AI model (Claude API) only if AI is enabled.
- **Prepared, then confirmed:** Every recognised mail becomes a booking or a change with the state „zu prüfen“ and carries the original mail as a document. The user confirms or discards it.

## What Changes

- **Mailbox per user:** IMAP host, port, user, password (encrypted), folder, cut-off date and an on/off switch, under *Ferienvermietung > Posteingang*. Logis polls it every few minutes, with no manual action.
- **Recognition:**
  - Rules for Fewo & Meer: confirmation („Neue Buchung für …“), short-notice notice („Last-Minute Buchung …“) and cancellation („Stornierung …“).
  - Rules for Holidu/DS: confirmation („Buchungsmitteilung“).
  - Everything else goes to the AI if AI is enabled, otherwise it is listed as not recognised.
- **Effects on bookings:**
  - A new booking number creates a booking with the state „zu prüfen“ and the mail attached. It occupies the calendar right away.
  - A known booking number with different data, or a cancellation, proposes a change on the existing booking. A mail without differences is only attached.
  - Mails that cannot be matched to a unit or booking stay in the inbox with a reason.
- **Unit mapping:** The property name in the mail („Haus Seeufer - Nordhorn“, „Ferienhaus Seeufer“) is mapped to a unit once per agency and remembered.
- **Inbox page:** All fetched mails with their result (Buchung angelegt, Änderung vorgeschlagen, angehängt, Duplikat, nicht zugeordnet, nicht erkannt), with actions to assign a unit, retry or ignore, and to fetch older mails from a chosen date.
- **Bookings:** new fields „Vermittler“ (agency, free text) and „Haustiere“ (pets). „zu prüfen“ markers and the actions *Bestätigen* and *Verwerfen* in the grid, the calendar and the dialog. The booking number becomes unique per unit and agency.
- **Documents:** The original mail is stored as an `.eml` document of the booking, plus a readable text preview.

## Capabilities

### New Capabilities

- `mail-import`: Mailbox configuration, polling from a cut-off date, recognition by rules and optional AI, unit mapping per agency, the effects on bookings, and the inbox with results and actions.

### Modified Capabilities

- `bookings`: Bookings and changes awaiting review, confirmation and discarding, the agency and pets fields, and booking numbers that are unique per unit and agency.

## Impact

- **Backend:**
  - In `app.logis.modules.vacationrental`: a new mail import (mailbox, poller, parsers, inbox), review state and pending changes on bookings, and Flyway migrations in `vacationrental`.
  - A trusted store method in `core.documents` for `.eml` files created by the server.
  - Scheduling gets enabled.
- **Dependencies:** `spring-boot-starter-mail` (IMAP), `jsoup` (HTML to text), the Anthropic Java SDK (optional AI fallback), and GreenMail for tests.
- **Configuration:** `LOGIS_AI_ENABLED`, `LOGIS_ANTHROPIC_API_KEY` and `LOGIS_AI_MODEL`, with AI off by default. Outbound network to the IMAP host and, with AI, to `api.anthropic.com`.
- **Frontend:**
  - A new page „Posteingang“ with mailbox settings.
  - „zu prüfen“ markers and actions in the grid, the calendar and `BookingDialog`.
  - Fields „Vermittler“ and „Haustiere“.
- **Privacy:** Mails contain guest data. They are stored in the database like other booking documents. With AI enabled, unrecognised mails, including guest data, are sent to Anthropic. The settings page states this.
- **Assumptions** (proposed during exploration, not explicitly confirmed; easy to change):
  - One mailbox per user. Imported bookings are created with that user's rights, so only on units where the user has `EDITOR`.
  - For Holidu, the expected rent is the total price („REISEPREIS“). Breakdown and VAT stay in the attached mail.
  - Polling every 5 minutes, and only unread-by-Logis mails (by Message-ID) from the cut-off date on. Mails are never changed or deleted in the mailbox.
  - When there are two phone numbers, the mobile number is taken if the landline is empty.
  - A booking awaiting review counts like a planned booking for the overlap rule. If it would overlap, no booking is created and the inbox shows the conflict.
  - AI model by default `claude-opus-5-5`, configurable.
