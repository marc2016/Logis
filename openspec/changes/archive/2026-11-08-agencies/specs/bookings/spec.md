# Spec Delta

## MODIFIED Requirements

### Requirement: Record a booking
A user with at least `EDITOR` as effective role on a unit SHALL be able to record a booking on it with arrival, departure, adults, and optionally children, external booking number, agency and expected rent. Only active units with usage `VACATION_RENTAL` MUST accept new bookings. Departure MUST be after arrival. A new booking SHALL have the status planned. A booking MUST NOT carry a channel.

#### Scenario: Booking from the provider's confirmation
- **WHEN** Anna records booking number "250905" on "Haus Seeufer" from 2026-11-06 to 2026-11-08 for 4 adults with an expected rent of 248.58
- **THEN** the booking appears as planned with 2 nights

#### Scenario: Unit not used for vacation rental
- **WHEN** Anna tries to record a booking on a unit with usage `LONG_TERM_RENTAL`
- **THEN** the system rejects this with the message „Die Einheit wird nicht als Ferienwohnung genutzt“

#### Scenario: Viewer may not record
- **WHEN** Dieter, as `VIEWER` of the property, tries to record a booking on one of its units
- **THEN** the system responds with HTTP 403

### Requirement: Unique booking number per unit
An external booking number, if given, MUST be unique among all bookings of a unit with the same agency, including cancelled ones. Bookings without agency count as one agency. Agencies are compared by identity, not by name, so renaming an agency MUST NOT change which numbers collide.

#### Scenario: Same confirmation entered twice
- **WHEN** Anna records booking number "250905" a second time on the same unit
- **THEN** the system rejects this with the message „Diese Buchungsnummer ist bereits erfasst“

#### Scenario: Same number at two agencies
- **WHEN** a booking "1234" of Fewo & Meer exists and a booking "1234" of Holidu is recorded on the same unit
- **THEN** both bookings exist

### Requirement: Agency and pets
A booking SHALL optionally reference an agency from the shared list of agencies, and SHALL carry the number of pets. Both SHALL be shown in the booking dialog. The dialog SHALL offer the active agencies for selection and SHALL allow creating a new agency from the dialog. A booking with an archived agency SHALL keep and show it. The agency SHALL be a filterable and sortable column „Vermittler“ of the booking list, and the list MUST NOT show a channel column.

#### Scenario: Agency from an imported mail
- **WHEN** a booking is created from a Holidu mail
- **THEN** its agency is "Holidu"

#### Scenario: Create an agency while recording a booking
- **WHEN** Anna types „Ferienbüro Nord“ into the agency field of the booking dialog, chooses to create it and saves the booking
- **THEN** the agency „Ferienbüro Nord“ exists in the shared list, and the booking references it

#### Scenario: Existing data after the update
- **WHEN** the system is updated with a booking that had the free-text agency „Fewo & Meer“ and one that had no agency but the channel `DIRECT`
- **THEN** the first references the agency „Fewo & Meer“ and the second the agency „Direkt“
