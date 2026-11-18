# Design

## Context

- `vacationrental.bookings` holds guest bookings with `adults`/`children`/`pets` (NOT NULL), optional `expected_rent`, an optional guest (`Guest` embeddable) and a review state. Overlap checks, the occupancy calendar, the yearly overview, the calendar feed and statements all read this table.
- `FewoUndMeerParser` recognises confirmations, short-notice notices, cancellations and block notices by subject; `ImportEngine` maps the property name of an agency to a unit and decides the effect.

## Goals / Non-Goals

**Goals:**
- One table for everything that occupies a unit, so overlap rules, calendar and feed cover owner uses without new queries.
- Import of the Fewo & Meer owner block notice without review.

**Non-Goals:**
- A mail that lifts an owner block (its format is unknown); an owner use is cancelled or deleted by hand.
- Owner uses from other agencies or the AI fallback (the AI keeps its current types).
- Costs of an owner use (e.g. cleaning) in statements or finances.

## Decisions

### D1: `kind` column on bookings
Add `kind VARCHAR(10) NOT NULL DEFAULT 'GUEST'` with a check `kind IN ('GUEST', 'OWNER')` in a new migration; every existing row becomes `GUEST` through the default, nothing else changes. `Booking` gets an enum `BookingKind { GUEST, OWNER }`. For owner uses the service sets `adults`, `children` and `pets` to 0, clears guest and expected rent and keeps review state `NONE`; input fields for them are ignored for `OWNER`.

*Alternative:* a separate `owner_uses` table. Rejected: every reader of occupancy (overlap check, calendar, feed, overview) would need a second query and a union.

### D2: Overview and statements exclude by kind
`OccupancyService` counts `OWNER` nights into a separate `ownerNights` per month and leaves them out of booked nights and planned rent. Statement assignment rejects `OWNER` with „Eine Eigenbelegung wird nicht abgerechnet“, and the open-bookings query of a statement filters to `GUEST`.

### D3: Mail type `OWNER_BLOCK`
`ParsedMail.MailType.OWNER_BLOCK`, recognised by the subject `^Eigentümerblockung (\S+), (.+?), vom DATE bis DATE`. `ImportEngine` handles it before the generic switch: find the unit via the agency's property mapping (unknown → `UNASSIGNED_UNIT`, as today); an existing booking with agency and number → attach the mail and, if the period differs, update the period directly (overlap → `PERIOD_TAKEN`); otherwise create an owner use (overlap → `PERIOD_TAKEN`) with the mail attached. New result `OWNER_USE_CREATED` labelled „Eigenbelegung angelegt“; an update reuses `ATTACHED`.

### D4: Frontend
The booking form gets a toggle „Gastbuchung | Eigenbelegung“ that hides guest count, guest, pets and rent for owner use. The booking list gets an „Art“ column and filter (`kind` query param, backend filter). Calendar bars of owner uses get their own style and the label „Eigenbelegung“; the yearly overview gets an „Eigenbelegung“ column with nights. The feed titles owner uses „Eigenbelegung“.

## Risks / Trade-offs

- [Code that assumes every booking has guests or rent] → Search all readers of `adults`, `expectedRent` and `guest`; tests cover list, page, statement and overview with an owner use.
- [Default on a large table] → Adding a column with a constant default is a metadata-only change in PostgreSQL; no table rewrite.

## Migration Plan

Deploy backend and frontend together. The migration only adds the column with a default and a check constraint; rollback is dropping the column, which loses only owner uses recorded since.
