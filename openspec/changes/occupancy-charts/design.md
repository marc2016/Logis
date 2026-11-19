# Design

## Context

- **Calculation.** `OccupancyService.overview(userId, unitId, year)` in `modules.vacationrental` computes the yearly overview of one unit (`Overview(unitId, year, months, total)` with `MonthFigures`):
  - nights split by night;
  - owner use separately;
  - settled net by statement date;
  - planned rent by arrival.
  
  It requires `VIEWER` on the unit. `access.visibleUnits(userId)` and `access.summaries(...)` give the visible units with property, usage and archived flags.
- **Page.** `AuswertungPage.tsx` holds unit and year in the address (`unitId`, `year`). It loads `useOverview(unitId, year)` and renders a table. The unit selection uses `IdSelect` with `useRentalUnits()`, whose `RentalUnit` carries `propertyName` and `archived`.
- **Frontend.** React 19, Tailwind 4, shadcn (`radix-nova`, colours as CSS variables). Pages are lazy-loaded chunks, and there is no chart library yet.
- **Layout.** Pages pick columns by the content width (`@container`, `@3xl:` …). `e2e/tests/layout.spec.ts` checks pages for overlapping or cut-off controls at 390, 820 and 1024 px.

## Goals / Non-Goals

**Goals:**
- Charts that show exactly the numbers of the tables, with one calculation in the backend.
- The same look as the rest of the interface, in light and dark mode, and usable on a phone.
- No change to the calculation rules of `occupancy`.

**Non-Goals:**
- Charts elsewhere (property finances, calendar).
- Multi-year trends beyond the previous year.
- The previous year in the „Alle Einheiten“ view (decided in planning; it would crowd the chart).
- Exporting charts as images.

## Decisions

### D1. shadcn chart on Recharts
- Add `recharts` (latest stable 3.x, exact version) and generate `src/components/ui/chart.tsx` with the shadcn CLI. It provides `ChartContainer`, `ChartTooltip`, `ChartLegend` and colour config through CSS variables.
- Chart colours come from new variables `--chart-1` … `--chart-4` in `index.css` for light and dark, if the theme does not define them yet.
  - booked and settled: primary series;
  - owner use and planned: secondary series;
  - previous year: muted.
- Recharts renders SVG, so text stays crisp and can be labelled.
- Alternatives considered:
  - Chart.js (canvas; theming by hand);
  - ECharts (too large);
  - hand-made SVG (tooltips, axes and responsiveness all by hand).
- The library lands in the lazy chunk of `AuswertungPage` only.

### D2. Charts of one unit (A, B)
- Data: the existing `Overview.months`. No backend change.
- „Belegung“:
  - a `BarChart` with stacked bars per month: `occupancy` (booked, %) and `ownerRate`, which is the owner-use nights as a percentage of the month's days, computed in the frontend from `ownerNights` and the month length;
  - Y axis 0–100 %.
- „Einnahmen“: a `BarChart` with two bars per month, `settledNet` and `plannedRent` (not stacked, so they are never added up). The Y axis shows euros formatted `de-DE`.
- Tooltips show the exact values like the table: nights, %, euros.
- Month labels:
  - `Jan`, `Feb`, … on the X axis;
  - single letters below 480 px of chart width, using the `ChartContainer`'s measured width.

### D3. Previous year (C)
- Search parameter `previous=1`. When it is on, `useOverview(unitId, year - 1)` is loaded in addition (cached by React Query like any year).
- „Belegung“ gets a `Line` with the previous year's `occupancy`, and „Einnahmen“ a `Line` with the previous year's `settledNet`. Recharts `ComposedChart` combines bars and lines.
- The legend names „2026“ and „2025“.

### D4. All units (D)
- **Backend:** `GET /api/vacationrental/occupancy/overview/units?year=` returns `[{ unitId, unitName, propertyId, propertyName, archived, total: MonthFigures }]`.
  - The service iterates over `access.visibleUnits(userId)`, filtered to `UnitUsage.VACATION_RENTAL`, and reuses the existing per-unit calculation, extracted into a private method that takes no access check, because visibility was checked once.
  - Archived units, and units of archived properties, are dropped when their total has 0 nights, 0 owner nights, 0 settled net and 0 planned rent.
  - The list is sorted by property name, then unit name.
  - A per-unit loop is fine for the expected size (a handful of units). If it ever grows, the queries can be batched without changing the API.
- **Frontend:**
  - the unit selection offers „Alle Einheiten“ (address `unitId=all`);
  - horizontal bar charts, one row per unit labelled „Unit (Property)“, so long names stay readable on a phone: occupancy rate, and settled net with planned rent side by side;
  - below them the table.
- **Total row:**
  - nights, owner nights and money summed;
  - occupancy rate = summed nights / (days of the year × number of listed units), rounded like the existing rate (`Occupancy.rate`).
  - The frontend computes it from the rows, and a unit test pins the rule.

### D5. Accessibility and tests
- Every chart is wrapped in a `figure` with `aria-label` (for example „Belegung je Monat 2026“). The table below remains the precise, accessible source.
- jsdom has no layout, so Recharts renders little there. Frontend tests:
  - mock `ResponsiveContainer`'s size (a fixed width via the `initialDimension` prop or a `ResizeObserver` stub);
  - check labels, legend, the switch and the requests, not pixels.
- E2E tests check that the charts render (SVG bars present, legend text) on desktop and phone. `layout.spec.ts` gets the evaluation page in both views.

## Risks / Trade-offs

- [Bundle size: about 100 KB gzip] → Only in the evaluation chunk.
- [Recharts and React 19] → Recharts 3 supports React 19. The version is pinned, and `npm run build` and the tests catch breaks.
- [Many units make the horizontal chart tall] → Its height grows with the number of units (about 36 px per unit). That is acceptable for the expected size.
- [Previous-year line with missing data] → A year without figures shows a flat line at 0, which is what the table shows too.

## Migration Plan

No data or schema changes. The frontend and the new endpoint ship together. Rollback means removing the frontend charts. The endpoint is additive.
