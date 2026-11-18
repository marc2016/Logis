# Tasks

## 1. Data and booking rules

- [ ] 1.1 Add migration `V<yyyyMMdd>.<HHmm>__vacationrental_booking_kind.sql` adding `kind` (default `GUEST`, check `GUEST`/`OWNER`); verify with a migration test that existing bookings read as `GUEST`
- [ ] 1.2 Add `BookingKind` to `Booking`, input and views; owner uses get no guests, guest data, rent or review; verify with a `BookingService` test recording and editing an owner use
- [ ] 1.3 Keep overlap and number rules for owner uses; verify a guest booking overlapping an owner use is rejected with „Die Einheit ist in diesem Zeitraum bereits belegt“
- [ ] 1.4 Add the `kind` filter to the booking list API; verify with a list test

## 2. Statements, overview and feed

- [ ] 2.1 Reject assigning an owner use to a statement („Eine Eigenbelegung wird nicht abgerechnet“) and leave owner uses out of open bookings; verify with a statements test
- [ ] 2.2 Count owner-use nights separately in the yearly overview and leave them out of booked nights and occupancy rate; verify with the June 2027 scenario
- [ ] 2.3 Title owner uses „Eigenbelegung“ in the calendar feed without „?“; verify with a feed test

## 3. Mail import

- [ ] 3.1 Recognise „Eigentümerblockung <Nr>, <Objekt>, vom … bis …“ in `FewoUndMeerParser` as `OWNER_BLOCK`; verify with a parser test using an invented sample mail
- [ ] 3.2 Handle `OWNER_BLOCK` in `ImportEngine` (unknown unit, match a hand-recorded owner use with the same period, create, update period, period taken) with result `OWNER_USE_CREATED`; verify with `MailImportTest` cases for each scenario

## 4. Frontend

- [ ] 4.1 Booking form: „Gastbuchung | Eigenbelegung“ choice hiding guest, guest count, pets and rent; verify in `VacationRental.test.tsx`
- [ ] 4.2 Booking list: „Art“ column and filter; booking page without guest and rent sections for owner uses; verify in tests
- [ ] 4.3 Calendar: own bar style labelled „Eigenbelegung“; yearly overview: „Eigenbelegung“ nights column; verify in tests
- [ ] 4.4 Mail result label „Eigenbelegung angelegt“; verify in `Mail.test.tsx`

## 5. End-to-end and docs

- [ ] 5.1 Add an e2e journey: an owner block mail with invented values creates an owner use visible in the calendar, and a guest booking in that period is rejected; verify the suite passes
- [ ] 5.2 Update `e2e/COVERAGE.md` and run `./mvnw test`, `npm test`, `npm run lint`, `npm run build`
