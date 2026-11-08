# Tasks

## 1. Database

- [x] 1.1 Add migration `V20261110.1100__vacationrental_agencies.sql` with the tables, columns, data migration and index swap from design D1/D2, and verify with a new `AgencyMigrationTest` that seeds the old schema (mixed-case names, channel-only booking, booking with agency and channel, unit mappings) and checks every resulting reference

## 2. Agencies backend

- [x] 2.1 Add `Agency`, alias handling, `AgencyRepository` and `AgencyService` with list (with visible booking counts), create, rename (keeps old name as alias), archive/restore, add/remove mail names; verify in a new `AgenciesTest` with the scenarios of the `agencies` spec for creating, duplicates, renaming, archiving and mail names
- [x] 2.2 Add merge and delete to `AgencyService` (design D5); verify in `AgenciesTest` the merge scenario, the booking number conflict, merged unit mappings, and deleting an agency in use
- [x] 2.3 Add `AgencyController` under `/api/vacationrental/agencies` with optimistic locking; verify in `AgenciesTest` that a second user sees the same list and that a stale version is rejected

## 3. Bookings

- [x] 3.1 Replace `agency` and `channel` on `Booking`, `BookingService`, `BookingController` and `BookingRepository` with `agencyId` (design D6), including `refTaken`, `findByRef`, `adoptAgency`, the list filter and sorting; remove `Channel`; verify by adapting `BookingsTest` and adding cases for unique numbers across a rename and for the agency filter

## 4. Mail import

- [x] 4.1 Resolve agencies in `ImportEngine` through `AgencyService.resolve` and move `UnitMapping`, `UnitMappingRepository` and `BookingMailController` to `agencyId` (design D4); verify in `MailImportTest` the scenarios for a new agency from a mail, a known mail name after renaming, a mapping that survives renaming, and the cancellation after adding a mail name, while all existing mail tests stay green

## 5. Frontend

- [x] 5.1 Update the booking types in `api.ts` and the fixtures (agency object, no channel) and add agency API hooks; verify with `npm run lint` and `npm run build`
- [x] 5.2 Replace the channel select and the agency text field in `BookingDialog` with the agency combobox including „anlegen“; verify with a test in `VacationRental.test.tsx` that a new agency can be created from the dialog
- [x] 5.3 Remove the channel column from `BuchungenPage` and filter by agency; verify with a test in `VacationRental.test.tsx`
- [x] 5.4 Add the page „Vermittler“ (list, create, rename, archive, mail names, merge, delete) and its navigation entry; verify with a new `Vermittler.test.tsx` covering rename and merge

## 6. Integration

- [x] 6.1 Run `cd backend && ./mvnw test` and `cd frontend && npm test && npm run lint && npm run build`, and verify all pass
- [ ] 6.2 Extend the e2e booking test with choosing an agency in the dialog, and verify the e2e suite passes
