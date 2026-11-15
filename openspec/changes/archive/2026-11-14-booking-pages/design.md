# Design

## Context

- **Bookings.** A booking is edited in `BookingDialog`, a modal opened from `BuchungenPage` (local state) and `KalenderPage`. It contains the whole form, `ReviewPanel` (confirm and discard, old and new values of a proposal) and `DocumentList`. `GET /api/vacationrental/bookings/{id}` already exists.
- **Mail import.** `MailContent` turns a mail into `subject` and a normalized `text`: HTML rows become lines, table cells are separated by tabs, blank runs are collapsed. Parsers match on `businessSubject()` (forward prefixes removed) and on `text`.
  - `MailRecognizer` tries the rule parsers (`FewoUndMeerParser`, `HoliduDsParser`) and then the optional `ClaudeAiExtractor`. Both produce a `ParsedMail` of plain values.
  - `ImportEngine` creates, completes (review state `NEW`), proposes or cancels, and stores the raw mail as a `message/rfc822` document through `Documents.store`, which returns the document id.
- **Proposals.** A proposal is embedded in `Booking` (`BookingProposal`) and replaced by a newer one.
- **Statements.** The statement details are rendered below the list in `AbrechnungenPage`, selected by `?statement=<id>`. Statements have no documents.
- **Documents.** Documents live in `core.documents`, owned by `(owner_type, owner_id)`. Deleting a single document goes through `DocumentService.delete`, with no event.

## Goals / Non-Goals

**Goals:**
- Exact highlights for imported values, independent of later changes to the mail-to-text conversion.
- A source model that can take PDF sources later without migrating existing rows.
- One booking form used by the page and the new-booking dialog.

**Non-Goals:**
- PDF preview, PDF locations or marking values in a document to fill a field (IDEAS.md).
- Rendering the original HTML of a mail. The panel shows the text the parser read.
- Re-deriving locations when parsers improve later. The backfill runs once per mail document.
- A warning about unsaved changes when leaving the booking page.
- Sources for statements.

## Decisions

### D1. Snapshot the text the parser read
A source stores `subject` (the `businessSubject()`), `sender`, `received_at` and `text`, exactly as `MailContent` produced them for recognition. Locations are offsets into these strings.
- *Alternative:* re-render the text from the `.eml` on every view. Rejected because any change to `MailContent.normalize` or `htmlToText` would silently shift every stored offset.
- Offsets are UTF-16 code-unit indexes, the same for Java `String` and JavaScript strings, so the frontend can slice the text directly.

### D2. Tables
New migration `V20261114.1200__vacationrental_booking_sources.sql`:

```
booking_sources
  id            BIGINT identity PK
  booking_id    BIGINT NOT NULL -> bookings(id) ON DELETE CASCADE
  document_id   BIGINT NOT NULL UNIQUE        -- core.documents id, no cross-schema FK
  kind          VARCHAR(16) NOT NULL           -- 'MAIL' now, 'PDF' later
  label         VARCHAR(255) NOT NULL
  sender        VARCHAR(320)
  received_at   TIMESTAMPTZ
  subject       TEXT
  text          TEXT NOT NULL
  created_at    TIMESTAMPTZ NOT NULL DEFAULT now()

booking_field_sources
  booking_id    BIGINT NOT NULL -> bookings(id) ON DELETE CASCADE
  scope         VARCHAR(16) NOT NULL           -- 'CURRENT' | 'PROPOSAL'
  field         VARCHAR(32) NOT NULL
  source_id     BIGINT NOT NULL -> booking_sources(id) ON DELETE CASCADE
  part          VARCHAR(16) NOT NULL           -- 'SUBJECT' | 'TEXT' (later e.g. 'PAGE')
  start_pos     INT NOT NULL
  end_pos       INT NOT NULL
  source_value  TEXT                           -- normalized value read from the source
  PRIMARY KEY (booking_id, scope, field)
```

