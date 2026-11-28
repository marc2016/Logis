# Design

## Context

See proposal.md for the motivation.

- Tenancies (`modules/longtermrental`) have a rent history of terms (`validFrom`, cold rent, utilities advance, further items); the state „künftig/laufend/beendet“ is derived from the dates and `GermanTime.today`.
- Transactions live in `core/ledger`. They are entered by hand; `TransactionService.update`, `delete` and the bulk actions (which call the single endpoints per entry) are the only write paths besides transfers. The ledger has no public interface for other modules yet.
- Property finances do not count transactions; modules contribute items through `core/realestate/FinanceSource` (the vacation rental's `StatementFinanceSource` is the model).
- The start page tiles come from `LogisModule.dashboardWidgets` in the frontend.

## Goals / Non-Goals

**Goals:**

- One entry per payment: recorded once, it is in the tenancy, in the account and in the property finances.
- Arrears always computed from current data, never stored.

**Non-Goals:**

- Assigning a payment to a chosen month (allocation is by order only).
- Holidays in the due date, interest, reminders, notifications.
- Deposit payments.

## Decisions

### D1: Payments stored by the module, transactions created through a ledger interface

Table `longtermrental.rent_payments`: `id`, `tenancy_id` (FK, `ON DELETE RESTRICT`), `payment_date`, `amount NUMERIC(12,2) CHECK (amount > 0)`, `transaction_id BIGINT NOT NULL`, `note VARCHAR(500)`, `version`, `created_at`. No foreign key to `core.transactions`: modules own their schemas; the link is kept by the code in one transaction.

`core/ledger` gets a public class `ManagedTransactions` (named interface `ledger`) with `create`, `update` and `delete` for a given source type and id. It checks `EDITOR` on the account, resolves the category „Mieteinnahmen“ by name among the active income categories (none if archived or renamed) and sets the target to the unit.

Alternative: the payment is a transaction with a link back. Rejected: the tenancy would read the ledger for its own history, and a module's data would depend on core tables it does not own.

### D2: Managed transactions are read-only in the finances

New core migration: `core.transactions` gets `source_type VARCHAR(64)` and `source_id BIGINT`, both nullable, with a check that both are set or both null. Existing rows stay without a source. `TransactionService.update` and `delete` reject a transaction with a source (HTTP 409, „Diese Transaktion gehört zu einer Mietzahlung und wird dort geändert“); the bulk actions get the same answer per entry, so their summary counts it as failed. The transaction view gets `source` (type, id, label „Mietzahlung“) for the link to the tenancy. Users who see the transaction only through its target see the same.

### D3: Rent due and allocation are computed

`RentLedger` (pure, unit tested) takes the terms, start, end, payments and today and returns months with due, paid, open and overdue, plus arrears or credit:

- months from the start month to the current month (or the end month if earlier);
- due = total of the term valid on the first of the month, or on the start date in the first month; prorated `total * days / daysInMonth` in the first and last month, `HALF_UP` to cents;
- payments sorted by date then id, poured into the months oldest first; what is left after the current month is credit;
- overdue = open > 0 and today is after the third working day of the month (Mon–Fri), all in `GermanTime`.

The list of tenancies computes the arrears per tenancy with the same class; payments are loaded for all listed tenancies in one query.

### D4: API

- `GET /api/longtermrental/tenancies/{id}/rent-account`: months, totals, payments (account name only for editors).
- `POST`, `PUT`, `DELETE /api/longtermrental/tenancies/{id}/payments[/{paymentId}]` with `date`, `amount`, `accountId`, `note`, `version`.
- The tenancy list view gets `arrears` and `overdue`.
- Deleting a tenancy with payments: 409 with the spec message. Changing its unit: `ManagedTransactions.update` for each payment's transaction in the same transaction.

### D5: Property finances

`RentPaymentFinanceSource` contributes each payment as income on its unit, label „Miete <tenant names>“, dated on the payment date, only while the module is active. Transactions are not part of the finances (unchanged), so nothing counts twice.

### D6: Frontend

- Tenancy page: section „Mietkonto“ with the totals (arrears or credit), a month table (Monat, Soll, Gezahlt, Offen, overdue mark), the payments (Datum, Betrag, Konto, Notiz) and „Zahlung erfassen“ for editors. Dialog with date (`DateField`), amount, account (accounts the user may edit, the last used one preselected through the user preference `longtermrental.paymentAccount`) and note.
- List „Mietverhältnisse“: column „Rückstand“.
- Finances: managed transactions show „Mietzahlung“ with a link and no edit actions.
- Tile „Mietrückstände“ in `modules/longtermrental/dashboard`.

## Risks / Trade-offs

- [The tenancy and the ledger change in one database transaction across schemas] → Both are in the same database; the module calls `ManagedTransactions` inside its own `@Transactional` method, so either both change or neither.
- [The category „Mieteinnahmen“ was renamed or archived] → The transaction gets no category; nothing fails.
- [An account is archived later] → Its managed transactions stay; a new payment cannot use it.
- [Long tenancies make many months] → A few hundred rows at most; computed per request without caching.
- [Allocation by order hides which month a late payment was meant for] → Accepted (decision of the owner); the payments list keeps every payment with its date.

## Migration Plan

1. Core migration adds the nullable source columns; existing transactions keep `NULL` and stay editable.
2. Long-term rental migration adds `rent_payments`, empty.
3. Rollback: older versions ignore the new columns and table; transactions created by payments then become editable again, which loses nothing.
