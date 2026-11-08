# Proposal

## Why

The occupancy calendar loads months endlessly in both directions while scrolling. Month rows are only 36px high, so the bottom trigger stays in reach and the page fires one request per month until dozens of months are loaded. Loading earlier months shifts the view, „Heute“ can land on the earliest loaded month instead of the current one, and saving a booking reloads every loaded month one by one. A year fits on one screen anyway, so paging by year is simpler and more predictable.

## What Changes

- The calendar shows one calendar year: December of the previous year, January to December, and January of the following year (14 rows).
- The user pages by whole years with „‹“ and „›“ and returns to the current year with „Heute“. The year is kept in the address (`?year=2026`). Without it, the current year is shown.
- The months of the neighbouring years are set apart visually, and the current month is marked.
- **BREAKING** (internal API): the calendar endpoint takes a year instead of a month and returns all 14 months of the property in one response. Only the frontend uses it.
- Endless scrolling, the buttons „Frühere Monate“ and „Spätere Monate“, and the address parameter `month` are removed.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `occupancy`: the requirement „Occupancy calendar“ (endless scrolling) is removed and replaced by „Occupancy calendar by year“, which shows a year with its neighbouring months and is paged by year.

## Impact

- Backend, module `vacationrental`: `OccupancyController` and `OccupancyService.calendar` (year instead of month, range from December of the previous year to January of the following year).
- Frontend, module `vacationrental`: `KalenderPage`, `CalendarGrid`, `useCalendarMonths` in `api.ts`, the tests `Kalender.test.tsx` and `Subscription.test.tsx`.
- e2e: `booking-calendar.spec.ts` and `mail-import.spec.ts` open the calendar with `?month=` and change to `?year=`.
- No database change.
- The planned change `owner-blocks` modifies the requirement „Occupancy calendar“, which this change removes. If this change is archived first, `owner-blocks` must move its calendar changes to „Occupancy calendar by year“.
