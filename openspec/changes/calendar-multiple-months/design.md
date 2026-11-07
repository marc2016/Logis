# Design

## Context

`KalenderPage` loads one month with `useCalendar(propertyId, month)` from `GET /api/vacationrental/occupancy/calendar?propertyId=&month=` and renders it with `CalendarGrid` (CSS grid, days as columns, rows per unit, sticky unit column). Arrows change `?month=` in the address. `nightsInMonth` in `calendar.ts` already clips a stay to a month. Motivation is in proposal.md.

## Goals / Non-Goals

**Goals:**
- Endless scrolling in both directions with only a few months in memory at a time to start with.
- No backend change; one request per month as today.

**Non-Goals:**
- One long timeline with months side by side (variant A) or month tiles with a week grid (variant C) from exploration.
- Virtualising (unmounting) months that are far away; a few dozen months of a few rows each are cheap.

## Decisions

### D1: Infinite query per month

`useInfiniteQuery` with `queryKey ['vacationrental', 'calendar', propertyId, startMonth]`, `initialPageParam = startMonth`, `getNextPageParam = last month + 1`, `getPreviousPageParam = first month - 1`, each page fetched from the existing endpoint. The first render fetches the start month and then the next two (`fetchNextPage` twice once the first page arrived), so a phone screen is filled.

- *Alternative:* a new range endpoint (`from`, `months`). It would save requests, but it needs backend work, and paging per month fits the infinite query naturally.

### D2: Loading on scroll

Two sentinel elements (top and bottom) observed with `IntersectionObserver` (`rootMargin: 400px`) call `fetchPreviousPage` / `fetchNextPage`, once at a time (`isFetching…` guard). Visible buttons „Frühere Monate“ and „Spätere Monate“ do the same for keyboard users, and in tests (jsdom has no `IntersectionObserver`; the hook does nothing without it).

When earlier months are inserted above, the scroll position is kept: the page stores `scrollHeight` before `fetchPreviousPage` and restores `scrollTop += newHeight - oldHeight` after the pages change, because CSS scroll anchoring is not reliable in every browser.

### D3: Layout

```
[+ Buchung]                        Objekt [Haus Seeufer v]  [Heute]
[ Frühere Monate ]
November 2026           <- month heading (sticky under the page header on scroll)
  Einheit | 1 2 3 ... 30
  Whg. 1  |   ####
Dezember 2026
  ...
[ Spätere Monate ]
```

- `CalendarGrid` stays per month and gets the month heading from the page; each month keeps its own day columns, so months of different lengths align by day number.
- „Heute“ resets `?month=` to the current month (new query key, so the list starts fresh) and scrolls to its heading.
- `?month=` remains the start month for deep links and existing links.

### D4: Updates

Saving or changing a booking invalidates `['vacationrental']`; TanStack Query refetches all loaded pages of the infinite query, so every visible month stays current.

## Risks / Trade-offs

- [Many requests when scrolling far] → One small request per month, only on demand; a user scrolling two years causes 24 requests over time.
- [Scroll jump when earlier months load] → Height compensation in D2; a test checks that the page calls it, the visual effect is checked manually.
- [Refetching many loaded months after each save] → Acceptable for a few dozen months; can be limited later with `maxPages`.
