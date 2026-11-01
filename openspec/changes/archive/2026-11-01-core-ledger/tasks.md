# Tasks

## 1. Database

- [x] 1.1 Create the migration for `core.object_shares`, `core.legal_entities`, `core.accounts`, `core.categories` and `core.transactions` according to design.md D2, D4, D5, D6 and D8, including indexes, unique constraints and checks. Verify: The backend starts with the `dev` profile, and `\d` in psql shows all tables with their constraints.
- [x] 1.2 Create the seed migration for the default categories according to the spec `categories`. Verify: An integration test confirms that after startup all required main and subcategories exist with the correct kind.
- [x] 1.3 Create the migration that gives existing users their private legal entity. Verify: An integration test first creates users without a legal entity, runs the migration (Flyway `target`) and then finds exactly one private legal entity per user.

## 2. Sharing (backend)

- [x] 2.1 Implement `ObjectShare`, `ShareRole`, `ShareableType` and `SharingService` (`grantOwner`, `requireRole`, `accessibleIds`, `invite`, `changeRole`, `revoke`, `list`) in `core.sharing` with `@NamedInterface("sharing")`. Verify: Integration tests confirm 404 without a share, 403 for a role that is too low, and the role hierarchy VIEWER < EDITOR < OWNER. `ModularityTest` stays green.
- [x] 2.2 Implement `SharesController` (`GET/POST /api/shares/{type}/{id}`, `PATCH/DELETE /api/shares/{type}/{id}/{userId}`). Verify: Integration tests cover inviting (effective immediately), disabled or unknown user („Benutzer nicht gefunden“), role change instead of a duplicate, revoking, giving up one's own share and „Es muss mindestens einen Owner geben“, and also that an `EDITOR` gets 403 when inviting.
- [x] 2.3 Implement the user search `GET /api/users/search` according to D3. Verify: Integration tests confirm at most 10 hits, no disabled users, an empty list for fewer than 2 characters, and exactly the fields `id`, `username` and `displayName`.

## 3. Legal entities (backend)

- [x] 3.1 Publish `UserCreatedEvent` in `UserAdminService.create` and `InitialAdminRunner`. In `core.ledger` a listener creates `<display name> privat`. Verify: An integration test confirms that a newly created user has exactly one private legal entity of type `PRIVATE_PERSON`.
- [x] 3.2 Implement the endpoints `/api/legal-entities`: list of one's own legal entities, create, change, delete. Verify: Integration tests cover that a duplicate name is rejected (case-insensitively), that a legal entity with accounts cannot be deleted, that the private one cannot be deleted, and that someone else's legal entity returns 404.

## 4. Accounts (backend)

- [x] 4.1 Implement IBAN validation (normalization, length, mod-97) and amount validation (scale ≤ 2, ≠ 0, maximum amount). Verify: Unit tests with valid and invalid IBANs (DE, AT, wrong check digits) and amounts.
- [x] 4.2 Implement the endpoints `/api/accounts`: list with legal entity, own role and balance in one grouped query; create only in one's own legal entity with `grantOwner`; change; archive; restore; and delete only without transactions, including deleting the shares. Verify: Integration tests cover:
  - Balance 100.00 + 0.10 + 0.20 − 50.00 = 50.30
  - `EDITOR` changes account → 403
  - Someone else's account → 404, also for admins
  - Account in someone else's legal entity → 404
  - Mixed list with someone else's legal entity as `{id, name, kind}`
  - No orphaned shares after deletion
- [x] 4.3 Add an ArchUnit rule: controllers in `core.ledger` do not access repositories directly. Verify: The test is green and fails as soon as a controller injects a repository for testing.

## 5. Categories (backend)

- [x] 5.1 Implement `GET /api/categories` (active tree, without categories of disabled modules) and the admin endpoints `/api/admin/categories`: create, rename, move, archive, restore, delete. Verify: Integration tests cover:
  - `USER` → 403
  - Third level rejected
  - Subcategory takes over the kind of the main category
  - Duplicate name rejected
  - Deleting a category in use with the message „Die Kategorie wird verwendet und kann nur archiviert werden“
  - Archive and restore
- [x] 5.2 Implement `CategoryContribution` in the named interface `modules` and the startup run according to D8. Verify: An integration test with a test bean creates a module category. A restart does not duplicate anything. Renaming or deleting results in „Diese Kategorie wird von einem Modul verwaltet“, archiving is allowed. With the module disabled, the category is missing from `GET /api/categories`.

## 6. Transactions (backend)

