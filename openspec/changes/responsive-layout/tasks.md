# Tasks

## 1. Layout check

- [ ] 1.1 Add `e2e/support/layout.ts` with a helper that returns overlapping visible controls (buttons, inputs, selects, comboboxes; >3 px, ignoring nesting) and controls that extend past the window or their `[role=dialog]`, skipping those inside a horizontally scrolling frame. Verify: on the current code it reports the overlapping „Objekt“/„Einheit“ selects on „Buchungen“ at 1024 px and nothing on „Konten“.
- [ ] 1.2 Add `e2e/tests/layout.spec.ts`: as an owner with a property, a unit, a booking imported from a mail and some transactions (via `e2e/support/data.ts` and `mail.ts`, invented values only), open „Buchungen“, „Transaktionen“, „Posteingang“, „Kalender“, the booking page and the „Freigeben“ dialog of the property at 390, 820 and 1024 px with the sidebar open; fail with page, width and control names. Verify: it fails on the current code for the cases in proposal.md – Why.
- [ ] 1.3 Add `layout.spec.ts` to the app-shell and end-to-end-tests rows in `e2e/COVERAGE.md`. Verify: the file names the new test.

## 2. Content area as size container

- [ ] 2.1 Make the page wrapper in `frontend/src/app/layout/AppShell.tsx` a size container (`@container`) with a short comment why pages use `@…:` variants. Verify: `npm test -- AppShell` and `npm run build` pass; no visual change at 1280 px.

## 3. Filter bars

- [ ] 3.1 `BuchungenPage.tsx`: replace `sm:grid-cols-2 lg:grid-cols-8` with an auto-fill grid (min 11 rem per control). Verify: layout check passes for „Buchungen“ at all widths; screenshot at 1024 and 1280 px shows readable dates and search.
- [ ] 3.2 `TransactionsPage.tsx`: same grid; account, object, category and search span two columns only from `@2xl`. Verify: layout check passes for „Transaktionen“; „Von“/„Bis“ show a full date at 1024 px; existing `TransactionsPage` unit tests pass.
- [ ] 3.3 `InboxFilters.tsx`: same grid. Verify: layout check passes for „Posteingang“; „Eingang von“/„Eingang bis“ are at least 176 px wide at 1024–1180 px; inbox unit tests pass.

## 4. Page layouts

- [ ] 4.1 `BookingPage.tsx`: switch the side-by-side layout, the tab list, the forced-visible tab panels and the sticky source panel from `lg:` to `@5xl:`. Verify: layout check passes for the booking page; at 1024 and 1280 px with the sidebar open the tabs „Daten“/„Quelle“ show; at 1440 px both panels sit side by side; `booking-proposal.spec.ts` still passes on desktop and phone.
- [ ] 4.2 Review the remaining page-level `lg:`/`xl:` grids (`PropertiesPage`, `AccountsPage`, `AccountPage`, `CategoriesPage`) and move those that depend on the content width to container variants. Verify: screenshots at 820, 1024 and 1280 px with sidebar open and collapsed show no overlap; frontend tests pass.

## 5. Sharing dialog

- [ ] 5.1 Confirm in the browser why the „Freigeben“ dialog content grows past the dialog at 390 px, then fix `ShareDialog.tsx` (no min-content growth of the dialog grid, share-row controls wrap below the name on narrow dialogs, invite controls stacked below `sm:`). Verify: layout check passes for the dialog at 390 px; `sharing.spec.ts` passes on desktop and phone; ShareDialog unit tests pass.

## 6. Integration

- [ ] 6.1 Run `cd frontend && npm test && npm run lint && npm run build` and the full e2e suite (desktop and phone). Verify: all green.
- [ ] 6.2 Screenshot every page at 390, 820, 1024 and 1280 px with the sidebar open, and at 1024 px collapsed, and compare with the findings in proposal.md – Why. Verify: none of the listed issues remains and no new overlap appears.

## Workflow follow-up

- Archive the change with `/opsx:archive` after merge and sync the specs.
