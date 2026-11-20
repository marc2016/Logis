# Design

## Context

`CalendarGrid` draws one CSS grid row per month and 31 day columns. Each cell is `h-9`, shows the day number at the top in `text-[10px]`, and has a `bg-muted/50` background on weekends. Booking bars sit in the same grid area with a top margin of `mt-2.5`, so the top 10 px of each cell belong to the day number.

`KalenderPage` passes `currentMonth` from `todayIso()`; the „Heute“ button only resets the year in the address. The grid scrolls sideways in its own frame (`overflow-x-auto`); on phones the columns are at least `1.75rem` wide.

## Goals / Non-Goals

**Goals:**
- Weekday abbreviation and today's mark in the existing top line of each cell.
- Scrolling to today.

**Non-Goals:**
- A week view.
- Week numbers.
- Changes to the bars.

## Decisions

### D1 — Weekday next to the number

Each cell shows `20` followed by `Fr` in a lighter colour, in the same top line. The labels are a fixed list (`So, Mo, Di, Mi, Do, Fr, Sa`, indexed by `getUTCDay()`), not `Intl` short weekday names, because `Intl` gives „Fr.“ with a dot.

At the minimum column width of 28 px, „20 Fr“ at 10 px fits within the cell padding: about 22 px of text. The cell gets an `aria-label` „Freitag, 20. November 2026“, so screen readers get the full date.

**Alternative:** the weekday on a second line under the number. This would need higher rows, and the bars would have to move. Rejected unless the e2e layout check shows clipping on phones.

### D2 — Today's mark

`CalendarGrid` gets `today` (ISO date). The matching cell:
- gets `aria-current="date"` and a `data-today` attribute;
- shows the day number on a small `bg-primary text-primary-foreground` pill;
- gets a `ring-1 ring-primary ring-inset`.

Only the cell whose month and day equal today is marked, so the neighbouring months of another year never are. The row keeps its current-month mark.

### D3 — Scrolling to today

`KalenderPage` remembers when it shows the current year and calls `scrollIntoView({ inline: 'center', block: 'nearest' })` on the today cell after the calendar data has loaded. This happens on the first render and after „Heute“.

It does not move the page vertically more than needed: `block: 'nearest'`. It does not run while the user pages through other years. The call is guarded with `?.` because jsdom lacks it.

## Risks / Trade-offs

- **Very narrow phones (below 360 px).** „30 Mo“ could touch the cell border. → `layout.spec.ts` checks the calendar at 390 px. If clipping shows up, fall back to D1's alternative only below a container width.
- **The time zone.** `todayIso()` uses the browser's local date, as the current month mark already does.
