# Design

## Context

See proposal.md for the motivation. `CalendarGrid.tsx` renders a CSS grid with `gridTemplateColumns: 5rem repeat(31, minmax(1.75rem, 1fr))` inside a frame with `overflow-x-auto`. The month column is sticky so that it stays visible while scrolling. Each day cell shows the number and, under it, the weekday (design D1 of calendar-today-weekdays). `scrollSideways` centres today when the current year opens or „Heute“ is chosen. Booking bars are buttons spanning their nights, with `truncate`.

## Goals / Non-Goals

**Goals:**

- No sideways scrolling at any width the app supports (from 360 px).
- Unchanged look where there is room (tablet, desktop).

**Non-Goals:**

- Another layout for phones (week view, list).
- Changing the height of the rows or the vertical scrolling of the page.

## Decisions

### D1: Columns that may shrink

The day columns become `minmax(0, 1fr)`; the frame loses `overflow-x-auto` and `w-max`. The month column is `5rem` on wide calendars and `2.75rem` on narrow ones. The sticky month column is no longer needed and is dropped.

Why not keep a minimum width and scroll below it: the owner explicitly wants no sideways scrolling.

### D2: Wide or narrow by the calendar's own width

A container query on the calendar frame decides (Tailwind 4 `@container` with a named breakpoint), not the window: the sidebar is shown on some widths and hidden on others, so the window says little about the room the calendar gets. The threshold is a calendar width where a day column is about 22 px, i.e. `2.75rem + 31 * 22px`, rounded to `46rem`:

| Calendar width | Day cell | Month label |
|---|---|---|
| >= 46rem | number and „Mo“ under it, as today | „Nov. 2026“ |
| < 46rem | number only, smaller font (8 px), centred | „Nov 26“ |

The weekday stays in the accessible name of every cell („Freitag, 20. November 2026“), so nothing is lost for screen readers. Both labels are rendered and one is hidden by the container query, so the switch needs no JavaScript and no measuring.

### D3: Bars

Bars keep `truncate`; on narrow calendars their horizontal padding shrinks to 1 px so that a few letters fit. The tooltip and the accessible name already carry the full label. No minimum width: a bar must never cover a neighbouring night.

### D4: No scrolling to today

`scrollSideways`, the `scrollToToday` prop and the effect are removed. The today mark (ring and filled number) stays.

## Risks / Trade-offs

- [8 px digits are small] → They only appear on phones; the number is a help for orientation, the exact date is in the booking that a tap opens and in the accessible name.
- [Container queries in old browsers] → Tailwind 4 already relies on them (the layout uses `@2xl` etc.); without support the wide variant shows, which only gets narrower, never scrolls.
- [The layout check accepted the calendar because it scrolled in a frame] → `layout.spec.ts` must now also check that the calendar frame has no sideways overflow.
