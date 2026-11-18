# Design

## Context

See proposal.md – Why for the findings. The shell (`AppShell.tsx`) puts pages into `SidebarInset`; the shadcn sidebar is an overlay below 768 px and a fixed 16 rem column above. Pages use Tailwind viewport variants (`sm:`, `lg:`, `xl:`) for their grids. Tailwind 4.3 is in use, which has container queries built in (`@container`, `@md:`, `@3xl:` …) without a plugin. Dialogs render in a portal at the body, outside the content area.

## Goals / Non-Goals

**Goals:**
- Page layouts react to the width of the content area, so the open sidebar no longer breaks them.
- Filter bars never squeeze a control below a readable width.
- A repeatable check catches regressions.

**Non-Goals:**
- Card views for tables on phones; tables keep scrolling sideways inside their frame.
- Changing the sidebar breakpoint or behaviour.
- Redesigning dialogs beyond the sharing dialog (the others were checked and fit).

## Decisions

### Content area as a size container
The page wrapper in `AppShell.tsx` (`div.flex-1 p-4 md:p-6`) gets `@container`. Page-level layouts that depend on width use container variants instead of viewport variants: `lg:grid-cols-2` → `@5xl:grid-cols-2` etc.
- *Alternative:* raise the viewport breakpoints (e.g. `xl:` instead of `lg:`). Rejected: still wrong when the sidebar is collapsed (wastes space) and whenever the sidebar width changes.
- Dialogs stay on viewport variants: they are not inside the container and are sized from the viewport anyway.

### Filter bars as auto-fill grids
Filter grids use `grid-cols-[repeat(auto-fill,minmax(11rem,1fr))]` instead of fixed column counts per breakpoint. Each control gets at least 11 rem (176 px), enough for a German date `TT.MM.JJJJ` with the picker icon and for „Alle Kategorien“; the bar wraps to as many rows as fit. Wide fields that need more room (search, account, object in „Transaktionen“) keep a span of 2 via a container variant (`@2xl:col-span-2`), so on a phone they still take one column.
- *Alternative:* container variants with fixed column counts per page. Rejected: needs tuning per filter count and breaks again when a filter is added.
- Applies to `BuchungenPage.tsx`, `TransactionsPage.tsx`, `InboxFilters.tsx`. The exact `minmax` value is checked against the screenshots; 11 rem is the starting point.

### Booking page: side by side from `@5xl`
Form and source panel sit side by side only from a content width of 64 rem (1024 px); below, the tabs „Daten“ and „Quelle“ stay. `lg:hidden`, `lg:grid-cols-2`, `lg:data-[state=inactive]:block` and `lg:sticky …` become their `@5xl:` counterparts. At 1280 px with the sidebar open the content is about 976 px, so tabs are shown there; at 1440 px both panels appear.

### Sharing dialog
Share rows: the name block already has `min-w-0 flex-1`; the fixed `w-36` role select plus icon button do not fit beside it at 390 px together with dialog padding, and the dialog's grid lets the row grow. Fix: give the dialog content children `min-w-0` (the grid track must not grow to min-content), wrap a share row's controls below the name on narrow dialogs (`flex-wrap`, controls `ml-auto`), and let the invite row stack until `sm:` as it already intends. The cause is confirmed during implementation with the dev tools before the fix.

### Layout check in the e2e suite
New `e2e/tests/layout.spec.ts` signs in as an owner with test data, then for each width (390, 820, 1024) with the sidebar open, opens „Buchungen“, „Transaktionen“, „Posteingang“, „Kalender“, a booking imported from a mail, and the sharing dialog of a property. A helper in `e2e/support/layout.ts` collects visible buttons, inputs, selects and comboboxes and reports
- pairs whose boxes overlap by more than 3 px (ignoring nesting),
- controls extending past the window or past their `[role=dialog]`, unless inside an element that scrolls horizontally (table frames).
The test runs in both projects like every other test; it sets its widths itself, so the project only changes the starting viewport.
- *Alternative:* screenshot comparison. Rejected: brittle against data changes and needs baseline images in git.

### Found during implementation
- The booking page had the same min-content problem as the sharing dialog on the phone: a long document name stretched the panel grid to 463 px. Its grid gets `grid-cols-[minmax(0,1fr)]` as well; names now truncate.
- The inbox date field (`DateField`) had a fixed `w-32` input, one pixel too narrow for „TT.MM.JJJJ“ at the phone font size. Input and calendar button now fill their column up to 11 rem.
- The bookings search spans two columns from `@2xl`, like the search in „Transaktionen“, so its placeholder fits.

## Risks / Trade-offs

- [Container variants are less familiar than viewport variants] → a short comment at the `@container` in `AppShell.tsx` explains why pages use `@…:` variants.
- [Auto-fill grids change the order in which filters wrap on desktop] → filters keep their source order; at 1280 px all bookings filters fit in two rows, which was checked in the old screenshots as acceptable.
- [The overlap helper produces false positives (e.g. decorative overlays, focus rings)] → it only looks at interactive controls and uses a 3 px tolerance; specific exceptions are listed in the helper with a reason.
