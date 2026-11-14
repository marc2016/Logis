## ADDED Requirements

### Requirement: Locations of read values
Recognition SHALL report, for every field it reads, where in the mail the value was found: in the subject or in the text, with start and end position in the text that was read. Values composed from one place, such as adults and children from „2 Erwachsene, 1 Kind“, SHALL share that location. For the AI fallback, the model SHALL return for each value the passage of the mail it read the value from. The system SHALL locate that passage in the mail. A value whose passage is not found MUST be kept without a location and MUST NOT be rejected for that reason.

#### Scenario: Rule result
- **WHEN** the Fewo & Meer confirmation with „Anreise ⇥ 23.12.2026“ is recognised
- **THEN** the arrival 2026-12-23 is reported with the location of „23.12.2026“ in the mail text

#### Scenario: Subject fields
- **WHEN** the Fewo & Meer cancellation "Stornierung 250905, Haus Seeufer - Nordhorn, vom 06.11.2026 bis zum 08.11.2026" is recognised
- **THEN** booking number, property name, arrival and departure are reported with their locations in the subject

#### Scenario: AI quote not found
- **WHEN** the AI returns a guest name with a passage that does not occur in the mail
- **THEN** the guest name is used without a location, and the other values keep theirs
