# Tasks

## 1. Backend storage

- [ ] 1.1 Add the migration `core/V<yyyyMMdd>.<HHmm>__core_dashboard.sql` with `core.dashboards` (design D1). Verify: the backend starts; a migration test checks the table and the cascade on user deletion.
- [ ] 1.2 Add `core/dashboard` with `GET` and `PUT /api/dashboard` and the shape validation (design D1). Verify with `DashboardTest`:
  - no row → `tiles: null`;
  - save and read;
  - `null` resets;
  - one user cannot see or change another's arrangement;
  - rejected cases (too many tiles, a bad type, duplicate IDs, settings over 2 KB, settings that are not an object) answer HTTP 400 with a German message;
  - unknown types are kept;
  - signed-out access is rejected.

## 2. Frontend framework

- [ ] 2.1 Add `DashboardWidget`, `dashboardWidgets` and `todoItems` to `LogisModule`, the catalog (core plus active modules), the defaults and the `useDashboard` / `useSaveDashboard` hooks with optimistic update (design D2, D3). Verify with unit tests:
  - the default with and without vacation rental;
  - unknown types are skipped in rendering but kept when saving.
- [ ] 2.2 Rebuild `DashboardPage` with the grid layout (design D5) and the edit mode (design D4): „Anpassen“, ↑/↓, settings dialog, „Entfernen“, „Kachel hinzufügen“, „Standard wiederherstellen“ with confirmation, and „Fertig“. Verify with `Dashboard.test.tsx`:
  - add, move, remove, reset;
  - a second tile of the same type with other settings;
  - each change sends the expected `PUT`.

## 3. Core tiles

- [ ] 3.1 „Zu erledigen“ (design D7), including the module's „Buchungen prüfen“ through `todoItems`. Verify with tests for counts and links, zero counts left out, and „Alles erledigt.“
- [ ] 3.2 „Kontostände“ with account selection, and „Letzte Transaktionen“ with count and account (design D7, D6). Verify with tests:
  - balances and totals per currency;
  - selection;
  - a no longer visible account shows „Nicht mehr verfügbar“ for the single-account setting and is dropped from the selection.

## 4. Vacation rental tiles

- [ ] 4.1 „Nächste An- und Abreisen“ with 7/14/30 days and unit (design D7). Verify with tests:
  - arrivals and departures in the window, sorted, owner use labelled;
  - unit filter;
  - an invisible unit shows „Nicht mehr verfügbar“.
- [ ] 4.2 „Belegung“ (wide) and „Einnahmen“ with unit / all units and the previous-year option (design D7). Verify with tests:
  - totals and the year-to-date comparison from overview fixtures;
  - links to the evaluation page with the same selection.
- [ ] 4.3 Register the three tiles in `modules/vacationrental/index.ts`. Verify: with the module inactive, they are neither in the catalog nor in the default.

## 5. End-to-end, documentation and checks

- [ ] 5.1 Add `e2e/tests/dashboard.spec.ts` and a row in `e2e/COVERAGE.md`. The test:
  - sees the default tiles;
  - customises (adds „Belegung“ for one unit, moves „Kontostände“ up, removes a tile);
  - reloads and signs in again to see the arrangement kept;
  - resets to the default.

  Add a phone and tablet check to `layout.spec.ts`. Verify: both specs green.
- [ ] 5.2 Update the README line for the start page. Run `cd backend && ./mvnw test` and `cd frontend && npm test && npm run lint && npm run build`; all must be green.

## Workflow follow-up

- Archive with `/opsx:archive`; the new `dashboard` spec is created on sync.
