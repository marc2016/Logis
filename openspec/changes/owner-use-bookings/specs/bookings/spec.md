# Spec Delta

## ADDED Requirements

### Requirement: Owner use
A booking SHALL be either a guest booking or an owner use („Eigenbelegung“), and existing bookings SHALL be guest bookings. An owner use SHALL have a unit, arrival and departure, and optionally an external booking number and agency; it MUST NOT carry adults, children, pets, guest data or an expected rent, and it MUST NEVER be „zu prüfen“. The same rules as for guest bookings SHALL apply to who may record, edit, cancel and delete it, to overlaps and to unique booking numbers. When recording a booking, the user SHALL choose „Gastbuchung“ or „Eigenbelegung“, with „Gastbuchung“ preselected. The booking list SHALL show the kind and SHALL be filterable by it, and the booking page of an owner use MUST NOT show guest, guest-count or rent fields.

#### Scenario: Record an owner use by hand
- **WHEN** Anna records an „Eigenbelegung“ on "Haus Seeufer" from 2027-06-09 to 2027-06-14
- **THEN** the owner use is saved with 5 nights and without guests or rent

#### Scenario: Owner use blocks the period
- **WHEN** an owner use runs from 2027-06-09 to 2027-06-14 and Anna records a guest booking from 2027-06-12 to 2027-06-15 on the same unit
- **THEN** the system rejects this with the message „Die Einheit ist in diesem Zeitraum bereits belegt“

#### Scenario: Filter by kind
- **WHEN** Anna filters the booking list by „Eigenbelegung“
- **THEN** only owner uses are listed

#### Scenario: Existing bookings
- **WHEN** the change is installed on a system with recorded bookings
- **THEN** all of them are guest bookings and otherwise unchanged
