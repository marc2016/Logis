# Proposal

## Why

Tenancies know what rent is agreed, but not what was paid. The owner cannot see which tenant is behind, by how much, and since when. This is the second of the five long-term rental changes (rent payments and arrears).

## What Changes

- **Rent due per month** is derived from the rent history: the entry valid in that month, prorated by days in the first and last month of a tenancy. Nothing is stored.
- **Payments** are recorded on the tenancy with date, amount, account and optional note. Recording one also creates the matching transaction in the chosen account (category „Mieteinnahmen“, the unit as target, the tenants as counterparty). Changing or deleting the payment changes or deletes that transaction; the transaction itself can no longer be changed in the finances.
- **Allocation by order:** payments settle the oldest open month first. The rent account of a tenancy shows per month the amount due, paid and open, the balance, and a credit when more was paid.
- **Overdue:** a month is overdue when it is not fully paid after its third working day (Monday to Friday, holidays not considered).
- The list of tenancies shows the arrears of each tenancy.
- **Property finances** get the payments as income on their payment date.
- **New start page tile „Mietrückstände“** of the long-term rental module: tenancies with overdue rent, largest arrears first. Not part of the default arrangement.
- A tenancy with payments can no longer be deleted. Changing its unit moves the target of its payment transactions along.

Not part of this change: deposits as payments, reminders and notifications, rent adjustments, utility statements, bank import.

## Capabilities

### New Capabilities

- `rent-payments`: rent due per month, payments with their transactions, allocation, arrears and the rent account of a tenancy.

### Modified Capabilities

- `transactions`: transactions created by a module are changed and deleted only through it.
- `property-finances`: the long-term rental module contributes rent payments as income.
- `dashboard`: the tile „Mietrückstände“ in the catalog.

## Impact

- Backend: new table `longtermrental.rent_payments` (new migration); `core.transactions` gets a nullable source (type and id) by a new core migration; existing transactions keep no source and stay editable. A public ledger interface for transactions managed by modules. `FinanceSource` of the long-term rental module.
- Frontend: rent account section on the tenancy page, payment dialog, arrears column in the list, the dashboard tile; read-only state of managed transactions in the finances.
- End-to-end: one new journey with a row in `e2e/COVERAGE.md`.
