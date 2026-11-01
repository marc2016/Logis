# Design

## Context

The scaffold from `project-scaffold` is in place. Spring Modulith has the modules `core`, `shared` and `vacationrental`. `core` may only use `shared` and exposes the named interface `modules` to domain modules. Sign-in and roles go through the `SessionAuthenticationFilter`. In controllers the signed-in user is available as `LogisPrincipal(userId, username)`. Errors are returned via `ApiException` as a `ProblemDetail` with a German message. The database has one schema per area, and migrations live under `db/migration/<area>` with timestamp versions.

Users are created in two places: in `UserAdminService.create` and in `InitialAdminRunner`. In the frontend, core areas have so far hard-coded their navigation in `AppSidebar`, while modules go through the registry. Forms use `FormError` and `PageHeader`.

Motivation and guiding decisions are in proposal.md, the requirements in `specs/`.

## Goals / Non-Goals

**Goals:**
- A sharing mechanism that domain modules reuse without copying it. The first module to use it is vacation rental with its holiday apartments.
- Every domain query filters by shares by construction. A forgotten filter must not lead to a data leak.
- Money amounts are exact from the database to the display, without floating point.

**Non-Goals:**
- Import, duplicate detection, matching with bank transactions. That comes with `core-bank-import`.
- Receipts and attachments, reports, budgets, shares of amounts or splits.
- Closed periods and an audit log.
- Currencies other than EUR.
- Categories that individual users create for themselves.
- Key figures on the dashboard. The „Übersicht“ stays a placeholder for now.

## Decisions

### D1: Packages and module boundaries

```
app.logis.core
  sharing/              sharing (named interface "sharing" for modules)
    ObjectShare, ShareRole, SharingService, ShareableType, SharesController
  ledger/
    LegalEntity..., Account..., Category..., Transaction..., Transfer...
  users/
    UserDirectoryController (user search), UserCreatedEvent
```

- `core.sharing` is exposed to modules via `@NamedInterface("sharing")`. `core.ledger` stays internal. Modules only get a ledger API once they need one, for example for matching payments in vacation rental.
- *Alternative:* a separate Modulith module `ledger` next to `core`. That would be a cleaner separation, but legal entities and private legal entities are closely tied to users, and the roadmap deliberately calls this topic "core". Extracting it later is possible, because `ledger` only accesses the rest of the core through the `users` and `sharing` APIs.

### D2: Shares as a generic table

```
core.object_shares
  id, object_type VARCHAR(64), object_id BIGINT, user_id -> users,
  role (OWNER|EDITOR|VIEWER), created_at
  UNIQUE (object_type, object_id, user_id)
  INDEX (user_id, object_type)
```

- The `object_type` is a stable string such as `"account"` and later `"vacationrental.property"`. Each type is registered as a `ShareableType` bean. It provides the type ID, display name and the check "does the object exist?". This lets the generic `SharesController` (`/api/shares/{type}/{id}`) work for all types.
- `SharingService` provides:
  - `grantOwner`
  - `requireRole(type, id, userId, minRole)`: returns 404 if there is no share and 403 if the role is too low
  - `accessibleIds(type, userId, minRole)`
  - `invite`, `changeRole`, `revoke` and `list`
- The invariant "at least one owner" is checked in a transaction with `SELECT … FOR UPDATE` on the object's owner rows. This is the same pattern as for "at least one active admin".
- **Queries filter by construction:** Services for shareable objects first fetch the shared IDs (`SharingService.rolesFor` or `accessibleIds`) and then query only those IDs. Single-object access always goes through `requireRole`.
  - *Implemented instead of the originally planned JPQL subselect:* The role per object is needed for display anyway, and the data sets are small. An ArchUnit test in the style of `ModularityTest` ensures that controllers in `core.ledger` do not call `JpaRepository.findById` directly, only the service methods with permission checks.
- When an object is deleted, its shares are deleted in the same service call. There is no foreign key because `object_id` is generic.
- *Alternative:* one share table per object type (`account_shares` etc.). That gives real foreign keys, but every module would have to rebuild the logic for inviting, owner protection and the UI. The generic table fits the decision "one sharing model for everything".

### D3: User search

`GET /api/users/search?q=` returns at most 10 active users as `{id, username, displayName}` for every signed-in user. It searches username and display name with `ILIKE`. If `q` is shorter than 2 characters, an empty list is returned. This is deliberately the only place where non-admins see other users.

