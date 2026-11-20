## ADDED Requirements

### Requirement: Extension notices
The system SHALL recognise a Fewo & Meer mail that reports a „Verlängerungsbuchung“ with property, original period („Zeitraum ursprüngliche Buchung“), extension period („Verlängerung“) and number of adults and children as an extension notice, also when it is forwarded. The notice SHALL be matched to the guest booking on the unit mapped to the property whose period equals the original period. The new period SHALL run from the earlier arrival to the later departure of both periods. Extension notices MUST be recognised only by the rules.

#### Scenario: Extension before the stay
- **WHEN** Anna's booking for „Haus Düne“ runs from 25.08.2026 to 28.08.2026 and Fewo & Meer reports an extension from 24.08.2026 to 25.08.2026
- **THEN** a change to 24.08.2026 – 28.08.2026 is proposed on that booking, and the mail shows as „Änderung vorgeschlagen“ with the kind „Verlängerung“

#### Scenario: Extension after the stay with more guests
- **WHEN** the booking runs from 10.07.2026 to 14.07.2026 for 2 adults and the notice reports an extension from 14.07.2026 to 16.07.2026 for 3 adults
- **THEN** a change to 10.07.2026 – 16.07.2026 with 3 adults is proposed

#### Scenario: Booking not reviewed yet
- **WHEN** the booking with the original period was created from a mail and is not reviewed yet
- **THEN** it takes the new period directly, and the mail is attached with „Angaben ergänzt“

#### Scenario: Already extended
- **WHEN** a booking on the unit already has the new period, for example because the owner changed it by hand
- **THEN** the mail is attached to that booking and no change is proposed

#### Scenario: No booking with the original period
- **WHEN** no guest booking on the mapped unit has the original period
- **THEN** the mail stays in the inbox as „nicht zugeordnet“ with the original period in its detail

#### Scenario: Unknown property
- **WHEN** the property name is not mapped to a unit yet
- **THEN** the mail waits for a unit like other Fewo & Meer mails

#### Scenario: Forwarded notice
- **WHEN** the owner forwards an extension notice to the mailbox
- **THEN** it is recognised like the original
