# Design

## Context

`core-ledger` is in place. Its classes in `core.ledger` are package-private; only the enums are public. `core.sharing` is the generic sharing mechanism: one table `core.object_shares` keyed by `(object_type, object_id, user_id)`, a `ShareableType` bean per type (today only `account`), `SharingService` with `grantOwner`, `requireRole`, `rolesFor`/`accessibleIds`, `invite`, `changeRole`, `revoke`, and the generic `SharesController` under `/api/shares/{type}/{id}`. It knows only flat objects: every type can have owners, and managing shares requires `OWNER` on the object itself.

The transaction list builds a JPA Specification that always starts with `account_id IN (accessibleIds)`; `TransactionViews` returns account name and note for every row. In the frontend, `ShareDialog` is generic over `(objectType, objectId, canManage)`, and core areas add their navigation directly in `AppSidebar` and `routes.tsx`.

Motivation and the decisions from exploration are in proposal.md, the requirements in `specs/`.

## Goals / Non-Goals

**Goals:**
- Child-object sharing becomes a feature of `core.sharing`, not something `core.realestate` builds on the side, so modules can later register child types of their own.
- The effective role is computed in one place. Modules and `core.ledger` never combine property and unit roles themselves.
- Visibility through a target cannot leak account data: hidden fields are removed on the server, not in the UI.

**Non-Goals:**
- Bookings, guests, prices, occupancy. They come with `vacation-rental`.
- Tenants, leases, utility cost statements, Anlage V reports.
- Splitting a transaction across several targets.
- Moving a unit to another property, or changing a property's legal entity.

## Decisions

### D1: `core.realestate` as a core package with a narrow API to `core.ledger`

```
app.logis.core
  realestate/              NEW  Property, Unit, PropertyService, PropertyController,
                                PropertyShareableType ("property"), UnitShareableType ("unit"),
                                UnitAccess (public API for modules, named interface "realestate")
  ledger/                       + LegalEntities (public): requireManaged(id, user), summary(id)
                                + TransactionTargets (public interface, implemented in realestate)
  sharing/                      + child types, effective role (D2)
```

- `realestate` needs legal entities ("does the user manage it?", name for display). `core.ledger` gets a small public facade `LegalEntities` instead of opening its classes.
- `ledger` needs to check targets ("may the user attribute to unit X?", "which targets can the user see?"). It declares the interface `TransactionTargets` itself, and `realestate` implements it. Dependencies point only from `realestate` to `ledger`; there is no package cycle.
- `UnitAccess` (effective role on a unit, units with a minimum role) is exposed to modules via `@NamedInterface("realestate")`, next to `sharing` and `modules`.
- *Alternative:* `realestate` inside `ledger`. That would avoid the facades but mix two topics into one package that is already the largest in the core.

### D2: Child object types in `core.sharing`

`ShareableType` gets two optional methods:

```java
default Optional<ParentRef> parentOf(Long objectId) { return Optional.empty(); }  // ParentRef(type, id)
default Set<ShareRole> allowedRoles() { return EnumSet.allOf(ShareRole.class); }
```

`UnitShareableType` returns `property` as parent and `{VIEWER, EDITOR}` as allowed roles. `SharingService` uses them:

```
effectiveRole(type, id, user) = max( role(type, id, user), role(parent, user) )   -- parent only for child types
requireRole(...)              -> uses effectiveRole; 404 if empty, 403 if too low
manage (invite/change/revoke) -> requires OWNER on the parent for child types, else OWNER on the object
invite/changeRole             -> rejects roles outside allowedRoles() with the unit message
owner invariant               -> only for types whose allowedRoles() contain OWNER
```

- `rolesFor(type, user, min)` for a child type returns the union of direct child shares and all children of parents on which the user has at least `min`. For that, `ShareableType` gets `default Set<Long> childrenOf(String parentType, Long parentId)`; `UnitShareableType` answers it with one query on `core.units`. The data sets are small (a handful of units per property).
- `SharesController` stays generic. `GET` returns `myRole` as the effective role plus a flag `canManage`, so the `ShareDialog` no longer derives management rights from `myRole == OWNER`.
- *Alternative:* units as plain objects with their own owners. Rejected in exploration (decision (i)): management stays with the property.

### D3: Data model

```
core.properties
  id, legal_entity_id -> legal_entities, name VARCHAR(128),
  street VARCHAR(128) NULL, postal_code VARCHAR(16) NULL, city VARCHAR(128) NULL,
  multi_unit BOOLEAN, archived BOOLEAN, created_at

core.units
  id, property_id -> properties, name VARCHAR(128),
  usage (VACATION_RENTAL|LONG_TERM_RENTAL|OWN_USE|OTHER), archived BOOLEAN, sort_order INTEGER, created_at
  UNIQUE (property_id, lower(name))

core.transactions  + property_id -> properties NULL, + unit_id -> units NULL
  CHECK (property_id IS NULL OR unit_id IS NULL)
  CHECK (transfer_id IS NULL OR (property_id IS NULL AND unit_id IS NULL))
  INDEX (property_id), INDEX (unit_id)
```