### D4: Legal entities and the private legal entity

```
core.legal_entities
  id, name VARCHAR(128), kind (PRIVATE_PERSON|GBR|GMBH|OTHER), tax_number VARCHAR(32) NULL,
  manager_user_id -> users, personal BOOLEAN, created_at
  UNIQUE (manager_user_id, lower(name))
  UNIQUE (manager_user_id) WHERE personal   -- exactly one private entity per user
```

- Legal entities are **not** a shareable object. They are visible through `manager_user_id = me`. Other people's legal entities only appear as `{id, name, kind}` embedded in the account data.
- **Creating the private legal entity:** `UserAdminService.create` and `InitialAdminRunner` publish a `UserCreatedEvent`. An `@EventListener` in `core.ledger` synchronously creates `<display name> privat` in the same transaction. Existing users get theirs through a migration (`INSERT … SELECT` for all users without a private legal entity).
- The name of the private legal entity is independent of the display name afterwards and can be renamed.

### D5: Accounts

```
core.accounts
  id, legal_entity_id -> legal_entities, name VARCHAR(128),
  kind (CHECKING|SAVINGS|CASH|CREDIT_CARD|OTHER), currency CHAR(3) DEFAULT 'EUR' CHECK = 'EUR',
  iban VARCHAR(34) NULL, opening_balance NUMERIC(14,2) DEFAULT 0, archived BOOLEAN, created_at
```

- An account may only be created in one's own legal entity (`manager_user_id = me`, otherwise 404). This runs `grantOwner("account", id, me)`.
- The **balance** is not stored but calculated by query: `opening_balance + COALESCE(SUM(amount), 0)`. In the account list this happens in a single grouped query. Stored balances would drift apart on edits, deletions and transfers, and the data sets are small.
- **IBAN:** Spaces are removed, upper case is enforced, then length 15–34 and mod-97 = 1 are checked. We do not maintain a list of countries.

### D6: Transactions and transfers

```
core.transactions
  id, account_id -> accounts, booking_date DATE, amount NUMERIC(14,2) CHECK <> 0,
  category_id -> categories NULL, counterparty VARCHAR(255), purpose VARCHAR(500), note TEXT,
  transfer_id UUID NULL, version INTEGER,
  created_by -> users, created_at, updated_by -> users, updated_at
  INDEX (account_id, booking_date DESC), INDEX (transfer_id), INDEX (category_id)
  CHECK (transfer_id IS NULL OR category_id IS NULL)
```

- **Optimistic locking** via `@Version`: The client sends `version` along, and on a mismatch the system responds with 409 and the message „Die Transaktion wurde inzwischen geändert“.
- **Transfers** are two rows with the same `transfer_id`. Dedicated endpoints `POST/PUT/DELETE /api/transfers/{transferId}` always change both sides in one transaction. The regular transaction endpoints reject changes to transfer rows with a reference to the transfer; deleting is the only exception and then deletes both sides.
- **List:** `GET /api/transactions?accountId&from&to&categoryId&uncategorized&q&page&size` returns `{items, page, totalItems, income, expense}`.
  - Filtering uses a JPA Specification that always starts with `account_id IN (accessibleIds)`.
  - `categoryId` includes the subcategories.
  - The totals come from a second query with the same Specification: `SUM(CASE WHEN amount > 0 …)`, only over rows with `transfer_id IS NULL`.
- Editing requires `EDITOR` on the previous and on the new account, and so does a transfer on the source and target account.

### D7: Money amounts in the API

- In the backend, amounts are `BigDecimal` with scale 2 throughout. Input with more than 2 decimal places is rejected (400), not rounded.
- **In JSON, amounts are strings** (`"-84.90"`), so that JavaScript never computes with floating point. This is implemented via `@JsonFormat(shape = STRING)` on the DTO fields.
- The frontend has `lib/money.ts`:
  - `formatEUR("-84.90")` returns "-84,90 €" (via `Intl.NumberFormat("de-DE")`).
  - `parseAmount("1.234,56")` returns `"1234.56"` and works purely on strings.
  - The frontend does no arithmetic. Balances and totals come from the server.

### D8: Categories

```
core.categories
  id, parent_id -> categories NULL, kind (INCOME|EXPENSE), name VARCHAR(64),
  module_key VARCHAR(128) NULL UNIQUE,   -- e.g. "vacationrental.cleaning"
  module_id VARCHAR(64) NULL, archived BOOLEAN, sort_order INTEGER
  UNIQUE (kind, COALESCE(parent_id, 0), lower(name))
```

