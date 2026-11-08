# Tasks

## 1. Backend

- [x] 1.1 Change `OccupancyController` and `OccupancyService.calendar` to `year` with the range from December of the previous year to January of the following year (design D1); verify in `OccupancyTest` that a stay across the year boundary is in the responses for 2026 and 2027, that `from`/`to` are `2025-12`/`2027-01`, and that an invalid year is rejected

## 2. Frontend

- [x] 2.1 Replace `useCalendarMonths` with `useCalendarYear` in `api.ts` and adapt the `Calendar` type (`year`, `from`, `to`); verify with `npm run build`
- [x] 2.2 Let `CalendarGrid` render the months from `from` to `to` with muted neighbouring months and the marked current month (design D2); verify in `Kalender.test.tsx` that 14 rows are shown, the outside rows are marked, and a stay across the year boundary appears in both December and January
- [x] 2.3 Rewrite `KalenderPage` with year paging, „Heute“, `?year=` and the fallback for `?month=`, removing sentinels, scroll compensation and the buttons for earlier and later months; verify in `Kalender.test.tsx` that paging requests the next year, „Heute“ returns to the current year, and an old `?month=` link opens its year
- [x] 2.4 Adapt `Subscription.test.tsx` to the new endpoint, and verify `npm test` and `npm run lint` pass

## 3. Integration

- [x] 3.1 Change the e2e tests `booking-calendar.spec.ts` and `mail-import.spec.ts` to `?year=` and verify they type-check (`npx tsc --noEmit` in `e2e`)
- [x] 3.2 Run `cd backend && ./mvnw test` and `cd frontend && npm test && npm run lint && npm run build`, and verify all pass
