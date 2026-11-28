# Tasks

## 1. Calendar

- [x] 1.1 Make the grid fit the width: shrinkable day columns, no `overflow-x-auto`, no sticky month column, month column `5rem`/`2.75rem` by a container query on the calendar frame (design D1, D2). Verify: `npm run build` and the existing `Kalender.test.tsx` pass.
- [x] 1.2 Show number and weekday on wide calendars and only the number (8 px, centred) on narrow ones, month label „Nov. 2026“ or „Nov 26“, accessible names unchanged (design D2). Verify with `Kalender.test.tsx` cases: both labels exist with the narrow one hidden by the container class; the cell's accessible name still names the weekday.
- [x] 1.3 Narrow bars: less padding on narrow calendars, label cut off, tooltip and accessible name unchanged (design D3). Verify with a `Kalender.test.tsx` case on a two-night bar.
- [x] 1.4 Remove scrolling to today: `scrollSideways`, the `scrollToToday` prop and its use in `KalenderPage` (design D4). Verify: `npm run lint` shows no new warnings and the today mark test passes.

## 2. End-to-end

- [x] 2.1 Replace the two sideways-scrolling tests in `booking-calendar.spec.ts` with one: on desktop the cell of today shows number and weekday; on the phone the calendar frame has no sideways overflow (`scrollWidth <= clientWidth`), day 31 of a month is inside the window, and today shows its number only. Verify: passes on desktop and phone.
- [x] 2.2 Let `layout.spec.ts` check that the calendar does not overflow sideways at 390, 820 and 1024 px. Update the bookings and occupancy rows of `e2e/COVERAGE.md`. Verify: `layout.spec.ts` passes.

## 3. Verification

- [x] 3.1 Run `cd frontend && npm test && npm run lint && npm run build`. All must be green.
- [ ] 3.2 Run the full end-to-end suite against a stack with the new frontend image. It must be green.
- [x] 3.3 Check screenshots of the calendar at 390, 820 and 1440 px for cut-off or overlapping text.

## Workflow follow-up

- Archive with `/opsx:archive` and sync the `occupancy` spec.
