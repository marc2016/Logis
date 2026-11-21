# Tasks

## 1. Statements: data and documents

- [ ] 1.1 Add the migration `V<yyyyMMdd>.<HHmm>__vacationrental_statement_import.sql` with `invoice_number`, `stated_payout` and the table `statement_open_lines` (design D7). Verify: the backend starts against a database with existing statements and they are unchanged.
- [ ] 1.2 Add invoice number and stated payout to `Statement`, the service input and the controller views, and return open-line count and the review reasons with list and detail (design D8). Verify with `StatementTest` cases: entered by hand; existing statement without both; payout differs → reason; equal → no reason.
- [ ] 1.3 Add open lines: entity, repository, listing for editors and count only for viewers, „Zuordnen“ (new line or added to the line of the same statement; refused for bookings on another statement, of another unit and owner uses) and „Verwerfen“; deleting a statement deletes them. Verify with `StatementTest` cases for each of these.
- [ ] 1.4 Add `StatementDocumentOwner` (EDITOR for read and write) and delete the documents with the statement (design D10). Verify with tests: editor reads the document, viewer gets 404, deleted with the statement.

## 2. Reading mails and PDFs

- [ ] 2.1 Add `MailAttachments` to `core/mail` (design D3). Verify with a unit test on built mails: PDF as attachment, PDF inside a forwarded mail, mail without attachment, a non-PDF named `.pdf`.
- [ ] 2.2 Add the PDFBox dependency and a test helper that builds a statement PDF of the Fewo & Meer layout from invented rows (design D11). Verify: the helper's PDF opens and its extracted text contains the three parts.
- [ ] 2.3 Add `FewoStatementPdf` (design D4, D5). Verify with unit tests on helper PDFs: normal rows; name with several words; extension row; „Gesamt“ check fails → not read; missing part → not read; text of another document → not read; page limit.
- [ ] 2.4 Check the reader against the real sample in the scratch directory, outside the repository: row count, sums and balance must equal the PDF's own totals. Adjust reader and helper until both agree. Verify: a note of the outcome in the apply summary, no file from the sample in `git status`.
- [ ] 2.5 Add the two recognition rules (design D2). Verify with unit tests on invented mails: both subjects, forwarded, wrong sender, month names including „März“, a subject with a dash in the object name.

## 3. Import

- [ ] 3.1 Add `StatementMailImport` and call it first in `BookingMailHandler` (design D1); add result `STATEMENT_CREATED` and kind `STATEMENT` with labels in backend and frontend. Verify: `MailLabelsTest` passes.
- [ ] 3.2 Create the statement with PDF document, lines and open lines (design D6). Verify with `MailImportTest` cases on invented data, one per scenario of „Fewo & Meer statement PDF“: match; booking of the previous month; extension merged; extension not taken over; no booking; booking already settled; owner use.
- [ ] 3.3 Cover the mail scenarios of „Statement mails“ in `MailImportTest`: unknown property → `UNASSIGNED_UNIT` and created after assigning; notice without property with one mapped unit; notice without a clear unit and the default mapping (design D9); invoice number already recorded → `ATTACHED`; no PDF and unreadable PDF → „PDF nicht gelesen“; stored „Nicht erkannt“ mail after „Erneut verarbeiten“; AI extractor never called.

## 4. Frontend

- [ ] 4.1 Statement list: mark „zu prüfen“; statement dialog and page header: invoice number and stated payout. Verify with frontend tests.
- [ ] 4.2 Statement page: review reasons, „Offene Zeilen“ with „Zuordnen“ (booking picker without settled bookings and owner uses) and „Verwerfen“, viewer sees the count only, hint „weicht vom Mietpreis ab“ on lines. Verify with frontend tests for each.
- [ ] 4.3 Statement page: „Dokumente“ with `DocumentList` for editors. Verify with a frontend test.
- [ ] 4.4 Inbox: labels „Abrechnung angelegt“ and „Abrechnung“, link to the statement for the statement link type, unit assignment for a mail without property name. Verify with inbox tests.

## 5. End-to-end

- [ ] 5.1 Add `e2e/tests/statement-mail.spec.ts`: a statement mail with a helper-built PDF from `e2e/fixtures/` arrives, the statement shows its lines and one open line as „zu prüfen“, the open line is assigned, the mark is gone, the PDF opens. Add the row to `e2e/COVERAGE.md`. Verify: the test passes on desktop and phone.

## 6. Verification

- [ ] 6.1 Run `cd backend && ./mvnw test` and `cd frontend && npm test && npm run lint && npm run build`. All must be green.
- [ ] 6.2 Run the full end-to-end suite against a stack with the new images. It must be green.
- [ ] 6.3 Search the diff for every tenant name, amount, invoice number, tax number, address, phone number and mail address of the real sample PDF and of the real export. None may occur, also not inside the committed fixture PDF (check its extracted text).

## Workflow follow-up

- Archive with `/opsx:archive` and sync the `booking-mails` and `rental-statements` specs.
