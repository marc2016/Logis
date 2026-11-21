# Tasks

## 1. Backend

- [x] 1.1 In `BookingService.applyProposal`, end the review when a cancellation is applied to a booking in state `NEW` (design D5). Verify with `BookingReviewTest` cases: apply on `NEW` → cancelled, state `NONE`, documents kept, period free; apply on `CHANGE` unchanged; `discardProposal` on `NEW` keeps `NEW`.
- [x] 1.2 Add `cancellationReported` to `OccupancyService.CalendarBooking` (design D3). Verify with a calendar test: true for a booking with a cancellation proposal, false for a change proposal and for no proposal.
- [x] 1.3 Extend `MailImportTest` with an invented „Last-Minute Buchung“ mail followed by its „Stornierung“ on an unconfirmed booking: state `NEW`, `proposal.type` `CANCELLATION`, the cancellation mail among the sources; then `proposal/apply` → status `CANCELLED`, state `NONE`. Reuse `fewo-last-minute.eml` and `fewo-cancellation-250905.eml`.

## 2. Frontend

- [x] 2.1 Reorder `ReviewPanel` so a cancellation proposal is shown before the `NEW` notice, with the texts and three buttons of design D4, and show the cancellation mail as the source in `BookingPage`. Verify with `BookingReview.test.tsx`: notice and buttons on a `NEW` booking, each button calls its endpoint, the cancellation mail is shown although it is not the latest source, a confirmed booking keeps the two-button panel.
- [x] 2.2 Show the badge „Stornierung gemeldet“ in the `BookingPage` header and the `BuchungenPage` list when `proposal.type` is `CANCELLATION`. Verify with `BookingReview.test.tsx` (list row and page header) and `BulkBookings.test.tsx` (list row).
- [x] 2.3 Add `cancellationReported` to `CalendarBooking` in `api.ts` and mark the bar in `CalendarGrid` (design D3). Verify with `Kalender.test.tsx`: label contains „Stornierung gemeldet“.
- [x] 2.4 Make the bulk action „Bestätigen“ skip bookings with a cancellation proposal (design D7). Verify with `BulkBookings.test.tsx`.
- [x] 2.5 Move on in the review mode only when no review is left on the booking (design D6). Verify with `ReviewQueue.test.tsx`: „Stornierung übernehmen“ and „Buchung verwerfen“ advance, „Stornierung verwerfen“ stays.

## 3. End-to-end

- [ ] 3.1 Add a test to `e2e/tests/booking-proposal.spec.ts`: an imported, unconfirmed booking gets a cancellation mail; the list shows „Stornierung gemeldet“; the page shows the notice; „Stornierung übernehmen“ cancels it. Add the journey to the `bookings` row of `e2e/COVERAGE.md`. Verify: the spec passes against a stack with the new images.

## 4. Verification

- [ ] 4.1 Run `cd backend && ./mvnw test` and `cd frontend && npm test && npm run lint && npm run build`. All must be green.
- [ ] 4.2 Check that tests, sample mails and specs of this change contain only invented values and nothing from the pasted mails (booking number, guest name, phone numbers, staff names, dates).

## Workflow follow-up

- Archive with `/opsx:archive` and sync the `bookings` spec.
