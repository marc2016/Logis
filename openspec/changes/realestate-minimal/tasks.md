# Tasks

## 1. Database

- [x] 1.1 Create the migration `V20261101.1200__core_realestate.sql` with `core.properties`, `core.units` and the target columns, checks and indexes on `core.transactions` according to design.md D3. Verify: The backend starts with the `dev` profile against an existing dev database with transactions, `\d core.transactions` shows both checks, and existing rows have `NULL` targets.

## 2. Sharing with child types (backend)

- [x] 2.1 Extend `ShareableType` with `parentOf`, `allowedRoles` and `childrenOf`, and make `SharingService` compute the effective role, check management rights on the parent, reject roles outside `allowedRoles()` with „Für Einheiten gibt es nur die Rollen Lesen und Bearbeiten“, and apply the owner invariant only to types with `OWNER` (D2). Verify: The existing `SharingTest` stays green unchanged, and new tests with a test child type cover effective role = maximum, parent-owner management, `EDITOR` of the child gets 403 when inviting, and `rolesFor` including children of shared parents.
- [x] 2.2 Return `myRole` as effective role and `canManage` from `SharesController`. Verify: An integration test checks `canManage` for an account owner, a property owner on a unit and a unit editor.

## 3. Properties and units (backend)

- [x] 3.1 Add the public facade `LegalEntities` in `core.ledger` (`requireManaged`, `summary`). Verify: `ModularityTest` stays green, and a test confirms 404 for someone else's legal entity.
- [x] 3.2 Implement `Property`, `Unit`, the shareable types `property` and `unit`, and `PropertyService` with create (single-unit creates its unit automatically, `grantOwner`), edit, archive/restore, delete (refused with attributions, removes units and shares), and unit add/edit/archive/restore with the "at least one active unit" check (D3). Verify: Integration tests cover every scenario of `specs/properties`, including „Ein Objekt braucht mindestens eine aktive Einheit“, „Eine Einheit mit diesem Namen existiert bereits“ and the delete message.
- [x] 3.3 Implement `PropertyController` and the unit endpoints according to D5, including the reduced view for unit-only users. Verify: Integration tests with Anna (OWNER), Carla (unit-only EDITOR) and Dieter (property VIEWER + unit EDITOR) check list contents, 404 without a share, 403 for property actions as `EDITOR` or unit-only user.
- [x] 3.4 Expose `UnitAccess` (effective role on a unit, units with a minimum role) via `@NamedInterface("realestate")` and add an ArchUnit rule that controllers in `core.realestate` do not use repositories. Verify: `ModularityTest` and the new architecture test are green; the rule fails when a controller injects a repository for testing.

## 4. Transactions with targets (backend)

- [x] 4.1 Declare `TransactionTargets` in `core.ledger` and implement it in `core.realestate`. Extend `PUT /api/transactions/{id}` (and creation) with `propertyId`/`unitId`: `EDITOR` on the account and on the new target, only active targets as new values, transfers rejected with „Umbuchungen können keinem Objekt zugeordnet werden“ (D4). Verify: Integration tests cover attributing, Dieter's 403 as property `VIEWER`, the transfer message, keeping an archived target unchanged, and the DB check against both target columns.
- [x] 4.2 Extend the list query with visibility through units and properties, the filters `propertyId` and `unitId`, and the per-row note search (D4). Verify: Integration tests cover "Filter by unit" for Carla with exact totals, property-wide transactions hidden from Carla, a note term not found by Carla, and Anna's results unchanged from before.
- [x] 4.3 Return reduced rows (`access = TARGET`, no account ID, account name or note, with `targetName`) from `TransactionViews`, and keep single-row endpoints account-only. Verify: Integration tests assert the JSON of a reduced row and that Carla gets 404 on `GET/PUT/DELETE /api/transactions/{id}`.

## 5. Frontend

- [x] 5.1 Create `core/realestate/api.ts` with types and query hooks, and adapt `ShareDialog` to `canManage` and per-type roles. Verify: `npm run build` is green, and a component test shows only „Lesen“ and „Bearbeiten“ for a unit and no actions for a unit editor.
- [x] 5.2 Add the sidebar group „Immobilien“ with „Objekte“ and the routes `/immobilien` and `/immobilien/:id`. Verify: A component test shows the group for every signed-in user, and an unknown property shows „Seite nicht gefunden“.
- [x] 5.3 Implement `PropertiesPage`, `PropertyDialog` and `UnitDialog` (single-unit vs. several units, first unit). Verify: Component tests cover creating a single-unit property, the duplicate unit name message, and a unit-only user seeing the property frame with one unit.
- [x] 5.4 Implement `PropertyDetailPage`: header with actions by role, unit list only for multi-unit properties, shares for property and units, attributed transactions via `TransactionTable`. Verify: Component tests cover the holiday home page without a unit list and a `VIEWER` without edit buttons.
- [x] 5.5 Add `TargetSelect` to `TransactionDialog` and the transaction filter bar, and show reduced rows in `TransactionTable` with the target instead of the account and without actions. Verify: Component tests check that the dialog sends `unitId`, the filter sends `unitId`/`propertyId`, and a reduced row has no edit action.

## 6. Acceptance

- [x] 6.1 Run the full test suites: `./mvnw verify` and `npm test && npm run lint && npm run build`. Verify: Everything is green.
- [ ] 6.2 Manual run with `./start.sh` and three users (admin as Anna, Carla, Dieter):
  1. Create the single-unit property "Ferienhaus Ostsee" and check that no unit list appears.
  2. Create a property with two units and share one unit with Carla as `EDITOR`, the property with Dieter as `VIEWER`.
  3. Attribute two transactions to Carla's unit and one to the property.
  4. As Carla, check that only the property frame, the one unit and its two transactions are visible, without account name or note.

  Verify: All steps behave as described in the specs.
- [x] 6.3 Extend the README section "Finances and sharing" with properties, units and unit shares. Verify: The section describes the roles as in `specs/object-sharing`.
