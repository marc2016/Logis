# Design

## Context

See proposal.md for the motivation.

- Statements exist (`Statement`, `StatementLine`, `StatementService`): one statement per unit with provider, period and date; one line per booking with gross, commission, cleaning. A line is keyed by its booking, so a booking is on at most one statement.
- `MailService` stores every mail completely (`raw`) and offers it to the `MailHandler`s; `BookingMailHandler` is the only handler of the module. It already handles one kind of mail before the recogniser and the AI (ticket receipts).
- `MailContent` carries sender, subject and text only. Attachments are not read anywhere yet.
- Property names are mapped to units per agency (`UnitMapping`); an unmapped mail waits as `UNASSIGNED_UNIT`, and `BookingMailController.assign` stores the mapping and reprocesses the waiting mails of `BookingMailHandler.KEY`.
- Documents hang on owners through `DocumentOwnerType`; bookings have one, statements do not. `Documents.store` accepts `application/pdf` from the server.
- `StoredMail.retryable()` is true for every mail without a link, so stored statement mails can be processed again.
- Observed in a real sample (not in the repository): the Fewo & Meer PDF is a text PDF, A4 landscape, with the same header on every page and these parts: „Provisionsabrechnung“ (per tenant: rental period, rent, additional services, commission, fee, net, VAT, gross), „Abrechnung Mieten, Rest- und Anzahlungen“ (per tenant: rental period, booking date, rent, deposit and consumption columns, additional services, remaining payment), „Alle Zahlungen zur Übersicht“ with „Saldo“. The commission table is repeated on a later page. Rows carry no booking number. An extension is a row of its own with „Verlängerungsbuchung“ on the next line.

## Goals / Non-Goals

**Goals:**

- Statement mails end as statements with lines, without typing.
- Everything the import could not settle is visible on the statement and can be fixed there.
- No value of a real statement in the repository.

**Non-Goals:**

- A general PDF table reader. The reader knows the one layout.
- Reading the PDF of DS Destination Solutions.
- Changing how net and payout are calculated.
- A stored review state for statements.

## Decisions

### D1: Statement mails are handled inside `BookingMailHandler`, before the recogniser

A new component `StatementMailImport` is asked first in `BookingMailHandler.handle`, like ticket receipts. If it recognises the mail, the handler returns its `Handled` and the recogniser and the AI are never reached.

- Why not a second `MailHandler`: the inbox labels, `MailLabelsTest`, the unit assignment in the inbox and `reprocessWaiting` are all tied to `BookingMailHandler.KEY`. A second handler would need its own copies of all of them for no gain.
- New result `STATEMENT_CREATED` („Abrechnung angelegt“) and kind `STATEMENT` („Abrechnung“) in `BookingMailResult`, `RESULT_LABELS`, `KIND_LABELS` and the frontend maps. `ATTACHED` and `UNASSIGNED_UNIT` are reused.
- `Handled.linkType` is `Statement.SOURCE_TYPE`; the inbox learns to link that type to the statement page.

### D2: Recognition rules

Both rules work on `businessSubject()`, so forwards are covered.

- Fewo & Meer: sender domain of the agency (the same check `FewoUndMeerParser` uses) and subject `Ihre Abrechnung für <Monat> <Jahr> - <Objekt>`. German month names, the object is the rest of the subject. The period is the calendar month.
- DS Destination Solutions: subject `Ihre Abrechnung: <number>` and a text containing „Eigentümerabrechnung“ and „Zeitraum vom <date> bis zum <date>“. The sender's domain is not known, the text check is specific enough. The agency is the sender's display name (of the attached original, of an inline „Von:“ line, or of the mail), passed through `HoliduDsParser.agency` like the intermediary of confirmations. This mail has no property name.

### D3: Attachments come from the raw mail

`core/mail` gets a small public reader, `MailAttachments.of(raw)`, returning file name and bytes of every attachment part, also inside a forwarded `message/rfc822`. The importer takes the first one that `FileType` identifies as PDF by its content, not by its name or declared type.

- Why not extend `MailContent`: it is created for every mail and passed to all parsers; attachments are needed by this one path only and can be megabytes.

### D4: PDF text with PDFBox, layout reader for Fewo & Meer

- Dependency: `org.apache.pdfbox:pdfbox` 3.x. Text is extracted with `PDFTextStripper` and `setSortByPosition(true)`, which yields one text line per table row.
- `FewoStatementPdf` reads:
  - header: `(Re-Nr. <number>)` and `(Rechnungs-Datum: <date>)`;
  - rows: a line with `<name> <date> - <date>` followed by amounts. Amounts are read from the right, so names with spaces need no column positions;
  - from the first „Provisionsabrechnung“ table: the last amount (commission including VAT);
  - from „Abrechnung Mieten, Rest- und Anzahlungen“: the last amount (rent plus additional services);
  - „Saldo“.
