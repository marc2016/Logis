# Tasks

## 1. Real estate core

- [x] 1.1 Add `UnitSummary` and `UnitAccess.summaries(unitIds)` in `core.realestate`. Verify: A test returns name, property, usage and archive flag for visible units, and `ModularityTest` stays green.
- [x] 1.2 Create `core.documents` (D1, D2): `documents` table in migration `V20261107.1200__core_documents_costs.sql`, `DocumentOwnerType`, `DocumentService` with type/signature check and 10 MB limit (including the multipart setting), `DocumentController` and `Documents.deleteAll`, and add the named interface `documents`. Verify: Tests with a stub owner type cover upload, download as attachment, delete, „Nur PDF- oder Bilddateien bis 10 MB“ for a 12 MB PDF and a renamed `.docx`, 404 for an unknown owner type and when `canRead` is false.
- [x] 1.3 Add `property_costs` to the same migration, and implement `PropertyCost`, `PropertyCostService` with rights on the target and the conflict message, the endpoints, `PropertyCostDocumentOwner` and the delete block on properties (D1, D2). Verify: Integration tests cover the cost scenarios of `specs/property-finances`, „Die Kosten wurden inzwischen geändert“, receipts, and „Zum Objekt sind noch Kosten erfasst und es kann nur archiviert werden“.
- [x] 1.4 Add `FinanceSource`, `FinanceItem` and `PropertyFinanceService` with `GET /api/properties/{id}/finances` (monthly table, totals, items, filters, sorting) (D1, D4). Verify: Tests with a stub source cover merging, visibility (Carla sees only unit items, property costs only with a property role, 404 without any role) and that attributed transactions do not count.
- [x] 1.5 Add the public interface `UnitReferences` and call all beans in `PropertyService.delete` after the transaction check (design.md D1). Verify: A test with a stub bean that blocks gets HTTP 409 with the stub's reason, and existing property tests stay green.

## 2. Database

- [x] 2.1 Create `V20261107.1210__vacationrental_bookings.sql` with `btree_gist`, `bookings`, `statements` and `statement_lines` according to D2. Verify: The backend starts with the `dev` profile, and a direct SQL insert of an overlapping, not cancelled booking fails on the exclusion constraint.

## 3. Bookings (backend)

- [x] 3.1 Implement `Booking`, `BookingService` and `BookingController` (D3, D4): create, edit, cancel/reinstate, delete, delete guest data, the list with filters, text search, whitelisted sorting and paging, `GET /units`, the derived status and `nights`, and guest fields set to `null` below `EDITOR`. Verify: Integration tests cover every booking scenario of `specs/bookings` except documents, every sort key, and „Die Einheit wird nicht als Ferienwohnung genutzt“, „Die Einheit ist in diesem Zeitraum bereits belegt“, „Diese Buchungsnummer ist bereits erfasst“ and „Die Buchung ist abgerechnet und kann nur storniert werden“.
- [x] 3.2 Implement `BookingDocumentOwner` (read and write only with `EDITOR` on the unit) and delete documents with the booking. Verify: Integration tests cover the "Booking documents" scenarios, including 404 for Dieter on download, documents kept after deleting guest data, and documents gone after deleting the booking.
- [x] 3.3 Implement `BookingUnitReferences`. Verify: Deleting a property with bookings returns „Zum Objekt gibt es noch Buchungen und es kann nur archiviert werden“, and deleting it without bookings still works.

## 4. Statements (backend)

- [x] 4.1 Implement `Statement`, `StatementLine`, `StatementService` and `StatementController` (D2, D4): CRUD, assign/update/remove lines with gross defaulting to the expected rent, computed net and payout, provider suggestions. Verify: Integration tests cover every scenario of `specs/rental-statements`, including the 151.29 example, „Die Buchung gehört zu einer anderen Einheit“, „Die Buchung ist bereits abgerechnet“, the cancellation payout, and the status reverting to planned when a line or the statement is deleted.
- [x] 4.2 Implement `StatementFinanceSource` (D1). Verify: An integration test shows the "Settled booking on the property" items on the statement date and the "November at the holiday home" totals (-168.71), and no items with the module disabled.

