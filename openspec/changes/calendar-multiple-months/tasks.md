# Tasks

## 1. Calendar

- [x] 1.1 Replace `useCalendar` in the calendar page with an infinite query per month (`useCalendarMonths`) according to D1, loading the start month and the two following ones. Verify: a component test renders November 2026, December 2026 and January 2027 with their headings.
- [x] 1.2 Add loading on scroll with top and bottom sentinels and the buttons „Frühere Monate“ / „Spätere Monate“, keeping the scroll position when earlier months are inserted (D2). Verify: component tests load February 2027 with „Spätere Monate“ and October 2026 with „Frühere Monate“, and a test with a stubbed `IntersectionObserver` loads the next month when the bottom sentinel becomes visible.
- [x] 1.3 Replace the month arrows with „Heute“ (D3) and keep „+ Buchung“, opening bookings and the update after saving (D4). Verify: component tests cover „Heute“ returning to the start month, a bar across the month boundary in both months, and a new booking appearing in the shown months after saving.

## 2. Acceptance

- [x] 2.1 Run `npm test && npm run lint && npm run build`. Verify: everything is green.
- [x] 2.2 Check on a phone-sized screen (Playwright, 390 px wide) that scrolling loads months in both directions without jumps. Verify: screenshots show several months one below the other.
