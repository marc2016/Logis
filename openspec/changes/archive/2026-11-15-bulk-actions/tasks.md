# Tasks

## 1. Shared selection and bulk runner

- [x] 1.1 Add `runBulk` (per-entry calls, concurrency 4, results grouped by message) and `bulkToast` (summary text) (design D1, D3). Verify with unit tests: all succeed, mixed failures grouped by message, and the concurrency limit.
- [x] 1.2 Add selection to `DataTable` (`selectable`, `selection`, `onSelectionChange`, `getRowId`, header checkbox with indeterminate state, Shift range, the checkbox not opening the row) and a `SelectionColumn` helper for plain tables (design D2). Verify in `DataTable.test.tsx`: select a page, the range, non-selectable rows, and that a click on the checkbox does not trigger the row click.
- [x] 1.3 Add `BulkActionBar` (count, actions, „Auswahl aufheben“, busy state) with `ConfirmButton` for destructive actions. Verify in a component test.

## 2. Bookings

- [x] 2.1 Add selection to `BuchungenPage` (selectable = `canEdit`, cleared on query change) and the actions „Bestätigen“ (only rows „zu prüfen“ count; others fail with a message), „Stornieren“ and „Löschen“. Verify in `VacationRental.test.tsx`: confirm all, delete with a settled booking failing and staying selected, a viewer sees no checkboxes.
- [x] 2.2 Add `BulkAssignDialog` (statement choice by unit, „Neue Abrechnung …“, commission %, cleaning) and „Abrechnung zuordnen“ (design D4). Verify in tests: the amounts sent per booking, and a booking of another unit failing with the backend message.

## 3. Inbox

- [x] 3.1 Add selection to `PosteingangPage` with „Erneut verarbeiten“, „Ignorieren“ and „Löschen“. Verify in `Mail.test.tsx`.
- [x] 3.2 Add the module hook `bulkMailActions` and the booking module's „Einheit zuordnen“ for waiting mails, counting mails already processed by an earlier call as done (design D5). Verify in tests: three waiting mails, one assign call that processes all three, and the summary „3 zugeordnet“.

## 4. Transactions

- [x] 4.1 Add selection to `TransactionTable` (selectable = `canEdit`) with „Kategorie zuordnen“ (active categories), „Objekt zuordnen“ (active properties and units, or none) and „Löschen“ (transfers grouped by `transferId`) (design D1). Verify in the ledger tests:
  - category assigned to several transactions with the full body and version;
  - a transfer fails with „Umbuchungen werden über die Umbuchung bearbeitet.“;
  - both sides of a transfer are deleted with one call;
  - a version conflict is reported.

## 5. Statements

- [x] 5.1 Add selection and „Löschen“ to the list of statements on `AbrechnungenPage`. Verify in tests.
- [x] 5.2 Add „Buchungen zuordnen“ on `StatementPage`: the unit's unassigned bookings, preselected for the period, commission % and cleaning. Add selection of lines with „Entfernen“. Verify in tests: 7 lines added with the right amounts, 2 lines removed, and the payout updated.

## 6. Integration

- [x] 6.1 Add an e2e test: select several bookings and cancel them together, then fill a statement with „Buchungen zuordnen“. Bookings „zu prüfen“ only arise from the mail import, so bulk confirming is covered by the frontend tests. Verify that it type-checks (`npx tsc --noEmit` in `e2e`).
- [x] 6.2 Run `cd frontend && npm test && npm run lint && npm run build`, and `cd backend && ./mvnw test` to make sure nothing changed there, and verify that all pass.
