# Tasks

## 1. Calendar

- [x] 1.1 Replace `useCalendar` in the calendar page with an infinite query per month (`useCalendarMonths`) according to D1, loading the start month and the two following ones. Verify: a component test renders November 2026, December 2026 and January 2027 with their headings.
- [x] 1.2 Add loading on scroll with top and bottom sentinels and the buttons „Frühere Monate“ / „Spätere Monate“, keeping the scroll position when earlier months are inserted (D2). Verify: component tests load February 2027 with „Spätere Monate“ and October 2026 with „Frühere Monate“, and a test with a stubbed `IntersectionObserver` loads the next month when the bottom sentinel becomes visible.
- [x] 1.3 Replace the month arrows with „Heute“ (D3) and keep „+ Buchung“, opening bookings and the update after saving (D4). Verify: component tests cover „Heute“ returning to the start month, a bar across the month boundary in both months, and a new booking appearing in the shown months after saving.

## 2. Acceptance

- [x] 2.1 Run `npm test && npm run lint && npm run build`. Verify: everything is green.
- [x] 2.2 Check on a phone-sized screen (Playwright, 390 px wide) that scrolling loads months in both directions without jumps. Verify: screenshots show several months one below the other.

## 3. Slim table with one row per month

- [x] 3.1 Choose one unit at the top instead of the property (`?unitId=`) and reduce each month to that unit (D3). Verify: a component test with two units shows only the bookings of the chosen one, and a unit-only user can choose only their unit.
- [x] 3.2 Render all loaded months as rows of one table with a sticky month column and 31 day columns, day numbers in the cells (D3). Verify: component tests find the rows November 2026 to January 2027, a bar across the month boundary in two rows, and update the other calendar tests to rows.
- [x] 3.3 Run `npm test && npm run lint && npm run build` and check the phone layout (Playwright, 390 px wide). Verify: everything is green and a screenshot shows months as slim rows.
