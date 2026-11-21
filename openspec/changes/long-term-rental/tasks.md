# Tasks

## 1. Module skeleton

- [ ] 1.1 Create the backend module `longtermrental` (design D1):
  - `package-info.java`, the configuration with the `ModuleDescriptor`, and the access helper on `UnitAccess`;
  - the schema and the migration location in `application.yml`;
  - a first migration `longtermrental/V<yyyyMMdd>.<HHmm>__longtermrental_tenancies.sql` with the tables of design D2.

  Verify:
  - `ModularityTest` passes with the new module;
  - `/api/modules` lists it;
  - with the module disabled, its API answers 404 and its data stays (tests next to `ModulesDisabledTest` and `ModuleDataRetainedTest`);
  - a migration test shows that existing data is unchanged.
- [ ] 1.2 Create the frontend module `modules/longtermrental` with its navigation and routes, registered in `app/registry.ts`. Verify with `AppShell` tests: the entries show only while the module is active.

## 2. Tenancies in the backend

- [ ] 2.1 Create, read, change, end and delete tenancies with tenants, deposit and the first rent term (design D2, D3, D5). Verify with `TenanciesTest`:
  - create and read;
  - a unit with another usage, end before start, and an overlap are rejected with their messages;
  - a following tenancy from the day after the end works;
  - optimistic locking;
  - delete removes tenants and rent terms.
- [ ] 2.2 Rights and visibility (design D3). Verify with tests:
  - no role → 404;
  - `VIEWER` reads without contact data and cannot write (403);
  - a unit-only `EDITOR` sees only that unit's tenancies.
- [ ] 2.3 Rent history: add, change and delete terms with their items (design D2, D5). Verify with tests:
  - the current rent by date, using the test clock;
  - the total with further items;
  - a duplicate date is rejected;
  - the first term cannot be deleted.
- [ ] 2.4 Tenant data deletion, the document owner type and the property deletion veto (design D5). Verify with tests:
  - deletion only after the end, and afterwards names and contact data are gone;
  - documents can be attached by an `EDITOR` and are hidden from a `VIEWER`;
  - deleting a property with a tenancy is rejected with „Das Objekt hat noch Mietverhältnisse“.
- [ ] 2.5 The units overview with state and vacancy (design D4). Verify with tests using the test clock:
  - running, future and ended;
  - vacancy since the day after the end;
  - never rented;
  - a future tenancy next to a vacancy.

## 3. Tenancies in the frontend

- [ ] 3.1 The page „Mietverhältnisse“ with filters, the state and the dialog for a new tenancy (design D6). Verify with frontend tests:
  - the list with the default filter;
  - the filter in the address;
  - creating a tenancy with two tenants and a rent;
  - the error messages of the backend are shown.
- [ ] 3.2 The tenancy page with the header, tenants, rent history („Miete ändern“), deposit, documents and actions (design D6). Verify with frontend tests:
  - a rent change adds a history row and changes the current rent;
  - ending a tenancy;
  - „Mieterdaten löschen“ only after the end;
  - a viewer sees no contact data and no edit controls;
  - „Mietverhältnis nicht gefunden“.
- [ ] 3.3 The page „Einheiten“ with the tenant or the vacancy (design D6). Verify with frontend tests: a rented unit, „Leerstand seit …“, and the link to a new tenancy with the unit preselected.

## 4. End-to-end, documentation and checks

- [ ] 4.1 Add `e2e/tests/tenancies.spec.ts`:
  - create a long-term unit;
  - record a tenancy, change the rent and attach a contract;
  - end it and see the vacancy.

  Add a helper for long-term units to `e2e/support/data.ts`, a row for `tenancies` to `e2e/COVERAGE.md`, and the tenancy page and the list to `layout.spec.ts`. Verify: these specs, `properties` and `smoke` are green.
- [ ] 4.2 Add a README section „Long-term rental“ and the module to the modules setting description. Run `cd backend && ./mvnw test` and `cd frontend && npm test && npm run lint && npm run build`; all must be green.
- [ ] 4.3 Check that tests, specs and docs contain invented tenant names and addresses only.

## Workflow follow-up

- Archive with `/opsx:archive`; this creates the `tenancies` spec.
- Next changes: `rent-payments`, `rent-adjustments`, `utility-statements`, `tenancy-extras`.
