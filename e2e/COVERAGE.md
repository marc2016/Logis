# Test coverage per capability

For every capability in `openspec/specs/`, this table names the end-to-end journeys that run in a real browser against the shipped stack (`e2e/tests`) and the layer that covers the rules and edge cases the suite does not. A change that adds a user-facing journey adds an end-to-end test and updates its row.

- **e2e:** Playwright against `compose.yml`, desktop and phone (`e2e/tests/*.spec.ts`).
- **Frontend:** vitest with a mocked API (`frontend/src/**/*.test.tsx`).
- **Backend:** Spring tests with Testcontainers (`backend/src/test/java/**`).

| Capability | e2e | Frontend | Backend | Remarks |
|---|---|---|---|---|
| accounts | `finances.spec.ts` (create account, balances) | `Ledger.test.tsx` | `AccountsTest`, `AmountsAndIbanTest` | |
| agencies | `agencies.spec.ts` (rename, merge) | `Vermittler.test.tsx` | `AgenciesTest`, `AgencyMigrationTest` | |
| app-shell | `smoke.spec.ts`, navigation in every test (phone menu included) | `AppShell.test.tsx` | – | |
| authentication | `auth.spec.ts` (2FA setup, codes, own password), `passkeys.spec.ts`, `sign-in.spec.ts` (back to sign-in, lockout), `account.spec.ts` (password, recovery codes) | `LoginPage.test.tsx`, `Passkeys.test.tsx`, `AccountPage.test.tsx` | `LoginFlowTest`, `AccountTest`, `PasskeysTest`, `DevTotpBypassTest`, `SecurityTest`, `CsrfCookieTest` | The development sign-in without TOTP only exists in the `dev` profile and is covered by backend tests. |
| booking-mails | `mail-import.spec.ts`, `booking-proposal.spec.ts` (Fewo & Meer confirmation and change), `owner-use.spec.ts` (owner block notice) | `Mail.test.tsx` | `MailParsersTest`, `MailLocationsTest`, `MailRecognizerTest`, `MailImportTest` | Other agencies, forwarded variants and the AI fallback run in backend tests with sample mails. |
| booking-sources | `mail-import.spec.ts` (highlighted passage), `booking-proposal.spec.ts` (proposal location) | `BookingPage.test.tsx` | `MailSourcesTest`, `BookingSourceBackfillTest`, `ReadingTest` | |
| bookings | `booking-calendar.spec.ts`, `bulk-actions.spec.ts`, `booking-proposal.spec.ts`, `sharing.spec.ts`, `owner-use.spec.ts` (owner use blocks the period) | `VacationRental.test.tsx` (incl. owner use), `BookingReview.test.tsx`, `BookingPage.test.tsx`, `BulkBookings.test.tsx` | `BookingsTest`, `BookingReviewTest`, `BookingDocumentsTest`, `BookingSchemaTest`, `OwnerUseTest`, `MailImportMigrationTest` | |
| bulk-selection | `bulk-actions.spec.ts`, `finances.spec.ts`, `inbox-bulk.spec.ts` | `DataTable.test.tsx`, `BulkBookings.test.tsx`, `BulkTransactions.test.tsx`, `BulkStatements.test.tsx`, `Mail.test.tsx` | – | Frontend only: bulk actions call the existing endpoints per entry. |
| calendar-subscription | `subscription.spec.ts` | `Subscription.test.tsx` | `CalendarLinksTest`, `OwnerUseTest` (owner use title) | |
| categories | `admin.spec.ts` (create, archive), `finances.spec.ts` (assign) | `Ledger.test.tsx` | `CategoriesTest`, `CategoryContributionTest` | |
| deployment | indirectly: every test runs against the images and nginx of `compose.yml` | – | `HealthTest`, `HealthDatabaseDownTest`, `ErrorHandlingTest`, `SecurityTest` | Security headers, startup checks and image builds are checked by the backend tests and the CI image build, not by user journeys. |
| end-to-end-tests | this suite | – | – | |
| legal-entities | `finances.spec.ts` (create owner) | `Ledger.test.tsx` | `LegalEntitiesTest` | |
| mail-inbox | `mail-import.spec.ts`, `inbox-bulk.spec.ts`, `inbox-fetch.spec.ts` (older mails via quick range, fetch result, no duplicates, quick filter „Offen“ after reload) | `Mail.test.tsx` (date field, calendar, progress, sorting, filters) | `MailInboxTest` (fetch status, one fetch at a time, parallel duplicates), `MailImportTest` | The progress bar itself is checked in `Mail.test.tsx`: in the suite a fetch of a few mails ends before it can be seen. |
| module-system | – | `AppShell.test.tsx` | `ModulesEnabledTest`, `ModulesDisabledTest`, `ModuleDataRetainedTest`, `DisabledModuleFinancesTest`, `DisabledModuleMailTest` | Not end to end: switching a module off changes the configuration of the whole stack the suite shares. |
| object-sharing | `sharing.spec.ts` (viewer, unit editor) | `Realestate.test.tsx`, `Ledger.test.tsx` | `SharingTest`, `SharingChildTypeTest`, `UnitAccessTest` | |
| occupancy | `booking-calendar.spec.ts` (calendar, month labels when scrolled on a phone), `evaluation.spec.ts` (yearly overview), `owner-use.spec.ts` (owner use bar) | `Kalender.test.tsx`, `VacationRental.test.tsx` | `OccupancyTest`, `OccupancyCalculationTest`, `OwnerUseTest` (owner-use nights) | |
| properties | `properties.spec.ts` (property with units) | `Realestate.test.tsx` | `PropertiesTest`, `RealestateTest`, `UnitReferencesTest` | |
| property-finances | `properties.spec.ts` (cost in the monthly overview) | `Finances.test.tsx` | `PropertyFinancesTest`, `PropertyCostsTest`, `StatementFinancesTest` | |
| rental-statements | `booking-calendar.spec.ts`, `bulk-actions.spec.ts`, `evaluation.spec.ts` | `VacationRental.test.tsx`, `BulkStatements.test.tsx` | `StatementsTest`, `OwnerUseTest` (owner use not settled) | |
| transactions | `finances.spec.ts` (entry, transfer, filter, bulk assign) | `Ledger.test.tsx`, `BulkTransactions.test.tsx` | `TransactionsTest`, `TransfersTest`, `TransactionListTest`, `TransactionTargetsTest` | |
| user-management | `admin.spec.ts` (create, disable, enable, reset 2FA) | `UsersPage.test.tsx` | `AdminUsersTest`, `InitialAdminRunnerTest`, `PasswordTest` | |