- A unit's property is fixed. A unit target is stored as `unit_id` only; its property is derived through the join, so moving data between units never leaves a stale `property_id`.
- `multi_unit` only drives the interface (D6). Adding a second unit sets it to `true`; it never switches back automatically, so the unit list does not disappear after archiving.
- "At least one active unit" is checked in the service with `SELECT … FOR UPDATE` on the property's units, the same pattern as the owner invariant.
- Deleting a property deletes its units and the shares of both types in the same service call. It is refused while transactions point to the property or its units.

### D4: Transaction visibility and the list query

```
visible(tx, user) =  tx.account_id IN accounts(user, VIEWER)          -- full row
                  OR tx.unit_id     IN UnitAccess.units(user, VIEWER) -- reduced row
                  OR tx.property_id IN properties(user, VIEWER)       -- reduced row (property role only)
```

- `TransactionService.list` gets the three ID sets (accounts from `SharingService`, units and properties through `TransactionTargets`) and the Specification starts with the OR above instead of `account_id IN …`.
- New filters `propertyId` and `unitId`. `propertyId` matches `property_id = P OR unit_id IN (visible units of P)`.
- Text search: `counterparty`/`purpose` as before; `note` only combined with `account_id IN accounts(user)`, so a note never influences hits for reduced rows.
- The totals use the same Specification and therefore include attributed transactions, which is what a per-unit filter needs.
- `TransactionViews` marks each row `access = FULL | TARGET`. For `TARGET` rows it sets `accountId`, `accountName` and `note` to `null` and adds `targetName`. Single-row endpoints (`GET/PUT/DELETE /api/transactions/{id}`) keep checking the account only, so a target-only user gets 404 there.
- Attributing: `PUT /api/transactions/{id}` gets optional `propertyId`/`unitId`. The service requires `EDITOR` on the account (as today) and `EDITOR` effective role on the new target if the target changes. Only active targets are accepted as new values; unchanged archived targets are kept.

### D5: Properties API

- `GET /api/properties?archived=` returns the visible properties with `{id, name, address, legalEntity{id,name,kind}, myRole|null, multiUnit, units:[visible units with myRole]}`.
- `POST /api/properties` with `{legalEntityId, name, address, multiUnit, firstUnit?}`; single-unit creates the unit named like the property.
- `PUT/DELETE /api/properties/{id}`, `POST /api/properties/{id}/archive|restore` (OWNER).
- `POST /api/properties/{id}/units`, `PUT /api/units/{id}`, `POST /api/units/{id}/archive|restore` (EDITOR on the property).
- Unit-only users receive the property without `myRole` and only their units; property-level actions return 403, unknown or invisible properties 404.

### D6: Frontend

```
src/core/realestate/
  api.ts                               types + query hooks
  PropertiesPage.tsx       /immobilien             list with legal entity, address, units
  PropertyDetailPage.tsx   /immobilien/:id         header + actions, units (multi-unit only),
                                                   shares, attributed transactions (TransactionTable)
  PropertyDialog.tsx, UnitDialog.tsx
src/components/TargetSelect.tsx        property/unit picker for TransactionDialog and filter bar
```

- **Sidebar:** new core group „Immobilien“ with the entry „Objekte“, below „Finanzen“.
- **Single-unit properties:** the detail page hides the unit list; the share button opens the property's shares. Unit shares are offered only for `multiUnit` properties.
- **ShareDialog:** uses `canManage` from the API and the role list from the type (units: „Lesen“, „Bearbeiten“).
- **TransactionTable:** rows with `access = TARGET` show the target instead of the account and no edit actions. `TransactionDialog` and the filter bar get `TargetSelect`, which offers only active targets on which the user has `EDITOR` (dialog) or any role (filter).

### D7: Tests

- **Backend:** integration tests per spec file, with the permission matrix of the exploration (Anna OWNER, Carla unit-only EDITOR, Dieter property VIEWER + unit EDITOR): effective roles, managing unit shares, `OWNER` rejected on units, property frame for unit-only users, reduced transaction rows, note search, attribution rights, transfers not attributable, last active unit, delete refused with attributions. `ModularityTest` checks the new named interface; an ArchUnit rule as in `LedgerArchitectureTest` keeps controllers in `core.realestate` away from repositories.
- **Frontend:** component tests for the property list (unit-only frame), the detail page (single vs. multi-unit), the ShareDialog with unit roles, and a reduced row in the TransactionTable.

## Risks / Trade-offs

- [The transaction list query grows an OR over three ID sets] → The sets are small and indexed (`account_id`, `unit_id`, `property_id`). If it becomes slow, the sets can move into a subquery against `object_shares`.
- [Reduced rows leak data through filters, e.g. searching the note] → Note search is restricted per row (D4), and the hidden fields are nulled on the server. A test searches a note term as Carla and expects no hit.
- [Child types make `SharingService` more complex for every type] → The parent logic only runs for types that declare a parent; `account` behaves exactly as before, covered by the existing sharing tests staying green.
- [`multi_unit` and the real number of units can disagree] → It only controls the interface and is set to `true` whenever a second unit is added; nothing in the data model depends on it.

## Migration Plan

One new Flyway migration in the schema `core` (`V20261101.1200__core_realestate.sql`): creates `properties` and `units` and adds the nullable target columns and checks to `transactions`. Existing transactions keep `NULL` targets, so nothing changes for existing data. Rollback works through a backup taken before the update (README: Restore).
