# Design

## Context

`vacationrental` is a placeholder module today: `VacationRentalConfiguration` (module descriptor, `@ConditionalOnLogisModule`), a status controller, an empty migration folder `db/migration/vacationrental` (Flyway already manages the schema `vacationrental`), and a frontend module `src/modules/vacationrental` with the page `BelegungPage`.

From `core.realestate`, modules may use only the named interface `realestate`, and today that is only `UnitAccess` (`roleOn(unitId, userId)`, `units(userId, minimum)`). The core already has a "who still uses this?" pattern: `LegalEntityUsage.blockingReason(id)`, implemented in `core.realestate`. `PropertyService.delete` checks only transactions.

The motivation is in proposal.md, and the requirements are in `specs/`.

## Goals / Non-Goals

**Goals:**
- The module depends only on `core.realestate` (named interface) and `core.sharing` (`ShareRole`). The core does not know about bookings.
- Guest contact data is removed on the server for `VIEWER`s, never only hidden in the UI.
- Figures are computed in one place (backend) with exact decimal arithmetic.

**Non-Goals:**
- Linking statements or bookings to transactions (comes with `core-bank-import`).
- Importing booking confirmations from email (later, AI inbox).
- Tourist tax, VAT, registration forms, invoices to guests.
- Module categories (no transactions are created here).

## Decisions

### D1: Package layout and core extensions

```
app.logis.modules.vacationrental
  Booking, BookingRepository, BookingService, BookingController      /api/vacationrental/bookings
  BookingDocument, BookingDocumentRepository, BookingDocumentService,
  BookingDocumentController                                           /api/vacationrental/documents
  Statement, StatementLine, StatementRepository, StatementService,
  StatementController                                                 /api/vacationrental/statements
  OccupancyService, OccupancyController                               /api/vacationrental/occupancy
  BookingUnitReferences  implements core.realestate.UnitReferences

app.logis.core.realestate          (named interface "realestate")
  + UnitReferences   public interface: String blockingReason(Collection<Long> unitIds)
  + UnitAccess.summaries(Collection<Long> unitIds) -> Map<Long, UnitSummary>
  + UnitSummary      record(id, name, propertyId, propertyName, usage, archived)
```

- `PropertyService.delete` asks all `UnitReferences` beans after the transaction check and throws the first reason as a 409. `BookingUnitReferences` returns „Zum Objekt gibt es noch Buchungen und es kann nur archiviert werden“. This mirrors `LegalEntityUsage`. The interface takes unit IDs, so the core passes all units of the property in one call.
- `UnitSummary` gives the module names, the property for grouping in the calendar, and the usage and archive checks for new bookings. The module never reads `core.units` directly.
- *Alternative:* a database foreign key from `vacationrental.bookings` to `core.units`. It was rejected because a module schema would then constrain core deletes with a raw SQL error instead of a message, and because migrations of disabled modules would still couple the schemas. The module stores `unit_id` without a foreign key, and the delete check covers integrity.

### D2: Data model (schema `vacationrental`)

```
bookings
  id, unit_id BIGINT NOT NULL, arrival DATE, departure DATE  CHECK (departure > arrival)
  external_ref VARCHAR(64) NULL, channel (AIRBNB|BOOKING_COM|DIRECT|OTHER) NULL
  adults SMALLINT >= 1, children SMALLINT >= 0, expected_rent NUMERIC(12,2) NULL
  cancelled BOOLEAN, guest_deleted BOOLEAN
  guest_name, guest_street, guest_postal_code, guest_city, guest_country, guest_phone, guest_email  (all NULL)
  version, created_at
  UNIQUE (unit_id, lower(external_ref))
  EXCLUDE USING gist (unit_id WITH =, daterange(arrival, departure) WITH &&) WHERE (NOT cancelled)

statements
  id, unit_id BIGINT NOT NULL, provider VARCHAR(128), period_from DATE, period_to DATE,
  statement_date DATE, version, created_at

statement_lines
  booking_id PK -> bookings (one statement per booking), statement_id -> statements ON DELETE CASCADE,
  gross NUMERIC(12,2), commission NUMERIC(12,2) >= 0, cleaning NUMERIC(12,2) >= 0

booking_documents
  id, booking_id -> bookings ON DELETE CASCADE, file_name VARCHAR(255), content_type VARCHAR(64),
  size_bytes INTEGER, content BYTEA, created_at
```

