## ADDED Requirements

### Requirement: Compact view
The booking page SHALL offer the views „Kompakt“ and „Alle Angaben“ for users who may edit the booking.
- „Kompakt“ SHALL show only arrival, departure, adults, children and the expected rent; for an owner use only arrival and departure.
- A field hidden in „Kompakt“ MUST still be shown while its value differs from its document or it has an error.
- The default SHALL be „Alle Angaben“.

The chosen view SHALL be remembered as a setting of the user and SHALL apply to every booking, also in the review mode. The dialog for recording a new booking MUST always show all fields.

#### Scenario: Switch to compact
- **WHEN** Anna opens a booking and chooses „Kompakt“
- **THEN** only Anreise, Abreise, Erwachsene, Kinder and Mietpreis are shown with „Speichern“, and the header still shows guest, unit, agency and booking number

#### Scenario: Remembered
- **WHEN** Anna chose „Kompakt“ and opens another booking
- **THEN** it opens in „Kompakt“

#### Scenario: Differing field stays visible
- **WHEN** a booking opens in „Kompakt“ and its guest name differs from its document
- **THEN** „Name“ is shown with the hint that it differs from the document

#### Scenario: Owner use
- **WHEN** Anna opens an owner use in „Kompakt“
- **THEN** only Anreise and Abreise are shown

#### Scenario: New booking
- **WHEN** Anna records a new booking while her view is „Kompakt“
- **THEN** the dialog shows all fields
