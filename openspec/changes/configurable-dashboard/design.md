# Design

## Context

- **Start page:** `/` renders `core/dashboard/DashboardPage`, a placeholder.
- **Data:** every figure the planned tiles need already has an API and a React Query hook, and each goes through the sharing model. The hooks are:
  - `useInbox` (all inbox items of the user);
  - `useBookings({from, to, unitId, review})`;
  - `useOverview(unitId, year)` and `useUnitsOverview(year)`;
  - `useAccounts` (with `balance`);
  - `useTransactions({accountId, size})`;
  - `useRentalUnits`.
- **List filters in the address:**
  - the inbox accepts `?ergebnis=<code>`;
  - the bookings list accepts `?review=true`.
- **Modules:** frontend modules fulfil `LogisModule` (`app/types.ts`); `useActiveModules` tells which are active. Modules already contribute inbox labels and actions this way.
- **Settings storage:** there is no per-user settings store yet.

## Goals / Non-Goals

**Goals:**
- A tile framework with per-user arrangement and settings stored in the backend.
- The six tiles of the proposal, with module tiles registered by the module.
- Tiles reuse the existing hooks; no new data APIs.

**Non-Goals:**
- A free grid or resizable tiles.
- Shared or admin-defined dashboards.
- New aggregate endpoints. If a tile turns out slow with large data, it gets one later.

## Decisions

### D1 — Arrangement stored as JSON per user in `core`

Migration `V<date>.<time>__core_dashboard.sql` creates:

```
core.dashboards (
  user_id    BIGINT PRIMARY KEY REFERENCES core.users (id) ON DELETE CASCADE,
  tiles      JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL
)
```

No row means the user never customised, so the frontend shows the default. No data migration is needed.

API in `core` (new package `core/dashboard`):
- `GET /api/dashboard` returns `{ tiles: [...] }`, or `{ tiles: null }` when not customised.
- `PUT /api/dashboard` with `{ tiles: [...] }` saves the arrangement; `tiles: null` deletes the row and resets to the default.

Each user reads and writes only their own row; the user ID comes from the session. A tile is `{ id: string, type: string, settings: object }`.

The backend validates the shape only:
- at most 40 tiles;
- `id` of at most 36 characters, unique within the arrangement;
- `type` matching `^[a-z]+(\.[a-z0-9-]+)+$`, e.g. `core.todo` or `vacationrental.occupancy`;
- `settings` a JSON object of at most 2 KB.

It does not know the tile catalog: tiles belong to frontend modules. Unknown types are stored as they are, so that tiles of an inactive module survive (spec „Settings that are no longer valid“).

**Alternatives considered:**
- **Browser storage.** Rejected: the arrangement must be the same on every device.
- **A generic key-value settings table.** Rejected: nothing else needs one yet; a dedicated table is simpler to read and migrate.

### D2 — Tile registry in the frontend

`LogisModule` gets `dashboardWidgets?: DashboardWidget[]`. The core has its own list.

```ts
interface DashboardWidget<S = object> {
  type: string                 // 'core.todo', 'vacationrental.occupancy'
  title: string                // German, e.g. 'Belegung'
  defaultSettings: S
  Tile: ComponentType<{ settings: S }>
  Settings?: ComponentType<{ value: S; onChange: (s: S) => void }>
  wide?: boolean               // spans two columns where there are two or more
}
```

The catalog is the core list plus the widgets of active modules. Tiles of types not in the catalog are skipped when rendering, but kept when saving.

### D3 — Default arrangement in the frontend

`core/dashboard/defaults.ts` lists the default tiles:
- `core.todo`;
- `vacationrental.arrivals` (14 days, all units);
- `vacationrental.occupancy` (all units, without the previous year);
- `core.balances` (all accounts).

The frontend uses the default while `GET` returns `null`; tiles of inactive modules fall away through D2. The default is not written to the backend until the user customises, so improvements to the default reach everyone who never customised.

### D4 — Edit mode

„Anpassen“ in the page header switches to edit mode:
- each tile gets ↑, ↓, a settings button (only if it has `Settings`) and „Entfernen“;
- „Kachel hinzufügen“ opens a list of the catalog;
- „Standard wiederherstellen“ (with a confirmation) saves `tiles: null`;
- „Fertig“ ends the mode.