- **Status is derived, not stored:** `CANCELLED` if `cancelled`, else `SETTLED` if a line exists, else `PLANNED`. This avoids a status column that can disagree with the lines.
- **Overlap** is enforced by the exclusion constraint (needs `btree_gist`, created in the migration) and checked in the service first so that the user gets the German message. The constraint catches races. `daterange(arrival, departure)` is half-open, which gives the changeover rule for free.
- **Net and payout** are computed (`gross − commission − cleaning`, sum per statement) and not stored.
- **Same unit** for line and statement is checked in the service, because `bookings.unit_id` and `statements.unit_id` are in different rows.
- **Guest data deletion** sets all `guest_*` fields to `NULL` and `guest_deleted = true` in one update, so the UI can show „Gastdaten gelöscht“ instead of an empty guest.
- **Documents in the database (`BYTEA`):** The nightly `pg_dump` backup then covers them without a second volume or backup path. Booking lists never select `content`; a separate `BookingDocumentRepository` loads it only for downloads. The type is checked by content type and file signature (magic bytes), not by the file name alone. The size limit is enforced in the service, and `spring.servlet.multipart.max-file-size` is set to `10MB`.
  - *Alternative:* files on a volume (`/data/documents`). It was rejected because it needs a new volume in `compose.yml`, a second backup path, and consistency handling between files and rows. That becomes worth it only with much larger amounts of data.
- *Alternative:* a separate `guests` table for returning guests. It was rejected because there is no requirement to reuse guests yet, and a separate table makes per-booking deletion harder.

### D3: Access checks

Every service method starts from the unit: `UnitAccess.roleOn(unitId, user)` gives 404 if empty and 403 if below `EDITOR` for writes. Lists use `UnitAccess.units(user, VIEWER)` as an `IN` filter. Each DTO takes the role of its row's unit: below `EDITOR`, the mapper sets `guest_street … guest_email` to `null` (the name stays) and omits `documents`. The same mapper serves bookings inside statement lines, so there is no second path that could leak. Document endpoints require `EDITOR` and answer 404 below it, so a `VIEWER` cannot tell whether a document exists. Text search adds the contact fields only for rows of units with `EDITOR`. In the current model it only searches the booking number and guest name, so there is nothing to restrict yet, but a test pins the rule.

### D4: API

- `GET /api/vacationrental/bookings?unitId=&propertyId=&from=&to=&status=&q=&sort=arrival,desc&page=` and `POST`, `PUT /{id}`, `DELETE /{id}`, `POST /{id}/cancel|reinstate`, `DELETE /{id}/guest`. Sort keys are whitelisted, and the page size is at most 100, as for transactions.
- `POST /api/vacationrental/bookings/{id}/documents` (multipart, one file per request), `GET /api/vacationrental/documents/{docId}` (download with `Content-Disposition: attachment`), `DELETE /api/vacationrental/documents/{docId}`
- `GET /api/vacationrental/units`: vacation rental units visible to the user with `myRole`, for pickers and the calendar
- `GET /api/vacationrental/statements?unitId=`, `GET /{id}` (with lines), `POST`, `PUT /{id}`, `DELETE /{id}`, `PUT /{id}/lines/{bookingId}` (assign or update), `DELETE /{id}/lines/{bookingId}`, `GET /providers?unitId=` (distinct names, most recent first)
- `GET /api/vacationrental/occupancy/calendar?propertyId=&month=2026-11`
- `GET /api/vacationrental/occupancy/overview?unitId=&year=2026`

Booking DTOs carry `status`, `nights`, `statementId` and `canEdit`.

### D5: Figures

`OccupancyService` loads the year's non-cancelled bookings of the unit, plus the cancelled bookings that have lines, with their lines in one query. It distributes per night in Java with `BigDecimal`:

```
perNight = amount / nights  (scale 2, HALF_UP); last night = amount - perNight * (nights - 1)
month.nights      += nights in month (not cancelled)
month.settledNet  += net share   (bookings with a line, not cancelled)
month.plannedRent += rent share  (planned bookings with expected_rent)
cancelled with line: whole net -> month of arrival
occupancy = nights / daysInMonth, shown as a whole percentage
```

Stays that reach into the year from December or out of it into January are clipped, but their shares are still computed over the whole stay.

### D6: Frontend