- `kind` and `part` are open strings, so a PDF source later adds a part such as `PAGE` with nullable columns for page and box, without touching existing rows.
- Proposal locations live in the same table under `scope = 'PROPOSAL'` instead of inside the embedded `BookingProposal`. Applying moves them to `CURRENT`, and discarding or replacing deletes them.
- No data migration is needed. Existing bookings are covered by the backfill (D8).

### D3. Field names
A `BookingField` enum names the fields once for backend and API: `externalRef`, `propertyName`, `arrival`, `departure`, `adults`, `children`, `pets`, `expectedRent`, `guestName`, `guestStreet`, `guestPostalCode`, `guestCity`, `guestCountry`, `guestPhone` and `guestEmail`. The frontend uses the same keys as `data-field` attributes on the form inputs. `propertyName` has no input; its location is shown on the unit field.

### D4. Parsers report locations
- `ParsedMail` gets `Map<BookingField, Location> locations`, with `Location(part, start, end)`. The existing constructors default it to an empty map.
- A small helper (`Reading`) wraps subject and text. It offers `find(field, pattern, part)` and returns the stripped `group(1)` like `GermanText.find`, recording the group's offsets with the stripped whitespace excluded.
- Parsers switch from `GermanText.find` to the helper. Fields read from one match share it: `adults` and `children` from „2 Erwachsene, 1 Kind“, and the period from a subject.
- Multi-line addresses (Fewo & Meer) and `street, PLZ city, country` lines (DS) track line start offsets, so each part gets its own location.
- `ParsedMail.usable` ignores locations.

### D5. AI quotes
- `AiExtraction` gets `List<Quote> quotes`, with `Quote(field, passage)` and the description "for each value, the exact passage of the mail it was read from".
- `MailRecognizer.toParsed` locates each passage, first in the text and then in the subject. The match is whitespace-tolerant: the passage is turned into a regex with `\s+` between tokens, and the first occurrence is taken.
- Passages not found give no location (spec: AI quote not found).
- *Alternative:* one `…Quote` property per field. Rejected because it doubles the schema.

### D6. Import stores sources and locations
- `ImportEngine.attach` returns the document id. A new `BookingSources` service (package-private, in `vacationrental`) creates the source from the `MailContent` and writes locations.
  - **create:** `CURRENT` locations for every located field.
  - **complete (`NEW`):** `CURRENT` locations for the fields taken from the mail.
  - **attached without differences:** add `CURRENT` locations only for fields without one.
  - **propose (change or cancellation):** replace `PROPOSAL` locations.
  - **block notice:** source only.
- `ImportEngine.apply` therefore receives the `MailContent` next to `ParsedMail` and `raw`.
- `source_value` is the normalized parsed value: ISO dates, plain decimals, stripped strings. This lets „differs“ be computed against the booking without parsing the source again.

### D7. Differs, proposals, guest data
- **Differs.** `differs` is computed when the booking view is built: the current value is compared with `source_value`, using the same comparisons `ImportEngine` already uses for proposals (`sameName`, `samePhone`, case-insensitive strings, `compareTo` for amounts). These move to a shared helper so the import and the view agree.
- **Proposals.** `BookingService.applyProposal` moves the `PROPOSAL` rows of the changed fields to `CURRENT`. `discardProposal` and `propose` (replace) delete the `PROPOSAL` rows.
- **Guest data.** `deleteGuest` deletes the rows of all `guest*` fields in both scopes. Sources stay, like documents.

### D8. Backfill for existing bookings
- A `BookingSourceBackfill` `ApplicationRunner` runs after Flyway on every start, before mail polling. It handles only bookings that have mail documents but no source.
- For each such booking, in its own transaction, it reads the `message/rfc822` documents oldest first through a new `Documents.contents(ownerType, ownerId, contentType)`. For each one it runs `MailContent.parse` and the rule parsers only (`MailRecognizer.recognizeByRules`), then creates the source.
- A later mail's locations replace earlier ones per field, which mirrors how the import applied newer values. Unrecognised mails are skipped. Failures are logged and leave that booking for the next start.
- Idempotent through `document_id UNIQUE` and the "no source yet" filter.
- *Alternative:* a Flyway Java migration. Rejected because it cannot use the parser beans and would freeze parser code into a migration.

