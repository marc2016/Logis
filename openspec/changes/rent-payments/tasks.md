# Tasks

## 1. Ledger

- [x] 1.1 Core migration `V<yyyyMMdd>.<HHmm>__core_transaction_source.sql` with nullable `source_type`, `source_id` and the both-or-none check (design D2). Verify with a migration test: existing transactions unchanged, no source.
- [x] 1.2 `ManagedTransactions` in `core/ledger` with a named interface: create, update, delete with rights check, category „Mieteinnahmen“ and unit target (design D1). Verify with `ManagedTransactionsTest`: created with category and target; account without `EDITOR` refused; archived category → none.
- [x] 1.3 Reject changing and deleting managed transactions, also in bulk, and expose their source in the transaction view (design D2). Verify with `TransactionsTest` cases: update and delete refused with the message; normal transactions unchanged; view shows the source.

## 2. Rent payments

- [ ] 2.1 Migration `V<yyyyMMdd>.<HHmm>__longtermrental_rent_payments.sql` (design D1). Verify: the backend starts against a database with existing tenancies.
- [ ] 2.2 `RentLedger` (design D3). Verify with a unit test per scenario of „Rent due per month“ and „Allocation and arrears“: full months, mid-month start and end, rent change, one month behind, partial payment, credit, second vs. after the third working day, a month starting on a weekend.
- [ ] 2.3 Payments service and endpoints with the transaction in one database transaction (design D4). Verify with `RentPaymentsTest`: record, change, delete with their transactions; account without rights; amount ≤ 0; viewer sees no account and cannot record.
- [ ] 2.4 Rent account endpoint and arrears in the tenancy list (design D3, D4). Verify with `RentPaymentsTest` cases for the account view and the list.
- [ ] 2.5 Tenancy delete refused with payments; unit change moves transaction targets (design D4). Verify with `RentPaymentsTest` cases.
- [ ] 2.6 `RentPaymentFinanceSource` (design D5). Verify with a test: income on the property, not counted twice, absent while the module is disabled.

## 3. Frontend

- [ ] 3.1 API types and hooks; section „Mietkonto“ with totals, months, payments and overdue marks on the tenancy page (design D6). Verify with `TenancyPage.test.tsx` cases including a viewer.
- [ ] 3.2 Payment dialog: record, change, delete; remembered account (design D6). Verify with frontend tests.
- [ ] 3.3 Column „Rückstand“ in „Mietverhältnisse“. Verify with `Mietverhaeltnisse.test.tsx`.
- [ ] 3.4 Managed transactions in the finances: „Mietzahlung“ link, no edit actions, bulk summary. Verify with `Ledger.test.tsx` cases.
- [ ] 3.5 Tile „Mietrückstände“ in the long-term rental module. Verify with `Dashboard.test.tsx` cases: list, empty state, module inactive.

## 4. End-to-end

- [ ] 4.1 `e2e/tests/rent-payments.spec.ts`: a tenancy two months old, one payment recorded into an account, the rent account shows one month open and overdue, the account has the transaction, which cannot be edited, the list shows the arrears, the tile lists the tenancy. Add the row in `e2e/COVERAGE.md`. Verify on desktop and phone.
- [ ] 4.2 Add the tenancy page with its rent account to `layout.spec.ts`. Verify: passes.

## 5. Verification

- [ ] 5.1 Run `cd backend && ./mvnw test` and `cd frontend && npm test && npm run lint && npm run build`. All must be green.
- [ ] 5.2 Run the full end-to-end suite against a stack with new images. It must be green.

## Workflow follow-up

- Archive with `/opsx:archive` and sync the specs `rent-payments`, `transactions`, `property-finances` and `dashboard`.
