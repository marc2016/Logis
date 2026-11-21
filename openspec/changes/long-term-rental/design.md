# Design

## Context

**Module system.** A domain module is:
- a package `app.logis.modules.<id>` with `@ApplicationModule`;
- components guarded by `@ConditionalOnLogisModule`;
- a `ModuleDescriptor` bean;
- its own schema and Flyway location, which always migrate, also while the module is disabled.

`ModularityTest` checks that a module uses only the core's named interfaces. In the frontend, a module is a `LogisModule` in `app/registry.ts`.

**Core API the module can use:**
- `UnitAccess`: effective roles on units, and unit summaries with their usage;
- `DocumentOwnerType`: documents on a module's own objects;
- `UnitReferences`: a veto against deleting a property;
- `FinanceSource`: income for the property finances, needed from the second change on.

**The existing module as a pattern.** `vacationrental` has:
- `RentalAccess`, which maps unit roles to 404 and 403;
- an exclusion constraint against overlapping bookings (`EXCLUDE USING gist … daterange`);
- guest data that can be deleted;
- pages per record with a back link.

**Units.** A unit has the usage `LONG_TERM_RENTAL` („Dauermiete“) in the core. Nothing refers to it yet.

## Goals / Non-Goals

**Goals:**
- The module skeleton and tenancies with tenants, rent history, deposit and documents.
- A data model that the later changes extend without restructuring: payments, adjustments, utility statements and commercial tenancies.

**Non-Goals:**
- Payments, arrears, reminders.
- Automatic graduated or indexed rent.
- Utility statements, meters, the living area of units.
- Value-added tax in the interface: the columns exist, the rate is always 0.
- A dashboard tile: it comes with the payments change, when there is something to act on.

## Decisions

### D1 — Module `longtermrental`, schema `longtermrental`

- **Backend:** the package `app.logis.modules.longtermrental`; the module ID `longtermrental`; the label „Dauervermietung“; the order 200, after vacation rental.
- **Database:** migrations under `db/migration/longtermrental`; the schema and the location are added to `spring.flyway` in `application.yml`.
- **Frontend:** `modules/longtermrental`, base path `dauervermietung`, with the navigation entries „Mietverhältnisse“ and „Einheiten“.

The module does not depend on `vacationrental`. Small helpers that both need (e.g. the 404/403 mapping) are duplicated, not shared: modules may only use the core.

### D2 — Tables

```
longtermrental.tenancies
  id, unit_id -> core.units, kind ('RESIDENTIAL'), start_date, end_date NULL (last day),
  deposit_amount NULL, deposit_received_on NULL, tenants_deleted BOOLEAN, version, created_at
  EXCLUDE USING gist (unit_id WITH =, daterange(start_date, end_date, '[]') WITH &&)

longtermrental.tenants
  id, tenancy_id -> tenancies ON DELETE CASCADE, position,
  name, street, postal_code, city, country, phone, email      (all NULL after deletion of tenant data)

longtermrental.rent_terms
  id, tenancy_id -> tenancies ON DELETE CASCADE, valid_from, UNIQUE (tenancy_id, valid_from)

longtermrental.rent_items
  id, rent_term_id -> rent_terms ON DELETE CASCADE, kind ('COLD_RENT' | 'UTILITIES_ADVANCE' | 'OTHER'),
  label NULL (only for OTHER), net_amount NUMERIC(12,2), tax_rate NUMERIC(5,2) DEFAULT 0, position
```

**Overlap.** `daterange(start_date, end_date, '[]')` treats the end as the last day. A `NULL` end is an open range, so a running tenancy blocks every later one. The constraint enforces „one tenancy per unit at a time“ in the database. The service checks first, to answer with the German message; the constraint catches races.

**Rent as terms and items.** One term per „valid from“ date with its items:
- later changes add terms (rent adjustments);
- utility statements read the advance payments per period;
- payments compute the monthly amount due from the term valid in that month.

**Net amount and tax rate.** Stored now and always 0 % for residential tenancies, so commercial tenancies need no data migration. The interface shows and enters plain amounts.

