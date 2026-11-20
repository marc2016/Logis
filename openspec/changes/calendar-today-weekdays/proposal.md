# Proposal

## Why

The occupancy calendar shows one row per month and one column per day. It shades weekends but does not say which weekday a day is, and it marks only the current month, not today. Finding „next Friday“ or today's arrivals takes counting. Because every row is a different month, the same weekday falls into a different column in each row, so a header row cannot help.

## What Changes

- **Weekdays:** every day cell shows its weekday as a two-letter abbreviation next to the day number, e.g. „20 Fr“. Two letters are unambiguous; one letter would not be (M for Mo/Mi, D for Di/Do, S for Sa/So).
- **Today:** today's cell is marked: the day number on the accent colour, a thin accent frame, and „heute“ for screen readers.
- **Scrolling:** when the calendar opens on the current year, or the user chooses „Heute“, today is scrolled into view. This matters on phones, where the calendar is wider than the screen.
- **Unchanged:** the existing „Heute“ button that returns to the current year stays as it is.

## Capabilities

### New Capabilities

### Modified Capabilities
- `occupancy`: „Occupancy calendar by year“ gains weekdays, the mark for today and scrolling to today.

## Impact

- **Frontend:** `modules/vacationrental/CalendarGrid.tsx` and `pages/KalenderPage.tsx`, with their tests.
- **e2e:** a check in `booking-calendar.spec.ts`, and the calendar at phone width in `layout.spec.ts`, which already covers the calendar.
- **Unchanged:** backend and API.
