# Proposal

## Why

Many tasks in Logis come in batches:
- confirming a dozen imported bookings „zu prüfen“;
- deleting old mails in the inbox, or assigning a unit to several mails of the same agency;
- giving forty bank transactions the right category or property;
- putting all bookings of a month onto the agency's statement.

Today every one of these takes opening each entry and acting on it alone.

## What Changes

- **Common selection behavior** for the lists named below:
  - a checkbox per row and a checkbox for all rows of the current page;
  - Shift-click selects a range;
  - an action bar shows the number of selected entries and the actions that fit them.
  - Destructive actions ask for confirmation first.
  - Each entry is processed on its own under the usual rules. Afterwards a summary names what worked and why the rest did not, for example „8 gelöscht, 2 nicht: Die Buchung ist abgerechnet und kann nur storniert werden“. The entries that failed stay selected.
  - Changing filters, sorting or page clears the selection.
- **Bookings:**
  - confirm the selected bookings „zu prüfen“;
  - cancel them;
  - delete them;
  - assign them to a statement of their unit (gross = expected rent, with a commission rate and a cleaning amount for all of them).
- **Inbox:**
  - assign a unit to the selected mails that wait for one;
  - retry processing;
  - ignore;
  - delete.
- **Transactions:**
  - assign a category;
  - assign a property or unit, or remove the attribution;
  - delete.
- **Statements:**
  - delete the selected statements from the list;
  - on a statement page, add several bookings of the unit at once (with commission rate and cleaning), and remove selected lines.
- Users without editing rights see no checkboxes. Entries they may not change fail with the usual message.

## Capabilities

### New Capabilities
- `bulk-selection`: how entries are selected in lists, how bulk actions confirm, run and report partial failures.

### Modified Capabilities
- `bookings`: bulk confirm, cancel, delete and assign to a statement.
- `mail-inbox`: bulk assign a unit, retry, ignore and delete.
- `transactions`: bulk assign a category or a target, and delete.
- `rental-statements`: bulk delete statements, add and remove several lines.

## Impact

- **Frontend:**
  - `DataTable` gets optional row selection with page-wide and range selection.
  - A shared `BulkActionBar` with confirmation and a shared runner with the summary.
  - `TransactionTable` and the statement tables, which are plain tables today, get the same selection.
  - Pages: `BuchungenPage`, `PosteingangPage`, the transaction pages, `AbrechnungenPage`, `StatementPage`.
- **Backend:** no new endpoints. Bulk actions call the existing endpoints per entry, so rights, rules and messages stay exactly as they are. Version conflicts of transactions surface as failures in the summary.
- **Tests:** frontend tests per list (selection, action, partial failure, rights) and one e2e flow.