- The second level is checked in the service: the `parent` may not have a `parent` itself, and the kind is taken from the main category.
- The **default categories** come from a Flyway seed (spec `categories`).
- **Module categories:** Modules register `CategoryContribution` beans with `moduleKey`, kind, name and optionally the `moduleKey` of the main category. A startup run (`ApplicationRunner`) creates missing categories based on `module_key` and renames nothing. Admins can only archive these categories. The selection API hides categories of disabled modules, because their `module_id` is missing from the `ModuleDescriptor` beans. The interface lives in the named interface `modules`. In this change only a test bean uses it.
- The admin endpoints live under `/api/admin/categories`. For selection when entering transactions there is `GET /api/categories`.

### D9: Frontend

```
src/core/ledger/
  money.ts (under lib/), api.ts (types + query hooks)
  accounts/AccountsPage.tsx          /finanzen/konten       list by legal entity, balance, role
  accounts/AccountDetailPage.tsx     /finanzen/konten/:id   header with balance + actions, transaction list
  accounts/AccountDialog.tsx         create/edit
  transactions/TransactionsPage.tsx  /finanzen/transaktionen   all accounts, filter bar, totals
  transactions/TransactionTable.tsx  (reused in account detail)
  transactions/TransactionDialog.tsx income/expense toggle, amount, date, category, ...
  transactions/TransferDialog.tsx
  legal-entities/LegalEntitiesPage.tsx /finanzen/eigentuemer
src/core/admin/categories/CategoriesPage.tsx  /einstellungen/kategorien
src/components/ShareDialog.tsx       generic: (objectType, objectId, canManage)
src/components/CategorySelect.tsx    grouped by main/subcategory
```

- **Sidebar:** Below „Allgemein“ comes the new core group „Finanzen“ with accounts, transactions and owners. „Einstellungen“ gets the entry „Kategorien“ for admins.
- **Date:** native `<input type="date">`. It is accessible without an extra dependency and good enough on mobile devices.
- **User picker in the ShareDialog:** shadcn `Command` in a `Popover` as a combobox, fed from `/api/users/search`.
- New shadcn components: `popover`, `command`, `textarea`, `tabs`, `toggle-group`, `checkbox`.
- After changes, TanStack Query invalidates `['accounts']`, `['transactions', filter]` and `['shares', type, id]` specifically.

### D10: Tests

- **Backend:** Integration tests per spec file.
  - Focus: permissions and data leaks. This includes a test "other user → 404" and "VIEWER → 403" per endpoint.
  - Further focus: exactness of balances and totals, transfers, optimistic locking, owner invariant, migration of private legal entities.
  - Unit tests for IBAN and amount validation. The Modulith test additionally checks the named interface `sharing`.
- **Frontend:**
  - Vitest for `money.ts` (formatting and parsing, edge cases).
  - Component tests for TransactionDialog (sign, error messages), ShareDialog (inviting, last owner) and the account list (grouping).

## Risks / Trade-offs

- [Generic share table without a foreign key to the object] → Orphaned shares are possible if an object is deleted bypassing the service. Countermeasures: deleting only through services, and a test that finds no shares without an object after every deletion case.
- [Balance by query instead of stored] → This is slower with many thousands of transactions per account. The index `(account_id, booking_date)` and small data sets make it uncritical. A materialized balance can come later without an API change.
- [Disabled sole owner] → The account can then no longer be managed by anyone. The fix is to re-enable the user. There is no admin tool for transferring yet; the README describes the SQL route.
- [One shared category tree] → Users cannot create their own subcategories and must ask an admin. This is a deliberate assumption (see proposal.md). It can be extended later.
- [Amounts as JSON strings] → Unusual for API consumers, but it prevents rounding errors. It is documented and applies to all money fields.

## Migration Plan

New Flyway migrations in the schema `core`. They create the tables, seed the default categories and insert private legal entities for existing users. They run automatically on the next start. Rolling back goes through a backup taken before the update (README: Restore), because Flyway migrations are not rolled back automatically.

## Open Questions

- **Transactions assigned to an account and to someone else's object.** Proposal: whoever only has rights on the object may also read them. This only becomes relevant with `realestate-minimal`, once there are such objects, and does not change this change.
