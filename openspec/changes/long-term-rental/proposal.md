# Proposal

## Why

Logis knows units with the usage „Dauermiete“, but nothing can be recorded for them: no tenant, no rent, no contract. The owner has long-term rental units and wants to manage them in Logis like the vacation rentals, up to utility statements.

This change is the first of five. It lays the module's foundation, and the later changes build on it:
1. tenancies (this change);
2. rent payments and arrears;
3. rent adjustments;
4. utility statements;
5. reminders and handover records.

## What Changes

- **A new domain module „Dauervermietung“** (`longtermrental`). It can be switched on and off like vacation rental, and it has its own database schema and its own navigation.
- **Tenancies** for units with the usage „Dauermiete“. A tenancy has:
  - one or more tenants with contact data;
  - a start and an optional end;
  - a deposit;
  - the contract and other files as documents.
- **Rent as a history.** Each entry is valid from a date and holds the cold rent, the advance payment for utilities and optional further items such as a parking space. A new entry records a rent change without losing the old one.
- **Views:**
  - a list of tenancies (current, future and ended) with a page per tenancy;
  - an overview per unit that shows the current tenant or the vacancy.
- **Rules:**
  - at most one tenancy per unit at any time;
  - rights come from the unit, as everywhere else;
  - tenant data can be deleted after the tenancy has ended;
  - a property with tenancies cannot be deleted.
- **Prepared for commercial tenancies:** a tenancy has a kind (only „Wohnen“ for now), and amounts are stored net with a tax rate (0 % for now). Commercial tenancies can follow without restructuring data.

**Not in this change:** payments, automatic rent adjustments, utility statements, reminders.

## Capabilities

### New Capabilities
- `tenancies`: long-term tenancies of units. It covers tenants, period, rent history, deposit, documents, visibility and vacancy.

### Modified Capabilities

## Impact

- **Backend:**
  - a new module package `app.logis.modules.longtermrental` with its own Flyway location `db/migration/longtermrental` and the schema `longtermrental` (added to the Flyway configuration);
  - new tables only, so no existing data changes;
  - it uses the core's public API: unit access, documents and the unit references veto.
- **Frontend:** a new module `modules/longtermrental`, registered in the module registry, with its pages under `/dauervermietung`.
- **Tests:**
  - backend tests, including the module switched off;
  - frontend tests;
  - a new e2e journey in `e2e/tests` with a row in `e2e/COVERAGE.md`.
- **Docs:** a README section and the module switch in the settings table.
