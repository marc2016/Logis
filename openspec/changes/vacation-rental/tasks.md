# Tasks

## 1. Real estate core

- [ ] 1.1 Add `UnitSummary` and `UnitAccess.summaries(unitIds)` in `core.realestate`. Verify: A test returns name, property, usage and archive flag for visible units, and `ModularityTest` stays green.
- [ ] 1.2 Add the public interface `UnitReferences` and call all beans in `PropertyService.delete` after the transaction check (design.md D1). Verify: A test with a stub bean that blocks gets HTTP 409 with the stub's reason, and existing property tests stay green.

## 2. Database

- [ ] 2.1 Create `V20261107.1200__vacationrental_bookings.sql` with `btree_gist`, `bookings`, `statements`, `statement_lines` and `booking_documents` according to D2. Verify: The backend starts with the `dev` profile, and a direct SQL insert of an overlapping, not cancelled booking fails on the exclusion constraint.

## 3. Bookings (backend)

- [ ] 3.1 Implement `Booking`, `BookingService` and `BookingController` (D3, D4): create, edit, cancel/reinstate, delete, delete guest data, the list with filters, text search, whitelisted sorting and paging, `GET /units`, the derived status and `nights`, and guest fields set to `null` below `EDITOR`. Verify: Integration tests cover every booking scenario of `specs/bookings` except documents, every sort key, and „Die Einheit wird nicht als Ferienwohnung genutzt“, „Die Einheit ist in diesem Zeitraum bereits belegt“, „Diese Buchungsnummer ist bereits erfasst“ and „Die Buchung ist abgerechnet und kann nur storniert werden“.
- [ ] 3.2 Implement booking documents (D2, D3, D4): upload with content type and signature check, a 10 MB limit including the multipart setting, download as attachment, delete, `documents` omitted below `EDITOR`, and cascade on booking delete. Verify: Integration tests cover the "Booking documents" scenarios, including „Nur PDF- oder Bilddateien bis 10 MB“ for a 12 MB PDF and for a renamed `.docx`, 404 for Dieter on download, documents kept after deleting guest data, and documents gone after deleting the booking.
- [ ] 3.3 Implement `BookingUnitReferences`. Verify: Deleting a property with bookings returns „Zum Objekt gibt es noch Buchungen und es kann nur archiviert werden“, and deleting it without bookings still works.

## 4. Statements (backend)

- [ ] 4.1 Implement `Statement`, `StatementLine`, `StatementService` and `StatementController` (D2, D4): CRUD, assign/update/remove lines with gross defaulting to the expected rent, computed net and payout, provider suggestions. Verify: Integration tests cover every scenario of `specs/rental-statements`, including the 151.29 example, „Die Buchung gehört zu einer anderen Einheit“, „Die Buchung ist bereits abgerechnet“, the cancellation payout, and the status reverting to planned when a line or the statement is deleted.

## 5. Occupancy (backend)

- [ ] 5.1 Implement `OccupancyService` and `OccupancyController` with the calendar and the yearly overview (D5). Verify: Unit tests cover the night split (700.00 → 400.00/300.00), the rounding remainder, the year boundary and the cancellation payout in the arrival month. Integration tests cover the "Mixed month" scenario (11 nights, 37 %) and Carla seeing only "Whg. 2" in the calendar.
- [ ] 5.2 Remove `VacationRentalStatusController`. Verify: With the module disabled, `/api/vacationrental/bookings` returns 404, and after enabling it again the bookings are still there.

## 6. Frontend

- [ ] 6.1 Create `modules/vacationrental/api.ts`, the nav items „Buchungen“, „Kalender“, „Abrechnungen“, „Auswertung“ with the index redirect to „Buchungen“, and remove `BelegungPage`. Verify: `npm run build` is green, and the sidebar test shows the four entries.
- [ ] 6.2 Implement `BookingDialog` with `DocumentList`: stay, guests, amounts, guest data and documents for create and edit; the guest and document sections only with `canEdit`; „Gastdaten löschen“ with confirmation and the hint about remaining documents (D6). Verify: Component tests cover that files chosen on create are uploaded after saving, that a failed upload keeps the booking, and that a viewer sees no contact fields and no documents.
- [ ] 6.3 Add `@tanstack/react-table` and a reusable `components/DataTable.tsx` (D6): server-side sorting and paging, sortable headers, and a column visibility menu stored in `localStorage`. Verify: A component test covers the sort callback, hiding a column, and the table rendering when `localStorage` throws.
- [ ] 6.4 Implement `BuchungenPage` on `DataTable`: the filter bar, `sort`, the filters and the page in the URL, paging, „+ Buchung“ at the top right, and opening the dialog on a row click. Verify: Component tests cover that clicking a header sends `sort=arrival,asc`, that filters survive a reload from the URL, and that the button opens the dialog.
- [ ] 6.5 Implement `KalenderPage` with `CalendarGrid`, property picker, month navigation and „+ Buchung“ at the top right with the property preselected. Verify: Component tests cover planned vs. settled bars, a cancelled booking not shown, a new booking appearing after saving, and Carla seeing one row.
- [ ] 6.6 Implement `AbrechnungenPage`, `StatementDialog` and `StatementLineDialog` with provider suggestions and the payout. Verify: A component test shows the net per line and the payout after assigning a booking.
- [ ] 6.7 Implement `AuswertungPage` with the 12-month table and totals. Verify: A component test renders nights, occupancy rate, settled net and planned rent from a mocked response.

## 7. Acceptance

- [ ] 7.1 Run the full test suites: `./mvnw verify` and `npm test && npm run lint && npm run build`. Verify: Everything is green.
- [ ] 7.2 Manual run with `./start.sh`:
  1. Using „+ Buchung“ in the grid, record booking "275505" on "Haus Seeufer" with the confirmation screenshot attached, then see it in the calendar as planned and find it by searching "275505".
  2. Create a statement, assign the booking, and check the net, the payout and the status settled.
  3. As a `VIEWER`, open the booking and check that there are no contact details.
  4. Delete the guest data and check the overview for November.

  Verify: All steps behave as described in the specs.
- [ ] 7.3 Add a section "Vacation rental" to the README (bookings, documents, statements, guest data and its manual deletion), and the 10 MB request body note to the deployment section. Verify: The section matches `specs/bookings` regarding visibility of contact data and documents.
