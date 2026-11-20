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

### D1 — Weekday under the number

Each cell shows the day number and, below it, the weekday in a lighter, slightly smaller type (9 px). The labels are a fixed list (`So, Mo, Di, Mi, Do, Fr, Sa`, indexed by `getUTCDay()`), not `Intl` short weekday names, because `Intl` gives „Fr.“ with a dot. The cell gets an `aria-label` „Freitag, 20. November 2026“, so screen readers get the full date.

The rows grow from 36 to 48 px, and the bars start 24 px below the top of the row instead of 10 px, so the number and the weekday stay visible above a bar.

**Alternative tried first:** the weekday next to the number („20 Fr“). It made the columns wider than their 28 px minimum, so at 1280 px the calendar no longer fit and the last days of each month were cut off. Rejected after a screenshot review.

### D2 — Today's mark

`CalendarGrid` gets `today` (ISO date). The matching cell:
- gets `aria-current="date"` and a `data-today` attribute;
- shows the day number on a small `bg-primary text-primary-foreground` pill;
- gets a `ring-1 ring-primary ring-inset`.

Only the cell whose month and day equal today is marked, so the neighbouring months of another year never are. The row keeps its current-month mark.

### D3 — Scrolling to today

When the current year is shown (on opening and after „Heute“), `CalendarGrid` scrolls its own frame sideways so that today's cell is centred in the part right of the sticky month column. It only does this if the cell is not fully visible already. The page itself never scrolls.

**Alternative tried first:** `scrollIntoView({ inline: 'center', block: 'nearest' })`. It also scrolled the page down to the current month, hiding the header and the unit selection. Rejected after a screenshot review.

The scroll does not run while the user pages through other years, nor after bookings reload.

## Risks / Trade-offs

- **Taller rows.** The calendar of a year is about 170 px taller. → Accepted: the weekdays were wanted, and the width matters more than the height.
- **The time zone.** `todayIso()` uses the browser's local date, as the current month mark already does.