- Rows of the two tables are joined by tenant name and period in order of appearance. The repeated commission table is ignored. „Gesamt“ rows are used as a check: if a table's rows do not sum to its „Gesamt“, the PDF counts as not read.
- Any surprise (missing part, unjoinable rows, failed check, encrypted or image-only PDF) makes the whole PDF „nicht gelesen“. Half-read statements are worse than empty ones.
- Limits: PDFs over 10 MB or 50 pages are not read.
- Alternative considered: AI extraction of the PDF. Rejected, because the layout is fixed and the PDF holds tenant names and amounts.

### D5: Amount mapping without a model change

Gross = last column of the payment table (rent + additional services), commission = commission including VAT, cleaning = 0. Net and payout stay as they are and then equal the PDF's balance. The booking mails carry the total price, so gross stays comparable with the booking's expected rent.

### D6: Matching rows to bookings

1. Group rows of the same tenant name whose periods touch (one's departure is the other's arrival), wherever they stand in the PDF: the extension row is not next to its booking's row. The mark „Verlängerungsbuchung“ stands on the line below its row. Try each group as one row with the combined period and added amounts.
2. If no booking has the combined period, fall back to the rows one by one.
3. A row matches the guest booking of the unit with exactly its period; a cancelled booking only if no other one has the period. The name is not compared.
4. A matched booking that is on another statement, and every unmatched row, becomes an open line (note „bereits abgerechnet“ in the first case, „Verlängerungsbuchung“ kept for extension rows).

Lines are written through `StatementService`, so its rules (same unit, no owner use, one statement per booking) stay in one place.

### D7: Data model

New migration `V<date>__vacationrental_statement_import.sql`:

- `statements.invoice_number VARCHAR(64) NULL`, `statements.stated_payout NUMERIC(12,2) NULL`.
- Table `statement_open_lines`: `id`, `statement_id` (FK, `ON DELETE CASCADE`), `guest_name VARCHAR(128)`, `arrival DATE`, `departure DATE`, `gross NUMERIC(12,2)`, `commission NUMERIC(12,2)`, `note VARCHAR(128) NULL`.
- No unique index on the invoice number: statements entered by hand may repeat one. Duplicates are checked by the importer only (same provider, same invoice number, among the units the mailbox owner can edit).

Existing rows are untouched; both new columns stay empty for them.

### D8: „zu prüfen“ is derived, not stored

Open-line count and `stated_payout` versus payout are computed when a statement is read; the list gets both in its existing query result. The per-line hint compares the line's gross with the booking's expected rent in the frontend, which already has both.

- Why not a stored flag: it would have to be kept right on every edit of lines and bookings. The derived mark clears itself.

### D9: Unit of a statement mail

- With a property name: the existing `UnitMapping` lookup, including the EDITOR check on the mailbox owner.
- Without one (DS Destination Solutions): a mapping of the agency with the property name „Abrechnungen“ is the agency's unit for statements. If there is none and all mappings of the agency point to one unit, that unit is used. Otherwise `UNASSIGNED_UNIT` with `subjectName` „Abrechnungen“; the existing assign endpoint stores that mapping, and its reprocessing picks the mail up. A visible name instead of an empty one keeps the inbox action and the agency page unchanged.
- The PDF names the object the way booking mails do; the subject sometimes differs. When the PDF is read, its object is the property name.

### D10: Documents on statements

`StatementDocumentOwner` with type `Statement.SOURCE_TYPE`; read and write need `EDITOR`, as for bookings, because the PDF names tenants. `StatementService.delete` calls `Documents.deleteAll`. The statement page shows the existing `DocumentList`. Only the PDF is stored, not the mail; the mail stays reachable through the inbox.

### D11: Test data

- A test helper builds statement PDFs of the same layout with PDFBox from invented rows. All PDF tests and the mail tests use it.
- The end-to-end test attaches a PDF produced by that helper with invented values, committed under `e2e/fixtures/`.
- The real sample is used once, outside the repository, to check that the reader handles the true layout.

## Risks / Trade-offs

- [The generated test PDF differs from the real layout in ways the tests cannot see] → Run the reader against the real sample in the scratch directory during apply; fix the reader and the helper until both behave the same.
- [Fewo & Meer changes the layout] → The PDF counts as not read; the statement is still created with the PDF attached and can be filled by hand.
- [Deposits, consumption or deposit refunds change the balance in ways the sample does not show] → The stated payout exposes the difference as „zu prüfen“; the owner corrects the lines.
- [Two bookings of a unit cannot share a period, but a cancelled and a new one can] → Rule 3 of D6 prefers the booking that is not cancelled.
- [A malicious or broken PDF] → Size and page limits, and every PDFBox error ends as „PDF nicht gelesen“ without failing the mail.
- [Processing thirty stored mails creates thirty statements at once that count in the finances] → That is the purpose; each can be deleted, and a deleted statement's mail can be processed again.

## Migration Plan

1. Deploy: Flyway adds the columns and the table.
2. The owner processes the stored statement mails again from the inbox.
3. Rollback: the previous version ignores the new columns and table; imported statements stay usable as ordinary statements.
