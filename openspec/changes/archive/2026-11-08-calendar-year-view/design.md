# Design

## Context

- `GET /api/vacationrental/occupancy/calendar?propertyId=&month=` returns one month of a property (`Calendar` with `month` and the units with their bookings overlapping that month). `OccupancyService.calendar` uses `BookingRepository.findForUnits(unitIds, from, to)`, which already accepts any range.
- `useCalendarMonths` in `api.ts` is an infinite query with one page per month. `KalenderPage` drives it with two `IntersectionObserver` sentinels, scroll compensation for earlier months and a „Heute“ button. `CalendarGrid` renders `Calendar[]`, one row per page, and splits bars per month with `nightsInMonth` from `calendar.ts`.
- `useInvalidateRental` invalidates everything under `['vacationrental']`, so an infinite query refetches each loaded page in sequence.
- No other client uses the calendar endpoint. The iCal feed has its own endpoint.

## Goals / Non-Goals

**Goals:**
- One request per shown year, so refreshing after a save costs one request.
- No scroll-driven loading and no scroll position correction.

**Non-Goals:**
- Changing the yearly overview („Auswertung“), which already works per year.
- Choosing a different number of months, or showing several units at once.
- Keyboard shortcuts for paging.

## Decisions

### D1: Endpoint takes a year and returns a range

`GET /api/vacationrental/occupancy/calendar?propertyId=7&year=2026` returns

```json
{ "propertyId": 7, "propertyName": "Haus Seeufer", "year": 2026,
  "from": "2025-12", "to": "2027-01", "units": [ { "id": 70, "bookings": [ ... ] } ] }
```

The bookings are those overlapping 2025-12-01 to 2027-01-31, through the existing `findForUnits`. `month` is dropped, and the parameter `month` is no longer accepted. Years outside 1900 to 2999 are rejected like in the overview.

- Alternative: keep the month endpoint and request 14 months. Rejected, because it is the cause of the many requests.
- Alternative: a generic `from`/`to` range. Rejected for now, because the only view needs exactly this range. Deriving it on the server keeps the client simple and the response cacheable per year.

### D2: Frontend renders months from one response

- `useCalendarYear(propertyId, year)` replaces `useCalendarMonths`. It is a plain `useQuery` with the key `['vacationrental', 'calendar', propertyId, year]`, so `useInvalidateRental` refreshes it with one request.
- `CalendarGrid` takes the list of months (`from` to `to`, built with `shiftMonth`) and the unit's bookings, and keeps using `nightsInMonth` per row. Rows outside the year get a muted style and `data-outside="true"`. The current month gets a marked row header and `aria-current="date"`.
- `KalenderPage` reads `year` from the address (default: current year), shows „‹ 2025“, „2026“ and „2027 ›“ as controls, and „Heute“ removes `year`. The sentinels, the scroll compensation and the buttons „Frühere Monate“ and „Spätere Monate“ go away.
- An old link with `?month=2026-11` is read as the year 2026, so bookmarks keep working.

## Risks / Trade-offs

- [A slightly larger response per request (14 months of bookings)] → A unit has a few hundred stays per year at most, which is still small.
- [Bars over the year boundary appear on two pages] → Intended. This is exactly what the neighbouring months are for.
- [Conflict with `owner-blocks`] → It modifies „Occupancy calendar“, which this change removes. If this change is archived first, `owner-blocks` must move its delta to „Occupancy calendar by year“. It still has to be rebased anyway.

## Migration Plan

No data migration. Deploying backend and frontend together is enough, because both are built from the same commit into the same release.
