# Design

## Context

- `vacationrental.bookings` has `agency VARCHAR(64)` (free text) and `channel VARCHAR(16)` with a check for `AIRBNB`, `BOOKING_COM`, `DIRECT`, `OTHER`. The unique index `bookings_external_ref_uq` is on `(unit_id, lower(coalesce(agency, '')), lower(external_ref))`.
- `vacationrental.unit_mappings` has `agency VARCHAR(64) NOT NULL` and the unique index `unit_mappings_uq` on `(owner_user_id, agency, lower(property_name))`.
- `ImportEngine`, `BookingRepository.findByRef`/`refTaken` and `UnitMappingRepository.find` compare agencies as lower-cased text. `BookingService.adoptAgency` copies the mail's agency onto a manual booking.
- The parsers produce agency names: "Fewo & Meer", "Holidu", or the intermediary name from DS mails (change `intermediary-booking-mails`).
- The channel is only set by hand in `BookingDialog` and shown in `BuchungenPage`.
- Real data is in use. This change restructures two tables, so the migration must carry every row over.

## Goals / Non-Goals

**Goals:**
- One shared list of agencies with stable identities, so renaming and merging never break mail matching, unique booking numbers or unit mappings.
- A migration that keeps every existing value.

**Non-Goals:**
- Linking the free-text provider of rental statements to agencies.
- Moving the parser rule "intermediary mentions Holidu → Holidu" into mail names. The parser keeps it, and mail names do the rest.
- Per-user agency lists or per-agency permissions.
- Dropping the old text columns. That can happen in a later cleanup once the new model has proven itself.

## Decisions

### D1: Tables

```sql
CREATE TABLE vacationrental.agencies (
    id         BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name       VARCHAR(64) NOT NULL,
    archived   BOOLEAN     NOT NULL DEFAULT FALSE,
    version    BIGINT      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX agencies_name_uq ON vacationrental.agencies (lower(name));

CREATE TABLE vacationrental.agency_aliases (
    id        BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    agency_id BIGINT       NOT NULL REFERENCES vacationrental.agencies (id) ON DELETE CASCADE,
    alias     VARCHAR(255) NOT NULL
);
CREATE UNIQUE INDEX agency_aliases_uq ON vacationrental.agency_aliases (lower(alias));
```

- The agency's current name is always stored as an alias row too. Resolving a mail name is then a single lookup, and renaming only adds the new name as an alias. The alias that equals the current name cannot be removed.
- `bookings.agency_id BIGINT REFERENCES vacationrental.agencies (id)`, nullable.
- `unit_mappings.agency_id BIGINT NOT NULL REFERENCES vacationrental.agencies (id)` once filled.
- New unique indexes replace the text-based ones: `(unit_id, coalesce(agency_id, 0), lower(external_ref))` for bookings, `(owner_user_id, agency_id, lower(property_name))` for unit mappings.
- The old columns `bookings.agency`, `bookings.channel` and `unit_mappings.agency` stay but are no longer mapped. `unit_mappings.agency` loses its `NOT NULL`, so new rows can omit it.

- Alternative: keep agency as text and add a separate table for display names. Rejected, because renaming would still need to rewrite every booking and mapping, and the identity problems with unique numbers would remain.

### D2: Data migration

In one Flyway migration, after `V20261109.1200`. The version is `V20261110.1100`, so it sorts before `owner-blocks`' planned `V20261110.1200`.

1. Insert one agency per distinct, trimmed, non-empty name from `bookings.agency` and `unit_mappings.agency`, grouped case-insensitively. Within a group, the spelling used by the most bookings wins.
2. Insert every distinct original spelling as an alias of its agency. Spellings that differ only in case collapse into one alias.
3. For bookings with `agency IS NULL AND channel IS NOT NULL`, ensure the agencies „Airbnb“, „Booking.com“, „Direkt“ and „Sonstige“ exist, matched case-insensitively against step 1, and use them.
4. Fill `bookings.agency_id` and `unit_mappings.agency_id` by case-insensitive name. If a booking has both an agency and a channel, the agency wins. The channel value stays in its old column.
5. Swap the unique indexes, then set `unit_mappings.agency_id NOT NULL`.

A migration test seeds the old schema with mixed-case names, a channel-only booking, a booking with both, and unit mappings, then checks that every row references the expected agency.

### D3: Placement and access

Agencies live in the module `vacationrental`: entity `Agency`, `AgencyRepository`, `AgencyService` and `AgencyController` under `/api/vacationrental/agencies`. The controller is conditional on the module like the others. Every signed-in user may read and change the list. Agencies are reference data, not domain data of a legal entity, so the sharing model does not apply. The booking count per agency is computed over the bookings visible to the user.

Concurrent edits are detected with `version` (optimistic locking), like costs.

### D4: Resolving mail names

`AgencyService.resolve(String name)` looks up the alias case-insensitively. If nothing matches, it creates the agency (name truncated to 64 characters) with the full name as alias. A unique-index violation from a concurrent import is caught and resolved by looking up again. `ImportEngine` calls `resolve` once per mail and works with the agency id from then on: in `findByRef`, `adoptAgency`, unit mapping lookup, creation and assignment of units (`BookingMailController`).

### D5: Merge and delete

`merge(sourceId, targetId)` runs in one transaction:
1. Reject if a booking of the source and one of the target share unit and booking number (case-insensitive).
2. For unit mappings with the same owner and property name in both agencies, keep the target's mapping and delete the source's. The target's mapping is newer knowledge and cannot conflict with the source's bookings.
3. Move bookings, the remaining unit mappings and aliases to the target, then delete the source.

`delete` checks for bookings and unit mappings first. `ON DELETE CASCADE` only covers aliases.

### D6: API and frontend

- The booking request carries `agencyId` instead of `agency` and `channel`. The response carries `agency: { id, name, archived } | null`. `Channel` and `channelLabel` are removed.
- The booking list filter takes `agencyId`. Sorting by agency sorts by name.
- `BookingDialog` gets an agency combobox built from `command.tsx` and `popover.tsx`. It lists active agencies, offers „„<Eingabe>“ anlegen“ when nothing matches, and shows an archived agency of the booking marked „archiviert“.
- New page „Vermittler“ in the module navigation: list, create, rename, archive or restore, edit mail names, merge (choose the target), delete.
- The inbox column „Quelle“ keeps showing the mail's agency name as read from the mail.

## Risks / Trade-offs

- [The structural migration fails on unexpected data, e.g. names over 64 characters in `unit_mappings`] → `unit_mappings.agency` is already `VARCHAR(64)`, and the migration test covers mixed case and empty values. Flyway runs the migration in one transaction on PostgreSQL, so a failure leaves the old schema intact.
- [Automatic creation fills the list with variants such as „FEWO &  MEER e.K.“] → Merging turns them into mail names of the right agency in one step, after which they never appear again.
- [Anyone can rename or merge shared agencies] → Accepted for a small group of users. Merging moves data but deletes nothing except the duplicate agency.
- [Conflict with `owner-blocks`, which also changes `bookings` and the booking dialog] → This change goes first. `owner-blocks` gets a migration version after `V20261110.1100` and builds on the new dialog.
- [Rollback] → The old columns still hold the pre-migration values, apart from new rows written afterwards. A rollback of the application would therefore show the old agencies of existing bookings, but not those of bookings created after the update.

## Migration Plan

1. Commit and deploy `intermediary-booking-mails` first.
2. Deploy this change: Flyway runs `V20261110.1100__vacationrental_agencies.sql` on start.
3. After deployment, check the page „Vermittler“, rename the long VVV name and merge any variants.
