# Proposal

## Why

Nine date fields in forms and dialogs still use the browser's built-in date input. Safari draws it taller than the other fields, without a placeholder and wider than its column, and every browser shows it in its own format. The filters of the bookings and transactions lists already moved to the project's own date field (`DateField`: typed `TT.MM.JJJJ` with a calendar), as the inbox and the mail export did before. The forms should behave the same.

## What Changes

- These fields switch to `DateField`:
  - booking: „Anreise“, „Abreise“;
  - statement: „Zeitraum von“, „bis“, „Abrechnungsdatum“;
  - property cost: „Datum“;
  - transaction: „Datum“;
  - transfer: „Datum“;
  - mailbox: „Stichtag“.
- `DateField` learns to be a required field: an empty or invalid date stops the form from being saved, with the browser's usual hint at the field, and the existing messages „Bitte ein gültiges Datum eingeben.“ / „Das Datum darf nicht in der Zukunft liegen.“.
- No field keeps the built-in date input afterwards.

## Capabilities

### New Capabilities

### Modified Capabilities
- `app-shell`: new requirement „Date fields“. Every date field accepts `TT.MM.JJJJ`, offers a calendar and fits its column; required date fields block saving while empty or invalid.

## Impact

- **Frontend:**
  - `components/DateField.tsx` (required support);
  - `BookingForm`, `StatementDialog`, `CostDialog`, `TransactionDialog`, `TransferDialog`, `MailboxCard`;
  - their tests.
- **e2e:** tests that fill „Anreise“/„Abreise“ type German dates (`booking-calendar`, `owner-use`, `sharing`).
- **Unchanged:** API and backend; date values stay ISO dates in requests.
