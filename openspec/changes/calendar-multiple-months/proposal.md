# Proposal

## Why

The booking calendar shows a single month and needs a click for every further month. To see free and occupied periods ahead (and look back), the user wants to see as many months as they like, one below the other, simply by scrolling. That works on the phone too. A box per month takes too much room; the user wants one slim table with one row per month.

## What Changes

- The calendar shows one unit, chosen at the top („Einheit“), instead of all units of a property.
- It is a single table: one row per month, columns for the days 1 to 31. Month names stay at the left when the table is swiped sideways.
- It starts with the current month (or the month given in the address). Scrolling down loads later months, scrolling up loads earlier ones. There is no fixed limit.
- A button „Heute“ jumps back to the current month. The arrows for the previous and next month are removed.
- Stays across a month boundary show as a partial bar in each month row.
- „+ Buchung“ and opening a booking work as before. After saving, all months that are shown update.
- The backend and its endpoint per month stay unchanged.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `occupancy`: The occupancy calendar shows one unit as a continuous, scrollable table with one row per month instead of one month for all units.

## Impact

- **Frontend:** `KalenderPage` and `CalendarGrid` in `src/modules/vacationrental/`. Months are loaded with an infinite query (TanStack Query) on the existing endpoint `/api/vacationrental/occupancy/calendar`.
- **Backend:** no changes.
- **Later:** choosing several units at once is out of scope; then each unit gets its own colour.
- **Assumptions** (proposed during exploration, not explicitly confirmed; easy to change):
  - The start month and the two after it load first; further months load while the end of the table is near the screen.
  - Buttons „Frühere Monate“ and „Spätere Monate“ at the top and bottom also load months, for keyboard users and as a fallback.
  - The address keeps only the unit (`?unitId=`) and the start month (`?month=`); months loaded by scrolling are not stored in it.
