# Proposal

## Why

The evaluation page („Auswertung“) shows the yearly overview of one unit as a table of twelve months. Seasons, gaps and the money behind them are hard to see in a table. There is also no way to compare a year with the year before, or to compare one's units with each other.

## What Changes

- **Charts on the evaluation page.** Above the existing table of a unit and year:
  - **Belegung:** occupancy rate per month as bars, with owner use stacked on top in its own colour.
  - **Einnahmen:** settled net and planned rent per month as bars side by side. They are never added up, as in the table.
- **Previous year.** A switch „Vorjahr einblenden“ adds the previous year to both charts as a line: the occupancy rate in one chart, settled net in the other.
- **All units.** The unit selection gets the entry „Alle Einheiten“. It compares every vacation rental unit the user can see, across properties, for the chosen year:
  - occupancy rate per unit;
  - settled net and planned rent per unit.
  
  Below the charts a table shows one row per unit with nights, occupancy rate, owner-use nights, settled net and planned rent. The unit names carry the property name. Archived units only appear if they have figures in that year.
- **Chart component.** shadcn's chart component, on top of Recharts, so that the charts use the same colours and dark mode as the rest of the interface. It is loaded only on the evaluation page.
- The figures and their rules stay as they are. The charts show the same numbers as the tables.

## Capabilities

### New Capabilities

### Modified Capabilities
- `occupancy`: new requirements for the charts of the yearly overview, the previous year in the charts, and the comparison of all units.

## Impact

- **Backend (`modules.vacationrental`):**
  - a new endpoint `GET /api/vacationrental/occupancy/overview/units?year=` with the year totals of every visible vacation rental unit;
  - it reuses the calculation of the existing overview.
- **Frontend:**
  - `recharts` (exact version) and `src/components/ui/chart.tsx` from shadcn;
  - charts, switch and the „Alle Einheiten“ view on `AuswertungPage`.
- **Tests:**
  - a backend test for the new endpoint (visibility, archived units, totals equal to the per-unit overview);
  - frontend tests for both views and the switch;
  - an e2e test for both views on desktop and phone.
  - `layout.spec.ts` covers the evaluation page at phone and tablet widths.
