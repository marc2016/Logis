# Design

## Context

`BookingMailHandler.handle` asks `MailRecognizer.recognize`, which runs the fixed `MailParser`s and then the AI, if it is enabled. Without a usable `ParsedMail`, the handler returns empty, and the core stores „Nicht erkannt“ (`NOT_RECOGNISED`).

`IGNORED` is a core result that the user sets by hand („Ignorieren“). It is labelled „Ignoriert“ in the frontend. A handler can report any result code through `Handled`.

`FewoUndMeerParser.fromAgency` decides whether a mail comes from Fewo & Meer: by its two sender domains or, for forwarded mails, by the agency's name or domain in the text.

## Goals / Non-Goals

**Goals:**
- Recognise receipts by rule and report them as ignored, before any parser or the AI looks at them.

**Non-Goals:**
- Ticket systems of other agencies.
- General user-defined ignore rules, e.g. „sender contains“ or „subject contains“.
- Changing stored mails automatically.

## Decisions

### D1 — A receipt check in the Fewo & Meer rules, run first

`FewoUndMeerParser` gets `Optional<String> ticketReceipt(MailContent mail)`. It returns the ticket ID when all of these hold:
- `fromAgency(mail)` is true;
- the text contains „vielen Dank für Ihre E-Mail“ (case-insensitive, whitespace normalised);
- the text contains „Ticket-ID:“ followed by an ID of the form `[A-Z]{3}-\d{3}-\d{5}`.

`MailRecognizer` exposes `ticketReceipt(mail)`, which asks the Fewo & Meer parser. `BookingMailHandler.handle` calls it before `recognize`. On a match it returns `Handled("IGNORED", null, "TICKET_RECEIPT", "Fewo & Meer", ticketId, null, null, null, null, "RULE")`.

Running it first means:
- a receipt that quotes a booking number in its subject is not attached to the booking;
- the AI is never asked about it.

**Alternatives considered:**
- **A new `ParsedMail.MailType` going through `ImportEngine`.** It would need special cases in `usable()` and in the engine for a mail that does nothing. Rejected.
- **A core-level auto-ignore rule.** It is more general, but it needs settings and UI; it is out of scope and listed as a non-goal.

### D2 — Result `IGNORED` from the handler

The handler reports the existing core code `IGNORED`, so the inbox shows „Ignoriert“ without a new result label. Unlike a mail ignored by hand, the stored mail keeps the handler's kind, source and reference. „Erneut verarbeiten“ stays available as for every handled mail.

The kind label „Eingangsbestätigung“ is added to `mailKindLabel` in the frontend, and to the backend labels if `mail-export-pseudonymization` is applied.

## Risks / Trade-offs

- **The agency changes its receipt text.** → Receipts fall back to „Nicht erkannt“, which is the same as today. No harm.
- **A staff reply quotes an earlier receipt** (contains both phrases). → Lines quoted with „>“ are left out before the check.

  Cutting at the first „Von:“/„From:“ line was considered and dropped: a receipt forwarded by the owner starts with exactly such a header and would be missed.

  A reply that quotes a receipt without „>“ (Outlook style) would still be ignored. Accepted: in the real export, none of the 65 staff replies in ticket threads contained the receipt text.
