# Spec Delta

## Purpose

Bookings record who stays in a vacation rental unit and when, from the service provider's booking confirmation until the booking is settled on a statement or cancelled. They are the basis for the occupancy calendar and the income per unit.

## ADDED Requirements

### Requirement: Record a booking
A user with at least `EDITOR` as effective role on a unit SHALL be able to record a booking on it with arrival, departure, adults, and optionally children, external booking number, channel and expected rent. Only active units with usage `VACATION_RENTAL` MUST accept new bookings. Departure MUST be after arrival. A new booking SHALL have the status planned.

#### Scenario: Booking from the provider's confirmation
- **WHEN** Anna records booking number "250905" on "Haus Seeufer" from 2026-11-06 to 2026-11-08 for 4 adults with an expected rent of 248.58
- **THEN** the booking appears as planned with 2 nights

#### Scenario: Unit not used for vacation rental
- **WHEN** Anna tries to record a booking on a unit with usage `LONG_TERM_RENTAL`
- **THEN** the system rejects this with the message „Die Einheit wird nicht als Ferienwohnung genutzt“

#### Scenario: Viewer may not record
- **WHEN** Dieter, as `VIEWER` of the property, tries to record a booking on one of its units
- **THEN** the system responds with HTTP 403

### Requirement: No overlapping bookings
Two bookings of the same unit that are not cancelled MUST NOT overlap. A stay SHALL occupy the nights from arrival up to the night before departure, so an arrival on another booking's departure day MUST be allowed.

#### Scenario: Overlap
- **WHEN** a booking exists from 2026-11-06 to 2026-11-08 and Anna records another one from 2026-11-07 to 2026-11-10 on the same unit
- **THEN** the system rejects this with the message „Die Einheit ist in diesem Zeitraum bereits belegt“

#### Scenario: Changeover day
- **WHEN** a booking ends on 2026-11-08 and Anna records one arriving on 2026-11-08
- **THEN** the booking is saved

### Requirement: Unique booking number per unit
An external booking number, if given, MUST be unique among all bookings of a unit, including cancelled ones.

#### Scenario: Same confirmation entered twice
- **WHEN** Anna records booking number "250905" a second time on the same unit
- **THEN** the system rejects this with the message „Diese Buchungsnummer ist bereits erfasst“

### Requirement: Edit, cancel and delete a booking
A user with at least `EDITOR` on the unit SHALL be able to edit a booking, cancel it and delete it. A cancelled booking MUST keep its data and MUST NOT occupy the calendar. A cancellation SHALL be undoable only if the period is still free. A booking that is assigned to a statement MUST NOT be deleted.

#### Scenario: Cancel
- **WHEN** Anna cancels the booking from 2026-11-06 to 2026-11-08
- **THEN** the booking shows the status cancelled, and a new booking for that period can be recorded

#### Scenario: Delete a settled booking
- **WHEN** Anna tries to delete a booking that is assigned to a statement
- **THEN** the system rejects this with the message „Die Buchung ist abgerechnet und kann nur storniert werden“

### Requirement: Guest data
A booking SHALL hold the guest's name and optionally address, phone and email. Users with `VIEWER` as effective role on the unit MUST see the guest name but MUST NOT receive address, phone or email from the system. Users with at least `EDITOR` SHALL see all guest data.

#### Scenario: Viewer reads a booking
- **WHEN** Dieter, as `VIEWER` of the property, opens a booking of one of its units
- **THEN** they see period, guests, guest name and amounts, but no address, phone or email

### Requirement: Delete guest data
A user with at least `EDITOR` on the unit SHALL be able to delete the guest data of a booking. This MUST remove the guest name and all contact details permanently and MUST keep the booking with its period, guest counts, booking number and amounts. Guest data MUST NOT be deleted automatically.

#### Scenario: Delete guest data
- **WHEN** Anna deletes the guest data of a past booking
- **THEN** the booking stays in the calendar and on its statement, and shows „Gastdaten gelöscht“ instead of the guest

### Requirement: Booking documents
A user with at least `EDITOR` on the unit SHALL be able to attach documents to a booking, download them and delete them one by one. Only PDF, JPEG, PNG, WebP and HEIC files of at most 10 MB MUST be accepted. Documents MUST be visible only to users with at least `EDITOR`. Deleting the guest data MUST NOT delete documents. Deleting the booking MUST delete them.

#### Scenario: Attach the confirmation
- **WHEN** Anna attaches a screenshot of the provider's confirmation to booking "250905"
- **THEN** the booking lists the document with its name, and Anna can download it

#### Scenario: Wrong file type or too large
- **WHEN** Anna tries to attach a 12 MB PDF or a Word file
- **THEN** the system rejects this with the message „Nur PDF- oder Bilddateien bis 10 MB“

#### Scenario: Viewer and documents
- **WHEN** Dieter, as `VIEWER` of the property, opens a booking with a document
- **THEN** they see no documents, and downloading one by its address returns HTTP 404

### Requirement: Booking list
The system SHALL list the bookings visible to the user, filterable by property, unit, status, period (stays that overlap it) and a search text in booking number and guest name. The list SHALL be sortable by arrival, departure, unit, guest, nights, expected rent and status, and sorted by arrival descending by default. Users with a role below `EDITOR` MUST NOT find bookings by text that only appears in contact details.

#### Scenario: Upcoming stays of one unit
- **WHEN** Anna filters by "Haus Seeufer" and status planned and sorts by arrival ascending
- **THEN** they see the planned bookings of that unit, earliest first

#### Scenario: Search by booking number
- **WHEN** Anna searches for "250905"
- **THEN** the list contains exactly that booking

### Requirement: Access through the unit
Bookings MUST be visible exactly to users with an effective role on their unit. A booking of a unit without such a role MUST be treated as nonexistent (HTTP 404).

#### Scenario: Unit-only editor
- **WHEN** Carla, as `EDITOR` of "Whg. 2" only, lists bookings
- **THEN** they see the bookings of "Whg. 2" and none of "Whg. 1"

#### Scenario: No access
- **WHEN** Bert retrieves a booking of a unit they have no role on
- **THEN** the system responds with HTTP 404
