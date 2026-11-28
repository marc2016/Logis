# Proposal

## Why

The occupancy calendar needs about 950 px: a 5rem month column and 31 day columns of at least 1.75rem. On a phone, and on narrower desktop windows, it scrolls sideways and opens scrolled to today, so a month is never seen as a whole. The owner wants the calendar to fit the width instead and become narrow.

## What Changes

- The calendar always fits the available width; it never scrolls sideways. The 31 day columns share the width equally.
- With wide columns (tablet, desktop) every day keeps its number and two-letter weekday, as today.
- With narrow columns (phone) every day shows only its number in a smaller font; the weekday shows through the weekend background that Saturdays and Sundays already have.
- The month column gets narrower on small widths („Nov 26“ instead of „Nov. 2026“); the full month stays the row's accessible name.
- Booking bars keep their label as long as it fits and are cut off otherwise; tapping still opens the booking, and the name stays in the tooltip and the accessible name.
- Today stays marked. Scrolling today into view is dropped, since nothing scrolls any more.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `occupancy`: the occupancy calendar fits the width without sideways scrolling; weekdays show as abbreviations only with enough room; today is no longer scrolled into view.

## Impact

- Frontend: `modules/vacationrental/CalendarGrid.tsx` and its tests (`Kalender.test.tsx`).
- End-to-end: `booking-calendar.spec.ts` (the tests about sideways scrolling and scrolling to today), `layout.spec.ts` (the calendar on phone and tablet).
- No backend, no API, no data change.