## 5. Occupancy (backend)

- [x] 5.1 Implement `OccupancyService` and `OccupancyController` with the calendar and the yearly overview (D5). Verify: Unit tests cover the night split (4 + 3 nights) with the 700.00 in April on the statement date, the year boundary, the cancellation payout on its statement date, and planned rent in the arrival month. Integration tests cover the "Mixed month" scenario (11 nights, 37 %), and Carla seeing only "Whg. 2" in the calendar.
- [x] 5.2 Remove `VacationRentalStatusController`. Verify: With the module disabled, `/api/vacationrental/bookings` returns 404, and after enabling it again the bookings are still there.

## 6. Frontend

- [ ] 6.1 Create `modules/vacationrental/api.ts`, the nav items „Buchungen“, „Kalender“, „Abrechnungen“, „Auswertung“ with the index redirect to „Buchungen“, and remove `BelegungPage`. Verify: `npm run build` is green, and the sidebar test shows the four entries.
- [ ] 6.2 Implement the shared `components/DocumentList.tsx` with upload, then `BookingDialog` with it: stay, guests, amounts, guest data and documents for create and edit; the guest and document sections only with `canEdit`; „Gastdaten löschen“ with confirmation and the hint about remaining documents (D6). Verify: Component tests cover that files chosen on create are uploaded after saving, that a failed upload keeps the booking, and that a viewer sees no contact fields and no documents.
- [ ] 6.3 Add `@tanstack/react-table` and a reusable `components/DataTable.tsx` (D6): server-side sorting and paging, sortable headers, and a column visibility menu stored in `localStorage`. Verify: A component test covers the sort callback, hiding a column, and the table rendering when `localStorage` throws.
- [ ] 6.4 Implement `BuchungenPage` on `DataTable`: the filter bar, `sort`, the filters and the page in the URL, paging, „+ Buchung“ at the top right, and opening the dialog on a row click. Verify: Component tests cover that clicking a header sends `sort=arrival,asc`, that filters survive a reload from the URL, and that the button opens the dialog.
- [ ] 6.5 Implement `KalenderPage` with `CalendarGrid`, property picker, month navigation and „+ Buchung“ at the top right with the property preselected. Verify: Component tests cover planned vs. settled bars, a cancelled booking not shown, a new booking appearing after saving, and Carla seeing one row.
- [ ] 6.6 Implement `AbrechnungenPage`, `StatementDialog` and `StatementLineDialog` with provider suggestions and the payout. Verify: A component test shows the net per line and the payout after assigning a booking.
- [ ] 6.7 Implement `AuswertungPage` with the 12-month table and totals. Verify: A component test renders nights, occupancy rate, settled net and planned rent from a mocked response.
- [ ] 6.8 Add the „Finanzen“ section to `PropertyDetailPage` with the year table, the item list on `DataTable`, „+ Kosten“ at the top right and `CostDialog` with receipts (D6). Verify: Component tests cover the monthly totals from a mocked response, the unit field only for multi-unit properties, read-only contributed items, and attributed transactions marked „nicht im Ergebnis“.

## 7. Acceptance

- [ ] 7.1 Run the full test suites: `./mvnw verify` and `npm test && npm run lint && npm run build`. Verify: Everything is green.
- [ ] 7.2 Manual run with `./start.sh`:
  1. Using „+ Buchung“ in the grid, record booking "275505" on "Haus Seeufer" with the confirmation screenshot attached, then see it in the calendar as planned and find it by searching "275505".
  2. Create a statement, assign the booking, and check the net, the payout and the status settled.
  3. As a `VIEWER`, open the booking and check that there are no contact details.
  4. Delete the guest data and check the overview for November.
  5. On "Haus Seeufer", record a repair with „+ Kosten“ and a PDF receipt, then check that „Finanzen“ shows the booking income, commission, cleaning and repair, with the result for November.

  Verify: All steps behave as described in the specs.
- [ ] 7.3 Add a section "Vacation rental" to the README (bookings, documents, statements, guest data and its manual deletion), a section on property finances and costs,, and the 10 MB request body note to the deployment section. Verify: The section matches `specs/bookings` regarding visibility of contact data and documents.
