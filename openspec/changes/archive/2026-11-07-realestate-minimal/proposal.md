# Proposal

## Why

The next goal is managing vacation rental bookings. A booking needs something to be booked: a holiday home or apartment. Following the roadmap, this change creates a lean real estate core first, so that `vacation-rental` and later long-term rentals and utility cost statements all build on the same properties instead of each module inventing its own. It also lets transactions be attributed to a property or unit, which is the basis for per-property figures and Anlage V.

Guiding decisions from exploration:

- **Real estate lives in the core** (`core.realestate`), like accounts, and cannot be switched off. Modules such as `vacationrental` depend only on the core; there is no module-to-module dependency.
- **Property and unit:** A property (e.g. a house) belongs to exactly one legal entity and has at least one unit (e.g. an apartment). A single-unit property such as a holiday home gets its unit automatically, and the interface hides the unit level for it.
- **Sharing on two levels:** Properties are shareable with `OWNER`, `EDITOR` and `VIEWER`. Individual units are shareable too, but only with `VIEWER` and `EDITOR`, managed by the property's owners. The effective role on a unit is the higher of the property role and the unit role.
- **Unit-only access is narrow:** Whoever only has a share on a unit sees the property as a frame (name, address), plus that unit, but no other units and no property-wide data.
- **Transactions can be attributed** to a property or a unit and are readable through it. Attributing requires `EDITOR` on the account and `EDITOR` on the target; changing transactions still requires rights on the account.

## What Changes

- **Properties and units:**
  - Create, edit and archive properties with name, address and legal entity. Whoever creates a property becomes its owner.
  - Create, edit and archive units with name and usage. A property created as a single-unit property gets one unit automatically.
- **Sharing:**
  - Properties use the existing sharing mechanism and dialog.
  - Units get shares with `VIEWER` or `EDITOR`, managed by property owners. The sharing mechanism learns about object types that belong to a parent object.
  - A core API returns the effective role on a unit and all units with at least a given role, for use by modules.
- **Transactions:**
  - A transaction can be attributed to at most one target: a property or a unit. Transfers cannot be attributed.
  - Users who see the target but not the account can read the attributed transactions, without account name, balance or note.
  - The transaction list can be filtered by property or unit.
- **Interface:**
  - New sidebar group „Immobilien“ with a property list and a property detail page (units, shares, attributed transactions).
  - The transaction dialog gets an optional field for the property or unit.

## Capabilities

### New Capabilities

- `properties`: Properties and units, their ownership by a legal entity, the automatic unit of single-unit properties, archiving and visibility for property and unit shares.

### Modified Capabilities

- `object-sharing`: Shares on units as child objects of a property, limited to `VIEWER` and `EDITOR`, managed by the parent's owners; effective role as the maximum of both levels.
- `transactions`: Attributing transactions to a property or unit, reading them through the target, and filtering the list by property or unit.

## Impact

- **Backend:** New package `app.logis.core.realestate` (properties, units, shareable types, API for modules). Changes in `core.sharing` (child object types, unit roles) and `core.ledger` (target columns on transactions, extended list query and visibility). New Flyway migration in the schema `core`.
- **Frontend:** New pages under `src/core/realestate/`, a new sidebar group, the existing `ShareDialog` reused for properties and units, and an extended `TransactionDialog`, `TransactionTable` and transaction filter bar.
- **Modules:** `vacationrental` stays a placeholder in this change; it will reference units through the new core API in `vacation-rental`.
- **Assumptions** (proposed during exploration, not explicitly confirmed; easy to change):
  - A property's legal entity cannot be changed afterwards, like an account's.
  - Master data stays minimal: property name and address; unit name and usage (vacation rental, long-term rental, own use, other). Living space, purchase data and similar fields come later.
  - Units are archived, not deleted, once anything refers to them.
  - A transaction has at most one target; splitting across several targets stays out of scope.
  - Attributed transactions are visible through the target without the account name, the balance or the note; the text search does not search the note for these rows.
