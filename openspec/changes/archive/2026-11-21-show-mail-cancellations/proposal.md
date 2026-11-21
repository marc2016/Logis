# Proposal

## Why

A cancellation mail for a booking that is itself still „zu prüfen“ is matched and stored as a proposed cancellation, but nothing shows it: the booking page only offers „Bestätigen“ and „Verwerfen“ for the new booking, and the list and calendar show the same „zu prüfen“ marker as for any imported booking. The owner confirms a booking the agency has already cancelled and learns about the cancellation only afterwards, if at all. This is the usual case when older mails are fetched, where the booking mail and its cancellation arrive in the same run.

## What Changes

- The booking page shows a reported cancellation also while the booking is not confirmed yet, with the proposing mail, and offers „Stornierung übernehmen“, „Stornierung verwerfen“ and „Buchung verwerfen“.
- Applying the reported cancellation of a booking that is not confirmed yet cancels it and ends its review in one step.
- Bookings with a reported cancellation carry the marker „Stornierung gemeldet“ in the booking list, the calendar and the header of the booking page, in addition to „zu prüfen“.
- The bulk action „Bestätigen“ skips bookings with a reported cancellation, like it skips other proposed changes.
- Recognition and matching of cancellation mails stay as they are. No schema change.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `bookings`: a cancellation reported by mail is visible wherever the booking is marked „zu prüfen“, also on bookings that are not confirmed yet, and applying it there ends the review.

## Impact

- Backend (`vacationrental`): `BookingService.applyProposal` (review state after a cancellation), bulk confirm rule, `OccupancyService.CalendarBooking` gets a flag for a reported cancellation. No migration: existing bookings with a stored cancellation proposal show the marker as soon as the change is deployed.
- Frontend: `ReviewPanel`, `BookingPage` header, `BuchungenPage` (badge, bulk confirm), `CalendarGrid`, `api.ts`.
- Tests: `BookingReviewTest`, `MailImportTest`, `BookingReview.test.tsx`, `Kalender.test.tsx`, `BulkBookings.test.tsx`, e2e `booking-proposal.spec.ts`, `e2e/COVERAGE.md`.