- [x] 6.1 Implement `POST/PUT/DELETE /api/transactions` with permission checks (EDITOR on old and new account), rejection for archived accounts, active categories only, and `@Version`. Verify: Integration tests cover:
  - Entering changes the balance
  - 0.00 and 12.345 rejected
  - `VIEWER` → 403
  - Someone else's account → 404
  - Archived account: „Das Konto ist archiviert“
  - Archived category: „Die Kategorie ist nicht verfügbar“
  - Stale `version` → 409 „Die Transaktion wurde inzwischen geändert“
  - Existing assignment stays after the category is archived
- [x] 6.2 Implement `GET /api/transactions` with Specification filters (account, period, category including subcategories, uncategorized, search text), pagination (max. 100) and totals excluding transfers. Verify: Integration tests cover that the totals are exact across all pages, that only shared accounts appear, that filtering by a main category includes the subcategories, that "uncategorized" excludes transfers, and that sorting is by date descending.
- [x] 6.3 Implement `POST/PUT/DELETE /api/transfers` according to D6. Regular endpoints reject changes to transfer rows, deleting removes both sides. Verify: Integration tests cover that both balances change and the totals stay the same, that a change affects both sides, that deleting one side deletes both, and that a target account with only `VIEWER` results in 403.

## 7. Frontend foundations

- [x] 7.1 Implement `lib/money.ts` (`formatEUR`, `parseAmount`). Verify: Vitest with "1.234,56", "-84,9", "0,005" (rejected), "abc" (rejected) and large amounts, without floating-point deviations.
- [x] 7.2 Add the shadcn components `popover`, `command`, `textarea`, `tabs`, `toggle-group` and `checkbox`. Verify: `npm run build` is green.
- [x] 7.3 Create `core/ledger/api.ts` with types (amounts as strings) and TanStack Query hooks for accounts, legal entities, categories, transactions, transfers and shares. Verify: `npm run build` is green (type check).
- [x] 7.4 Extend the sidebar with the core group „Finanzen“ (accounts, transactions, owners) and the admin entry „Kategorien“. Routes are added according to D9. Verify: Component test: „Finanzen“ is visible, and „Kategorien“ only for admins.

## 8. Frontend pages

- [x] 8.1 Implement `ShareDialog` (generic) and `CategorySelect`. Verify: Component tests cover that inviting via search creates the share, that the message appears for the last owner, and that a `VIEWER` only sees the list without actions.
- [x] 8.2 Implement the owners page (`/finanzen/eigentuemer`): list, create, rename, delete with error messages. Verify: Component tests cover creating and the message for a duplicate name.
- [x] 8.3 Implement the account list (`/finanzen/konten`) with grouping by legal entity, role, balance, „Archivierte anzeigen“ and `AccountDialog`. Verify: Component tests cover grouping, the balance format "1.250,00 €" and the error message for an invalid IBAN.
- [x] 8.4 Implement `TransactionTable`, `TransactionDialog` (income/expense toggle, amount, date, category, counterparty, reference, note) and `TransferDialog`. Verify: Component tests cover that „Ausgabe“ with 84,90 sends the value "-84.90", that the 409 message is shown, and that a transfer is marked as such in the table.
- [x] 8.5 Implement the account detail (`/finanzen/konten/:id`): header with balance, role and actions (edit, share, archive depending on the role) and the account's transaction list. Verify: Component tests confirm that a `VIEWER` sees no buttons for entering or editing, and that an unknown account shows "page not found".
- [x] 8.6 Implement the transactions page (`/finanzen/transaktionen`): filter bar (account, period, category, uncategorized, search), totals of income and expenses, pagination. Verify: A component test confirms that the filters send the right query parameters and that the totals are shown.
- [x] 8.7 Implement category management (`/einstellungen/kategorien`): tree by kind, create, rename, move, archive and restore, delete with message. Module categories are marked and can only be archived. Verify: Component tests cover creating and both error messages.

## 9. Acceptance

- [x] 9.1 Run the full test suites: `./mvnw verify` and `npm test && npm run build`. Verify: Everything is green.
- [x] 9.2 Manual run with `./start.sh` and two users (admin and Bert):
  1. Create the legal entity "GbR Ostsee", create an account in it and invite Bert as `VIEWER`.
  2. Check that Bert only sees this one GbR account and cannot enter anything.
  3. Make Bert `EDITOR` and check that they can now enter transactions.
  4. Make a transfer between own accounts.
  5. Check the monthly filter with totals.

  Verify: All steps behave as described in the specs.
- [x] 9.3 Extend the README: section "Finances and sharing" (legal entities, accounts, roles) and an SQL emergency procedure for transferring an account whose only owner is disabled. Verify: Run the SQL procedure once on the dev stack.
