# Spec Delta

## MODIFIED Requirements

### Requirement: Unique booking number per unit
An external booking number, if given, MUST be unique among all bookings of a unit with the same agency, including cancelled ones. Bookings without agency count as one agency.

#### Scenario: Same confirmation entered twice
- **WHEN** Anna records booking number "250905" a second time on the same unit
- **THEN** the system rejects this with the message „Diese Buchungsnummer ist bereits erfasst“

#### Scenario: Same number at two agencies
- **WHEN** a booking "1234" of Fewo & Meer exists and a booking "1234" of Holidu is recorded on the same unit
- **THEN** both bookings exist

## ADDED Requirements

### Requirement: Agency and pets
A booking SHALL optionally carry the agency it came from (free text, e.g. "Fewo & Meer") and the number of pets. Both SHALL be shown in the booking dialog, and the agency SHALL be a filterable and sortable column of the booking list.

#### Scenario: Agency from an imported mail
- **WHEN** a booking is created from a Holidu mail
- **THEN** its agency is "Holidu"

### Requirement: Bookings awaiting review
A booking created from a mail SHALL have the review state „zu prüfen“ until a user with at least `EDITOR` confirms or discards it. It MUST occupy the calendar like a planned booking and MUST be marked in the booking list, the calendar and the dialog. Confirming SHALL make it an ordinary booking, possibly after editing. Discarding SHALL delete it together with its documents and mark the mail in the inbox as discarded.

#### Scenario: Confirm an imported booking
- **WHEN** Anna opens the booking 250903 „zu prüfen“, corrects the guest name and confirms
- **THEN** the booking is an ordinary planned booking without the marker

#### Scenario: Filter bookings to review
- **WHEN** Anna filters the booking list by „zu prüfen“
- **THEN** they see only bookings and changes awaiting review

### Requirement: Proposed changes
A booking SHALL carry at most one proposed change from a mail: new values for period, guests, pets, expected rent and guest data, or a cancellation. The dialog MUST show old and new values. Applying SHALL take over the values or cancel the booking, subject to the usual rules such as the overlap check. Discarding SHALL keep the booking unchanged. A newer proposal MUST replace an older one.

#### Scenario: Apply a cancellation
- **WHEN** Anna applies the proposed cancellation of booking 250905
- **THEN** the booking is cancelled and the calendar period is free

#### Scenario: Change would overlap
- **WHEN** a proposed change moves a booking onto an occupied period and Anna applies it
- **THEN** the system rejects this with the message „Die Einheit ist in diesem Zeitraum bereits belegt“ and the proposal stays