### D9. Deleting a mail document
- `DocumentService.delete` publishes `DocumentDeleted(ownerType, ownerId, documentId)` within its transaction. `vacationrental` listens with a synchronous `@EventListener` and deletes the source by `document_id`, and the field rows go by cascade.
- Deleting the booking cascades through `booking_id`.
- *Alternative:* a cross-schema FK to `core.documents`. Rejected to keep module schemas independent.

### D10. API
- `GET /bookings/{id}`: for users with at least `EDITOR`, the view adds
  - `sources: [{id, kind, label, receivedAt}]`
  - `locations: {<field>: {sourceId, part, start, end, sourceValue, differs}}`
  - `proposalLocations: {<field>: {...}}`
- For other roles these are empty. The list endpoint does not carry them.
- `GET /bookings/{id}/sources/{sourceId}` returns `{subject, sender, receivedAt, text}`. It returns 404 below `EDITOR` or when the source belongs to another booking.

### D11. Frontend structure
- **Routes.** `buchungen/:bookingId` gets a new `BookingPage`, and `abrechnungen/:statementId` a new `StatementPage`. Both are lazy like the other pages.
- **`BookingForm`.** Extracted from `BookingDialog`: fields, validation, save. It is used by `BookingPage` (edit) and by `BookingDialog`, which shrinks to "new booking" only. After a successful create the dialog closes as today.
- **`BookingPage` layout.** `BookingHeader` (guest, period plus nights, unit, agency, rent, status, review marker and actions), then a two-column grid of `BookingForm` plus documents on the left and `SourcePanel` on the right (`lg:` and up). Below `lg` there are `Tabs` „Daten“ and „Quelle“.
- **Active field.** A `focusin` listener on the form reads the nearest `data-field`. If that field has a location, it becomes active and the `SourcePanel` switches to its source tab.
- **`SourcePanel`.**
  - It renders subject and text split into segments at the location boundaries of that source. Each segment with fields becomes a `<mark data-fields>`. The active one gets a stronger style and `scrollIntoView({block: 'center'})`.
  - Clicking a mark focuses the first matching input.
  - The source text is fetched per tab with TanStack Query.
- **Fields marked „weicht vom Dokument ab“** show the `sourceValue` in German formatting under the input.
- **`ReviewPanel` moves into the header area.** Its new-value cells become buttons that activate the proposal location.
- **Navigation.**
  - The list row and the calendar's `onOpen` call `navigate('/ferienvermietung/buchungen/<id>', {state: {back: <current url>}})`.
  - The page's back link uses `state.back`, falling back to the list. The browser back button works natively.
  - Discard and delete navigate to `state.back` or the list.
  - The inbox action „Zur Buchung“ navigates to the page.
- **Statements.** `AbrechnungenPage` keeps the list and redirects `?statement=<id>` to `abrechnungen/<id>` with `replace`. The detail block moves to `StatementPage` with a header, and lines and dialogs stay as they are.
- **Not found.** Both pages show „Buchung nicht gefunden“ or „Abrechnung nicht gefunden“ on a 404.

## Risks / Trade-offs

- [Snapshot text holds guest data in a second place] → Same visibility as the `.eml` (`EDITOR` only). It is deleted with the document or the booking. Guest field values in `source_value` are deleted with the guest data.
- [AI passages may be paraphrased] → Whitespace-tolerant search. A miss only means no highlight.
- [Two different fields with overlapping but unequal spans] → The segment split handles any overlap: a segment lists every field covering it.
- [Backfill on a large history slows startup] → Few bookings today. Per-booking transactions and logging keep a failure local. It could move to an async runner if needed.
- [Old links and e2e flows assume the dialog] → `?statement=` redirect. The e2e tests are updated in this change.
- [Rollback] → Only new tables. An older image ignores them, because Hibernate validates only mapped entities, so a rollback needs no restore.

## Migration Plan

1. Deploy: Flyway creates the two tables, and the backfill derives sources for existing imported bookings on first start.
2. Check one imported booking: source tab and highlights.
3. Rollback: start the previous image. The new tables stay unused.
