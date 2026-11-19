# Tasks

## 1. Backend

- [x] 1.1 Extract the per-unit calculation of `OccupancyService.overview` into a method without access check, and add `unitsOverview(userId, year)` with the endpoint `GET /api/vacationrental/occupancy/overview/units?year=` (design D4). Verify in `OccupancyTest`:
  - all visible vacation rental units across properties are listed with property names, sorted;
  - units without a role are missing;
  - each total equals the unit's `/overview` total;
  - archived units without figures are missing, and with figures present;
  - an invalid year gives 400.

## 2. Chart foundation

- [x] 2.1 Add `recharts` (exact latest, 3.11.0) and `src/components/ui/chart.tsx` via the shadcn CLI, and the `--chart-*` colour variables for light and dark if missing (design D1). Verify with `npm run build` that the library only lands in the `AuswertungPage` chunk.

## 3. Charts of one unit

- [x] 3.1 Add „Belegung“ and „Einnahmen“ above the table, with tooltips, abbreviated month labels and `figure` labels (design D2, D5). Verify in a frontend test with a mocked overview:
  - both figures are labelled for the year;
  - the legend names the series;
  - the owner-use share is computed from nights and month length;
  - settled and planned are separate series.
- [x] 3.2 Add the switch „Vorjahr einblenden“ with `previous=1` in the address and the previous-year lines (design D3). Verify in a frontend test that the switch loads the previous year's overview, adds a legend entry for that year, and survives a reload.

## 4. All units

- [x] 4.1 Add „Alle Einheiten“ to the unit selection (`unitId=all`), with both horizontal charts and the table with total row (design D4). Verify in a frontend test:
  - the request to `/overview/units`;
  - rows labelled with property names;
  - the total row, including the occupancy rule;
  - switching back to a unit shows its charts.
- [x] 4.2 Run `npm run lint`, `npm test` and `npm run build` in `frontend` and verify all pass.

## 5. End to end and documentation

- [x] 5.1 Extend `e2e/tests/evaluation.spec.ts`: one unit with charts and the previous-year switch, and „Alle Einheiten“ with two units of different properties. Add the evaluation page in both views to `e2e/tests/layout.spec.ts`. Update the `occupancy` row of `e2e/COVERAGE.md`. Verify against the compose stack on desktop and phone.
- [x] 5.2 Mention the charts and the „Alle Einheiten“ view in the README where the evaluation is described, if it is. Verify the wording matches the spec.
- [x] 5.3 Run `cd backend && ./mvnw test` and verify the whole suite passes.

## Workflow follow-up

- Archive the change after review (`/opsx:archive`).
