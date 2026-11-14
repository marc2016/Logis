# Proposal

## Why

A booking opens in a modal dialog. The dialog has no address, so it cannot be bookmarked, linked from the inbox or reopened with the browser's back button. It also has no room for the booking's source. Most bookings now come from agency mails, and the original mail is stored with the booking only as a `.eml` download. To check an imported value, the user downloads the mail and searches it by hand. Statements have the same problem: their details sit below the list instead of on a page of their own.

## What Changes

- **Booking page.** Each booking gets its own page at `/ferienvermietung/buchungen/<id>`, which replaces the edit dialog. The booking list, the calendar and the inbox's „Zur Buchung“ lead there, and the back button returns to the list or calendar with its filters.
  - Recording a new booking stays a dialog.
- **Header.** The page highlights the important details in a header: guest, period with number of nights, unit, agency, expected rent, status and the review marker. For bookings „zu prüfen“ or with a proposed change, the header carries the review actions.
- **Split view.** The page shows the booking's data on the left and its source on the right: the readable text of the mails the booking came from, one tab per mail. On phones the source is a tab next to the data.
- **Field locations.**
  - When a booking is created or changed from a mail, the system stores for each field where in the mail its value was found. Rule parsers report the exact position. The AI fallback quotes the text it read, and the system locates the quote.
  - Clicking or focusing a field opens the mail its current value came from and highlights the location. Clicking a highlighted location focuses the field.
  - A field whose value was edited by hand and no longer matches the mail is marked „weicht vom Dokument ab“, with the mail's value next to it.
  - Proposed changes show where in the newer mail each new value was found.
- **Text snapshot.** The text the parser read is stored with the booking when the mail is imported, so locations stay exact when the mail-to-text conversion changes later. The `.eml` document stays as it is.
- **Existing bookings.** A one-time run after the update derives sources and field locations from the `.eml` documents already attached to existing bookings, using the rule parsers only. It does not change any booking data.
- **Statement page.** Each statement gets its own page at `/ferienvermietung/abrechnungen/<id>`, with a header (provider, unit, period, statement date, payout) and its lines. Old `?statement=` links lead there. A source panel for statements is not part of this change.
- **Groundwork for PDFs.** The model of sources and locations allows PDF sources later (see IDEAS.md). No PDF preview or PDF locations are built now.

## Capabilities

### New Capabilities
- `booking-sources`: sources of a booking (mail text snapshots), the location of each field's value in a source, how locations are recorded on import and kept on edits, proposals and guest-data deletion, visibility, and the one-time derivation for existing bookings.

### Modified Capabilities
- `bookings`: a booking opens as its own page with header and split view instead of a dialog. The review and proposal requirements and the agency field refer to the page.
- `booking-mails`: recognition reports, for each extracted value, where in the mail it was found, and the AI fallback quotes its sources.
- `rental-statements`: a statement opens as its own page with an address.
- `mail-inbox`: the linked record of a mail opens directly.

## Impact

- **Backend (`vacationrental`):**
  - `ParsedMail` and the parsers (`FewoUndMeerParser`, `HoliduDsParser`) return locations alongside values. `GermanText.find` gets a locating variant.
  - `AiExtraction`, `ClaudeAiExtractor` and `MailRecognizer` handle quotes.
  - `ImportEngine` stores sources and locations.
  - `BookingService` keeps locations on proposals, applies and guest-data deletion.
  - `BookingController` adds source and location data to the booking view, plus an endpoint for a source's text.
  - A new startup runner fills in sources for existing bookings.
- **Backend (`core.documents`):** an event when a document is deleted, so a source whose `.eml` is deleted goes with it. A read access to a document's content for the backfill.
- **Database:**
  - New migration with the tables `vacationrental.booking_sources` and `vacationrental.booking_field_sources`.
  - Existing data is untouched. The backfill only adds rows.
- **Frontend:**
  - New routes `buchungen/:bookingId` and `abrechnungen/:statementId`.
  - The booking form is extracted from `BookingDialog` into a component shared by the page and the new-booking dialog.
  - New components: source panel with highlighting, booking header, and statement page.
  - The booking list, calendar, inbox action and `AbrechnungenPage` navigate to the new pages.
- **Tests:**
  - Backend tests for locations per parser, the AI quote matching, the import, proposals and the backfill.
  - Frontend tests for the pages.
  - The e2e tests `mail-import` and `booking-calendar` change from the dialog to the page.