Every change saves at once with `PUT` and an optimistic update, so there is no separate save button to forget. The settings dialog edits a copy and saves on „Übernehmen“. New tiles get `crypto.randomUUID()` as ID.

Moving uses buttons instead of dragging: they work on phones and with the keyboard.

### D5 — Layout

The page uses a CSS grid inside the existing `@container` of the app shell:
- 1 column below `@2xl`;
- 2 columns from `@2xl`;
- 3 columns from `@5xl`.

`wide` tiles span 2 columns where there are at least 2. The tile order is the DOM order, so screen readers and phones read the user's order. Each tile is a `Card` with its title as heading (`h2`) and a region labelled with the title.

### D6 — Settings that point to objects

Settings hold IDs, e.g. `{ unitId: 7 }` or `{ accountIds: [3, 4] }`. A tile checks them against the visible objects from its hooks (`useRentalUnits`, `useAccounts`):
- **missing unit or single account** → the tile shows „Nicht mehr verfügbar. Bitte in den Einstellungen eine andere Auswahl treffen.“ and requests no data for that ID;
- **multi-select of accounts** → missing IDs are dropped from the shown list, and the tile says so only when none are left.

The backend never trusts these IDs: the existing APIs only return visible data anyway.

### D7 — The tiles

**„Zu erledigen“ (`core.todo`)** counts inbox items by result:
- `UNASSIGNED_UNIT` → „Einheit zuordnen“;
- `CHANGE_PROPOSED` → „Änderung vorgeschlagen“;
- `NOT_RECOGNISED` → „Nicht erkannt“.

Each count links to `/posteingang?ergebnis=<code>`. With vacation rental active, it adds „Buchungen prüfen“ from `useBookings({review: true})` (total), linking to `/ferienvermietung/buchungen?review=true`. Counts of zero are left out; all zero shows „Alles erledigt.“

Labels come from the existing inbox label registry. The booking count is contributed by the module through an optional `todoItems` hook on `LogisModule`, so the core tile does not import module code.

**„Nächste An- und Abreisen“ (`vacationrental.arrivals`):**
- `useBookings({from: today, to: today + N, unitId})`, without cancelled bookings;
- events: an arrival per booking whose arrival lies in the window, and a departure per booking whose departure lies in the window;
- sorted by date, departures before arrivals on the same day; at most 10 rows plus „Alle anzeigen“ to the calendar;
- each row: date, „Anreise“/„Abreise“, unit, guest name, or „Eigenbelegung“ for owner uses.

**„Belegung“ (`vacationrental.occupancy`, wide):**
- `useOverview(unitId, year)` for one unit. For all units, `useUnitsOverview(year)` lists the units with figures, and their monthly overviews are loaded and added up (`monthsTotal`, occupancy over days × units, as on the evaluation page). `useUnitsOverview` alone has only year totals, not months. This means one request per unit, sharing the evaluation's cache; an aggregate endpoint can follow if it gets slow;
- a compact Recharts bar chart of the 12 months, using the existing `ChartContainer` and palette, plus the yearly total in percent;
- the previous year optionally as a line, as on the evaluation page;
- links to the evaluation page with the same selection.

**„Einnahmen“ (`vacationrental.income`):**
- the sum of `settledNet` of the months up to the current month for this year and for the previous year, from the same per-unit monthly data;
- the difference in percent; links to the evaluation page.

**„Kontostände“ (`core.balances`):** the accounts from `useAccounts`, without archived ones, filtered by `accountIds` when set; name and balance, plus a total per currency.

**„Letzte Transaktionen“ (`core.transactions`):** `useTransactions({accountId, size: n})` with date, text, amount; links to the transactions page.

## Risks / Trade-offs

- **`useInbox` loads all inbox items for counting.** → It is already loaded for the inbox page and cached. If inboxes grow large, a count endpoint can follow without changing the spec.
- **Labels and tiles live in the frontend, unknown to the backend.** → The backend validates only the shape; a test keeps the default types in the catalog.
- **Concurrent edits from two devices.** → Last write wins; this is acceptable for a personal start page.
- **Deleting a user** → the row is removed by `ON DELETE CASCADE`. Users are normally deactivated, not deleted.

## Migration Plan

A new migration creates the table, with no data migration. Existing users have no row and see the default. Rollback: the frontend falls back to the default whenever the API fails.
