# Tasks

## 1. Calendar grid

- [ ] 1.1 Show the weekday abbreviation next to the day number in every day cell, with an `aria-label` of the full date (design D1). Verify with `Kalender.test.tsx`: the cell of 20 November 2026 shows „20“ and „Fr“, and 21 November shows „Sa“.
- [ ] 1.2 Mark today's cell with `aria-current="date"`, the number pill and a ring; pass `today` from `KalenderPage` (design D2). Verify with tests:
  - with a fixed today, exactly one cell is marked;
  - in another year, none is.
- [ ] 1.3 Scroll today into view when the current year is shown and after „Heute“ (design D3). Verify with a test that `scrollIntoView` is called on the today cell on opening and after „Heute“, and not for another year.

## 2. Checks

- [ ] 2.1 Extend `booking-calendar.spec.ts`: the today cell is visible and marked, and a day cell shows its weekday. The calendar at 390, 820 and 1024 px in `layout.spec.ts` must stay free of overlaps. Verify: both specs green.
- [ ] 2.2 Run `cd frontend && npm test && npm run lint && npm run build`; all green.

## Workflow follow-up

- Archive with `/opsx:archive` and sync the `occupancy` spec.
