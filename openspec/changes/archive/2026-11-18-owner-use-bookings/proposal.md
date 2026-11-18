# Proposal

## Why

Fewo & Meer confirms dates the owner blocks for themselves with a mail „Eigentümerblockung <Nr>, <Objekt>, vom … bis …“. Logis does not know this mail type, so it lands in the inbox as „Nicht erkannt“, and Logis has no way to record that the owner occupies a unit: those days look free in the calendar and in the calendar feed.

## What Changes

- New kind of booking: **owner use** („Eigenbelegung“) next to the guest booking. An owner use has a unit, a period and optionally a booking number and agency; it has no guests, guest data or expected rent and is never „zu prüfen“.
- Owner uses occupy the unit like guest bookings: no overlaps, shown in the occupancy calendar (distinguishable from guest bookings) and in the calendar feed as „Eigenbelegung“.
- Owner uses can be recorded by hand: when recording, the user chooses „Gastbuchung“ or „Eigenbelegung“. The booking list can be filtered by kind.
- The yearly overview shows owner-use nights on their own; they do not count as booked nights or towards the occupancy rate.
- Owner uses cannot be assigned to a statement.
- Mails „Eigentümerblockung …“ from Fewo & Meer are recognised and create the owner use directly on the mapped unit; a later mail with the same number and another period changes the period directly.
- Existing bookings become guest bookings by a migration; no data changes otherwise.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `bookings`: new kind owner use, recording by hand, list filter by kind.
- `booking-mails`: recognise the Fewo & Meer owner block notice; it creates or updates an owner use without review.
- `occupancy`: owner uses in the calendar and on their own in the yearly overview.
- `calendar-subscription`: owner uses in the feed as „Eigenbelegung“.
- `rental-statements`: owner uses cannot be settled.

## Impact

- Backend `vacationrental`: `Booking` (new `kind`), `BookingService`, `BookingController`, `FewoUndMeerParser`, `ParsedMail`, `ImportEngine`, `BookingMailResult`, `OccupancyService`, `CalendarFeed`, statement assignment.
- Migration `V<timestamp>__vacationrental_booking_kind.sql`: adds `kind` with default `GUEST` for all existing rows.
- Frontend `modules/vacationrental`: booking form and page, booking list filter, calendar bars, yearly overview, mail result label.
- Tests: backend parser/import/occupancy/feed tests, frontend tests, an e2e journey and `e2e/COVERAGE.md`.
