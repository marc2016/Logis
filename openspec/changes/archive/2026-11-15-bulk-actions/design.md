# Design

## Context

- **List components.**
  - `DataTable` (TanStack Table) renders the booking list and the inbox. It has server paging and sorting and a row click, but no selection.
  - `TransactionTable` and the statement tables (`AbrechnungenPage`, `StatementPage`) are plain shadcn `Table`s.
- **Endpoints.** Every action in scope already exists per entry:
  - bookings: `POST /bookings/{id}/confirm`, `/cancel`, `DELETE /bookings/{id}`, and `PUT /statements/{sid}/lines/{bookingId}` with gross, commission and cleaning;
  - inbox: `POST /api/mail/inbox/{id}/retry`, `/ignore`, `DELETE /api/mail/inbox/{id}`, and `POST /api/vacationrental/mail/{id}/assign`;
  - transactions: `PUT /api/transactions/{id}`, which needs the full body and the version, `DELETE /api/transactions/{id}`, and `DELETE /api/transfers/{transferId}` for transfers;
  - statements: `DELETE /statements/{id}` and `DELETE /statements/{id}/lines/{bookingId}`.
- **Messages.** The backend already returns German messages for every rule, for example „Die Buchung ist abgerechnet und kann nur storniert werden“, „Die Buchung gehört zu einer anderen Einheit“ and „Umbuchungen werden über die Umbuchung bearbeitet.“
- **Rights.** Each view carries `canEdit` per booking, statement and transaction. Inbox mails always belong to the user.

## Goals / Non-Goals

**Goals:**
- One selection and bulk-action pattern, reused by all lists in scope.
- Exactly the same rules and messages as the single actions.

**Non-Goals:**
- Selection across pages or „all results of the filter“.
- Undo.
- Bulk editing of other fields, such as dates or amounts.
- Bulk actions in further lists: agencies, users, property costs.

## Decisions

### D1. Per-entry calls from the frontend, no bulk endpoints
- A shared `runBulk(items, action, {concurrency: 4})` calls the existing endpoint per entry and collects `{ok, failed: Map<message, items>}`.
- *Why:* Rights, validation, optimistic locking and messages stay in one place. Partial failure is natural, because each call is its own transaction. A page holds at most 100 entries, so at most 100 calls run, four at a time.
- *Alternative:* bulk endpoints per resource. Rejected for now because they would duplicate rules and their reporting, and the volumes are small. They can be added later behind the same `runBulk` interface if needed.
- Transfers in a deletion are grouped by `transferId` and deleted once through `/api/transfers/{transferId}`. Category or target changes on transfers go to `PUT /api/transactions/{id}` and fail there with the backend message.
- Transaction updates send the row's full current data with the changed field and its version. A concurrent change therefore fails with „Die Transaktion wurde inzwischen geändert“.

### D2. Selection in `DataTable`
- New optional props:
  - `selectable?: (row) => boolean` decides which rows get a checkbox;
  - `selection` and `onSelectionChange`, a set of row ids controlled by the page;
  - `getRowId`.
- The first column renders the checkbox. Clicking it stops the event, so the row does not open. The header checkbox selects or clears all selectable rows of the page and shows an indeterminate state for a partial selection.
- Shift-click selects the range between the last clicked row and this one, among selectable rows.
- Pages clear the selection whenever the query parameters change (filter, sort, page).
- `TransactionTable` and the statement tables get the same checkbox column through a small `SelectionColumn` helper, so they do not have to move to `DataTable`.

### D3. Action bar and report
- `BulkActionBar` is sticky at the bottom of the list while the selection is not empty. It shows „N ausgewählt“, the page's action buttons and „Auswahl aufheben“.
- Destructive actions use `ConfirmButton` with „N Einträge löschen?“.
- Actions that need input open a small dialog. These are the statement choice with commission % and cleaning, the category choice, the target choice and the unit choice.
- After the run:
  - a toast with „X <Verb>“, for example „6 bestätigt“, and per failure message „Y nicht: <Meldung>“;
  - the failed rows stay selected, the others are deselected;
  - the page invalidates its queries.

### D4. Statement assignment
- `BulkAssignDialog` (statement, commission %, cleaning €) serves the booking list and the statement page.
  - From the booking list the user chooses a statement. The choice offers statements of the selected bookings' units, plus „Neue Abrechnung …“, which opens the existing `StatementDialog` first.
  - On the statement page the dialog lists the unit's unassigned bookings with checkboxes, preselected for the statement period.
- Per booking it sends gross = expected rent (missing gives the backend message), commission = round(gross × rate / 100, 2), and cleaning.

### D5. Inbox unit assignment
- The booking module contributes bulk actions through the module hook, in the same way it contributes `MailActions` today. A new optional `bulkMailActions` lists actions with a filter (`result === 'UNASSIGNED_UNIT'`) and a runner.
- Assigning calls the assign endpoint per mail. The backend then processes the other waiting mails with the same agency and name. So mails that are already processed by an earlier call of the same run are counted as done, not as failures: a 400 or 404 is followed by a check of the mail's new result.

## Risks / Trade-offs

- [Many calls for one action] → Four at a time, at most one page. The duration shows as a busy state on the action bar.
- [Selection lost on refetch after an action] → Selection is by id, and rows that still exist keep their state.
- [Assigning bookings from several units at once] → Only bookings of the statement's unit succeed. The rest fail with the backend message, which says why.