```
src/modules/vacationrental/
  api.ts                      types + query hooks
  pages/BuchungenPage.tsx     /buchungen   grid: filter bar (Objekt, Einheit, Status, Zeitraum, Suche),
                                           sortable column headers, paging; "+ Buchung" top right
  pages/KalenderPage.tsx      /kalender    property picker, month navigation, CalendarGrid;
                                           "+ Buchung" top right; click on a bar opens the booking
  pages/AbrechnungenPage.tsx  statements per unit; detail with lines, payout, "Buchung zuordnen"
  pages/AuswertungPage.tsx    unit + year picker, 12-month table with totals
  BookingDialog.tsx           shared modal for create/edit: stay, guests, amounts, guest data, documents
  DocumentList.tsx, StatementDialog.tsx, StatementLineDialog.tsx, CalendarGrid.tsx
```

- Nav items: „Buchungen“, „Kalender“, „Abrechnungen“, „Auswertung“. The module index redirects to „Buchungen“. The placeholder `BelegungPage` is removed.
- **Grid:** the existing shadcn `table` with clickable column headers (`sort` in the URL query, so filters and sorting survive a reload and can be shared as links). A row click opens `BookingDialog` in edit mode. No new table library is needed for one sortable list.
- **Add button:** `PageHeader` action at the top right on both pages. Both open the same `BookingDialog`. In the calendar, the current property is preselected. After saving, the query cache of bookings and calendar is invalidated.
- **Documents in the dialog:** On create, files are kept in the dialog and uploaded right after the booking is saved. On edit, they are uploaded immediately. A failed upload shows an error on that file without losing the booking.
- Status labels: „Geplant“, „Abgerechnet“, „Storniert“. Channel labels: „Airbnb“, „Booking.com“, „Direkt“, „Sonstige“.
- The calendar is a simple CSS grid (days as columns), with no new dependency. Planned bars are outlined, settled bars filled, and cancelled bookings are not shown.
- The guest section of `BookingDialog` is shown only when `canEdit`. „Gastdaten löschen“ asks for confirmation.

### D7: Tests

- **Backend:** integration tests per spec file with the realestate permission matrix (Anna OWNER, Carla unit-only EDITOR, Dieter property VIEWER). They cover: overlap and changeover, the exclusion constraint against a direct insert, booking number uniqueness, usage check, guest fields `null` for Dieter in bookings and statement lines, guest deletion keeping documents, document type/size/signature checks and 404 for Dieter, list filters and every sort key, assign/remove/delete statement effects on status, net and payout arithmetic, cancellation payout, night split and the yearly overview including the year boundary, property delete blocked by bookings, module disabled → 404. `ModularityTest` covers the new dependency on `realestate`.
- **Frontend:** component tests for the calendar row filtering and bar styles, `BookingDialog` without guest fields for a viewer, the statement payout display, and the overview table.

## Risks / Trade-offs

- [No foreign key from bookings to units] → The delete check in `PropertyService` is the only guard. Units themselves are never deleted except with their property, and that path is covered by `UnitReferences` and a test.
- [Guest contact data without automatic deletion] → This was decided by the user. Deletion is one click per booking, and data minimisation can be added later as a scheduled job without a model change (`guest_deleted` already exists).
- [`btree_gist` extension] → The migration runs `CREATE EXTENSION IF NOT EXISTS btree_gist`. This needs a role that is allowed to create the extension, which the app user is in the dev and prod compose setups. On failure, Flyway stops at startup with a clear error.
- [Documents keep guest data after „Gastdaten löschen“] → The user decided this. The dialog shows a hint after deleting guest data when documents remain, so the user can delete them too.
- [Database grows with files] → At a few documents of about 1 MB per booking, this stays in the range of hundreds of MB per year. The backup size grows the same way. Moving to a volume later only changes `BookingDocumentRepository` and a migration.
- [Rounding in the night split] → The last-night remainder keeps the yearly sums exact. Monthly values can differ by a cent from a manual calculation, which is acceptable for an overview.

## Migration Plan

The first migration in `db/migration/vacationrental` (`V20261107.1200__vacationrental_bookings.sql`) creates `btree_gist`, `bookings`, `statements`, `statement_lines` and `booking_documents`. The reverse proxy in front of Logis must accept request bodies of 10 MB. The README section on deployment mentions this (e.g. `client_max_body_size` for nginx). No existing data is touched. Rollback works through a backup taken before the update (README: Restore).

## Open Questions

- Should statements also carry deductions that do not belong to a booking (e.g. maintenance)? This can be added as statement-level items later without changing booking lines.
