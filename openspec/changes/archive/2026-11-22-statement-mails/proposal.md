# Proposal

## Why

Agencies send their statements by mail, with all amounts in an attached PDF. Today these mails end as „Nicht erkannt“, and every statement and each of its lines has to be typed in by hand. About thirty such mails already wait in the inbox.

## What Changes

- Statement mails are recognised by rules, never by AI:
  - Fewo & Meer: „Ihre Abrechnung für `<Monat Jahr>` - `<Objekt>`“ with a PDF.
  - The owner statement notice of the booking system DS Destination Solutions: „Ihre Abrechnung: `<Rechnungsnummer>`“ with the period in the text.
- A recognised mail creates a statement for the unit (provider, period, statement date, invoice number) and attaches the PDF to it as a document. The inbox shows „Abrechnung angelegt“ with a link to the statement.
- The Fewo & Meer PDF is read: one line per tenant with rental period and amounts, and the balance.
  - Gross is rent plus additional services, commission is the commission including VAT, cleaning is 0. The payout then equals the balance of the PDF.
  - A line is assigned to the booking of the unit with the same period. An extension line is added to the line of the booking it extends.
  - A line without a booking stays on the statement as an open line and can be assigned or discarded by hand.
- A statement shows „zu prüfen“ while it has open lines or its payout differs from the balance stated in the PDF. A line whose gross differs from the booking's expected rent is marked.
- A mail with an invoice number that is already recorded does not create a second statement.
- Statements get documents: the imported PDF, and files added by hand.
- The PDF of DS Destination Solutions is not read yet (no sample). Its statement is created without lines, with the PDF attached.
- Stored statement mails are picked up with the existing „Erneut verarbeiten“.

Not part of this change: reading other agencies' PDFs, AI extraction of PDFs, keeping attachments in the admin mail export.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `booking-mails`: statement mails are recognised and turned into statements.
- `rental-statements`: imported statements with invoice number and stated payout, open lines, the review mark, and documents on statements.

## Impact

- Backend, module `vacationrental`: statement mail rules and PDF reader, statement import, new columns on `statements`, new table for open lines, document owner type for statements, new endpoints for open lines. New Flyway migration; existing statements keep their data and get empty new columns.
- Backend, `core/mail`: reading the attachments of a stored mail.
- New dependency: Apache PDFBox for PDF text extraction (and for creating test PDFs).
- Frontend: statement list and page (review mark, invoice number, open lines, documents), inbox labels and link to the statement.
- Imported statements count in the property finances like statements entered by hand.
- Tests use generated PDFs and mails with invented values only; no value of a real statement enters the repository.
- End-to-end: one new journey with a row in `e2e/COVERAGE.md`.
