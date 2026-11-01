# Proposal

## Why

Logis has a working scaffold of sign-in, users and modules, but no domain data yet. All later modules build on a shared financial core: vacation rental, real estate, investments, bank import and reports. This change creates that core, and keeps it lean. There are owners (legal entities), accounts, categories and manually entered transactions, plus the **per-object sharing model** that protects every domain query from now on.

Guiding decisions from exploration:

- **Roadmap order:** `core-ledger` (this change), then `realestate-minimal`, then `vacation-rental` with manual entry, then `core-bank-import`. The import is not needed for manual vacation rental and gets better later, once there are bookings it can match payments to.
- **Legal entities:** There are one's own private finances and, alongside them, owners that must be kept separate for tax purposes (e.g. a GbR). Every account belongs to exactly one legal entity. Every user automatically has their own private legal entity.
- **Legal entities grant no rights:** Whoever creates a legal entity manages it. Others only see its name, and only on the accounts shared with them.
- **Sharing exists only per object.** The first shareable object is the account, with the roles `OWNER`, `EDITOR` and `VIEWER`. Transactions inherit the account's rights. Only existing users are invited. The system role `ADMIN` grants no access to domain data.
- **No double-entry bookkeeping:** An amount is positive (income) or negative (expense), in EUR.

## What Changes

- **Sharing mechanism in the core:** generic, for any object type. Modules can later use it for their own objects, such as holiday apartments.
  - Owners invite existing users as `VIEWER` or `EDITOR`, change roles and revoke shares.
  - There is always at least one owner.
  - A user search for inviting returns only username and display name.
- **Legal entities:**
  - One private legal entity per user, created automatically.
  - Further legal entities can be created and renamed (name, type, tax number).
- **Accounts:** create, edit and archive.
  - An account belongs to a legal entity and has a type, IBAN, opening balance and a running balance.
  - Whoever creates an account becomes its owner.
- **Transactions:**
  - Enter, edit and delete manually, with date, amount, category, counterparty, reference and note.
  - Filter by account, period, category and text, with totals for income and expenses.
- **Transfers between own accounts:** two linked entries that change the balances but do not count as income or expense.
- **Categories:**
  - One shared two-level category tree, split into income and expenses, with German default categories.
  - Admins maintain it.
  - Modules can contribute their own categories later.
- **Interface:**
  - New sidebar group „Finanzen“ with accounts, transactions and owners.
  - Under „Einstellungen“ the new entry „Kategorien“ for admins.
  - A sharing dialog on the account.

## Capabilities

### New Capabilities

- `object-sharing`: Sharing individual objects with existing users with the roles `OWNER`, `EDITOR` and `VIEWER`, plus the user search for inviting.
- `legal-entities`: Legal entities (owners) for tax separation, with one private legal entity per user.
- `accounts`: Accounts belonging to a legal entity, with balance and archiving.
- `transactions`: Manual transactions and transfers, filters and totals.
- `categories`: Shared category tree maintained by admins.

### Modified Capabilities

None. The new menu entries satisfy the existing requirements of `app-shell` without changing them.

## Impact

- **Backend:** New packages `app.logis.core.sharing` (with a public API for modules) and `app.logis.core.ledger` (legal entities, accounts, categories, transactions). New Flyway migrations in the schema `core`.
- **Frontend:** New pages under `src/core/ledger/` and `src/core/admin/categories/`, a sharing dialog as a reusable component and a new sidebar group.
- **Existing users:** A migration creates a private legal entity for them retroactively.
- **Assumptions** (proposed during exploration, not explicitly confirmed; easy to change):
  - One shared category tree for everyone instead of categories per legal entity.
  - EUR only. An account still has a currency field.
  - Transactions can be freely edited and deleted. Closed periods and an audit log come later.
  - Receipts and attachments are not part of this change.
