# Tasks

## 1. Data model

- [x] 1.1 Add migration `V20261114.1200__vacationrental_booking_sources.sql` with `booking_sources` and `booking_field_sources` (design D2), and the entities or repositories for them. Verify with a test that Flyway migrates an existing database to the new schema without touching existing bookings, and that Hibernate validates.
- [x] 1.2 Add the `BookingField` enum and `Location(part, start, end)` (design D3, D4). Verify that the backend compiles (`./mvnw -q compile`).

## 2. Locations in recognition

- [x] 2.1 Add the `Reading` helper that finds values in subject or text and records the stripped group offsets. Verify in a unit test that the offsets cover exactly the value, also with spaces and tabs around it.
- [x] 2.2 Extend `ParsedMail` with `locations` and switch `FewoUndMeerParser` to the helper for all subject and text fields, including the multi-line address and shared locations for adults and children (design D4). Verify in `MailParsersTest` that, for the confirmation, the forwarded variants, the short-notice notice, the cancellation and the block notice, `text.substring(start, end)` or `subject.substring(start, end)` equals the written value for every reported field.
- [x] 2.3 Do the same for `HoliduDsParser`, including the address line split into street, postal code, city and country. Verify in `MailParsersTest` with the Holidu and VVV test mails.
- [x] 2.4 Add `quotes` to `AiExtraction` and the extractor schema, and locate passages whitespace-tolerantly in `MailRecognizer.toParsed` (design D5). Verify in a unit test that a found passage yields its location, a passage with different line breaks is still found, and a missing passage leaves the value without a location.
- [x] 2.5 Add `MailRecognizer.recognizeByRules` for the backfill. Verify that it never calls the AI extractor (test with a mock).

## 3. Sources on import and in the booking lifecycle

- [x] 3.1 Add the `BookingSources` service, which creates a source from `MailContent` and writes `CURRENT` and `PROPOSAL` locations with normalized `source_value` (design D6). Let `ImportEngine` pass `MailContent` through and use the document id from `attach`. Verify in `MailSourcesTest`:
  - a confirmation creates one source with locations for all fields;
  - completing a `NEW` booking adds the new fields' locations from the second mail;
  - a matching mail fills only missing locations;
  - a change mail writes `PROPOSAL` locations;
  - a block notice adds a source only.
- [x] 3.2 Move the value comparisons (`sameName`, `samePhone`, strings, amounts, dates) from `ImportEngine` into a shared helper used by the import and the differs computation (design D7). Verify that the existing `MailImportTest` and `BookingReviewTest` stay green.
- [x] 3.3 Move `PROPOSAL` locations to `CURRENT` on apply, delete them on discard and on a replacing proposal, and delete `guest*` locations on guest-data deletion (design D7). Verify in `MailSourcesTest` with the spec scenarios „Apply a change“, „Discard a change“ and „Delete guest data“.
- [x] 3.4 Publish `DocumentDeleted` from `DocumentService.delete` and delete the matching source in `vacationrental` (design D9). Verify in a test that deleting the change mail's document removes only that source and its locations.

## 4. API

- [x] 4.1 Add `sources`, `locations` (with `differs`) and `proposalLocations` to the booking view for `EDITOR` and above, and empty values for others (design D10). Verify in `MailSourcesTest`:
  - an imported booking returns locations;
  - a guest name edited by hand returns `differs: true` and the mail value;
  - editing it back returns `differs: false`;
  - a `VIEWER` gets no sources.
- [x] 4.2 Add `GET /bookings/{id}/sources/{sourceId}`. Verify in `MailSourcesTest` that it returns subject, sender, date and text for an editor, and 404 for a viewer and for a source of another booking.

## 5. Backfill

- [x] 5.1 Add `Documents.contents(ownerType, ownerId, contentType)` and `BookingSourceBackfill`, which runs on start for bookings with mail documents but no source, oldest mail first, rules only, one transaction per booking (design D8). Verify in a test that:
  - a booking with a confirmation and a change `.eml` gets two sources;
  - the departure points to the change mail and differs if the change was not applied;
  - the booking data is unchanged;
  - an unrecognised mail is skipped;
  - a second run creates nothing.

## 6. Frontend: booking page

- [x] 6.1 Add the types and hooks for the booking view extensions and source text to `api.ts` (`useBooking(id)`, `useBookingSource(bookingId, sourceId)`). Verify with `npm run build`.
- [x] 6.2 Extract `BookingForm` from `BookingDialog` with `data-field` attributes on every input, and reduce `BookingDialog` to new bookings (design D11). Verify that the existing booking tests in `VacationRental.test.tsx` for creating a booking still pass.
- [x] 6.3 Add `BookingHeader` with guest, period, nights, unit, agency, rent, status, review marker and the review actions from `ReviewPanel`. Proposal values become buttons that activate their locations. Verify in a new `BookingPage.test.tsx` that the header shows these details and that confirming removes the marker.
- [x] 6.4 Add `SourcePanel` with source tabs, segment rendering with `<mark>`, active highlight with scrolling, and click-to-focus. Add the „weicht vom Dokument ab“ hint under differing fields. Verify in `BookingPage.test.tsx` that:
  - focusing the arrival highlights its passage in the right tab;
  - clicking a mark focuses its field;
  - a differing field shows the mail value;
  - a viewer sees no source panel.
- [x] 6.5 Add the route `buchungen/:bookingId` with `BookingPage`, the „Daten“ and „Quelle“ tabs below `lg`, the back link using `state.back`, navigation after discard or delete, and „Buchung nicht gefunden“. Verify in `BookingPage.test.tsx`.
- [x] 6.6 Let the booking list rows, the calendar's `onOpen` and the inbox action „Zur Buchung“ navigate to the page with `state.back`. Verify in `VacationRental.test.tsx`, `Kalender.test.tsx` and `BookingReview.test.tsx` that clicking opens the page address, and adapt the tests that used the dialog for existing bookings.

## 7. Frontend: statement page

- [x] 7.1 Add the route `abrechnungen/:statementId` with `StatementPage` (header with provider, unit, period, date and payout, lines and dialogs moved from `AbrechnungenPage`, „Abrechnung nicht gefunden“). Let `AbrechnungenPage` navigate there and redirect `?statement=`. Verify in the statement tests in `VacationRental.test.tsx`, including the old link.

## 8. Integration

- [x] 8.1 Update the e2e tests `mail-import.spec.ts` (open the imported booking page, check a highlighted source passage, confirm) and `booking-calendar.spec.ts` (open the booking page from the calendar, statement page). Verify that they type-check (`npx tsc --noEmit` in `e2e`).
- [x] 8.2 Run `cd backend && ./mvnw test` and `cd frontend && npm test && npm run lint && npm run build`, and verify that all pass.