**`kind`.** A column with a check constraint for `RESIDENTIAL` only; a later change extends the constraint.

**Alternatives considered:**
- **Fixed columns** `cold_rent` and `utilities_advance` on the term. Simpler, but „Stellplatz“ and later commercial extras would need columns or a second structure. Rejected.
- **Tenants as shared contacts** across tenancies. Nothing needs that yet, and deleting tenant data would get harder. Rejected.

### D3 — Rights and visibility

Rights come from the unit through `UnitAccess`, as in vacation rental:
- no role → 404 („Mietverhältnis nicht gefunden“);
- `VIEWER` → read without contact data and without documents;
- `EDITOR` or `OWNER` → everything.

The API leaves the contact fields out for viewers; the frontend does not hide them itself.

Units offered for a new tenancy: units with the usage `LONG_TERM_RENTAL` on which the user has at least `EDITOR`. If a unit's usage is changed later, its tenancies stay visible and editable; only new tenancies need the usage.

### D4 — State and vacancy are derived

Nothing is stored: the state follows from the dates and today.
- „künftig“: the start lies after today;
- „laufend“: the start is today or earlier, and there is no end or the end is today or later;
- „beendet“: the end lies before today.

The vacancy of a unit is the day after the end of its last ended tenancy, if no tenancy is running. A future tenancy does not end the vacancy; the page shows it as „ab TT.MM.JJJJ vermietet“ next to the vacancy.

### D5 — API

Under `/api/longtermrental`:
- `GET /units`: the visible long-term units with the current tenancy summary or the vacancy;
- `GET /tenancies?propertyId&unitId&state`: the list;
- `POST /tenancies`: create with tenants and the first rent term;
- `GET`, `PUT`, `DELETE /tenancies/{id}`: one tenancy; `PUT` carries dates, deposit and tenants, with optimistic locking through `version`;
- `POST`, `PUT`, `DELETE /tenancies/{id}/rent-terms[/{termId}]`: the rent history;
- `DELETE /tenancies/{id}/tenant-data`: delete the tenant data, only after the end.

Documents use the generic document endpoints with the owner type `longtermrental.tenancy` (`DocumentOwnerType`: read and write from `EDITOR`).

`UnitReferences` returns „Das Objekt hat noch Mietverhältnisse“ while a tenancy refers to one of the units.

### D6 — Frontend

**„Mietverhältnisse“:**
- a `DataTable` (`tableId` `longtermrental.tenancies`) with filters in the address;
- the columns Einheit, Mieter, Zeitraum, aktuelle Miete and Status;
- the button „Mietverhältnis“, which opens a dialog for a new one.

**Tenancy page** `/dauervermietung/mietverhaeltnisse/:id`:
- a header with tenants, unit, period, current rent and state;
- sections for the data, the tenants, the rent history (a table with „Miete ändern“), the deposit and the documents;
- the actions „Beenden“, „Mieterdaten löschen“ (after the end) and „Löschen“.

**„Einheiten“:** cards or rows per unit with the tenant and rent, or „Leerstand seit …“. Each links to the tenancy, or to „Mietverhältnis“ with the unit preselected.

Dates use `DateField`, amounts use the existing money input helpers. All UI text is German.

## Risks / Trade-offs

- **The exclusion constraint needs `btree_gist`.** → The vacation rental migration already enables the extension. The new migration uses `CREATE EXTENSION IF NOT EXISTS` to be independent of the order.
- **A tenancy recorded with a wrong unit.** → The unit of a tenancy can be changed while the new unit is free in that period. Later changes (payments) may restrict this.
- **A unit loses the usage „Dauermiete“.** → Its tenancies stay; see D3.
- **Tenant data is personal data.** → Viewers get no contact data, and deleting it after the end is built in. Test data uses invented names.

## Migration Plan

- Only new tables in a new schema; no existing data is touched.
- Deployments get the module enabled by default, as modules without an explicit setting are. The README names the setting to switch it off.
- Rollback: disable the module; its tables stay unused.
