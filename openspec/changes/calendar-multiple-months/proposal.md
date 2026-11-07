# Proposal

## Why

The booking calendar shows a single month and needs a click for every further month. To see free and occupied periods ahead (and look back), the user wants to see as many months as they like, one below the other, simply by scrolling. That works on the phone too.

## What Changes

- The calendar page shows months one below the other, starting with the current month (or the month given in the address).
- Scrolling down loads the next month, scrolling up loads the previous one. There is no fixed limit.
- A button „Heute“ jumps back to the current month. The arrows for the previous and next month are removed.
- Stays across a month boundary show as a partial bar in each month, as before.
- „+ Buchung“ and opening a booking work as before. After saving, all months that are shown update.
- The backend and its endpoint per month stay unchanged.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `occupancy`: The occupancy calendar shows a continuous, scrollable sequence of months instead of one month.

## Impact

- **Frontend:** `KalenderPage` and `CalendarGrid` in `src/modules/vacationrental/`. Months are loaded with an infinite query (TanStack Query) on the existing endpoint `/api/vacationrental/occupancy/calendar`.
- **Backend:** no changes.
- **Assumptions** (proposed during exploration, not explicitly confirmed; easy to change):
  - Three months are loaded at first: the start month and the two after it. Further months load one at a time when the user scrolls near the start or the end.
  - Buttons „Frühere Monate“ and „Spätere Monate“ at the top and bottom also load months, for keyboard users and as a fallback.
  - The address keeps only the property and the start month (`?month=`); months loaded by scrolling are not stored in it.
