## ADDED Requirements

### Requirement: Export duration
Pseudonymizing a mail SHALL take time roughly proportional to the mail's length plus the number of known values. The time MUST NOT grow with the product of the two. An export of 600 mails SHALL be ready within one minute, even when the mailbox owner can see bookings with several thousand guest values.

#### Scenario: Large mailbox
- **WHEN** the admin exports 600 mails of a mailbox whose owner can see 400 bookings
- **THEN** the ZIP is ready within one minute

#### Scenario: More bookings, same export
- **WHEN** the owner gets ten times as many bookings and the admin exports the same mails again
- **THEN** the export does not take ten times as long

### Requirement: Overlapping values
Known values SHALL be replaced in a single pass over each text. Where two known values overlap in a text, the one that starts first SHALL be replaced. If both start at the same place, the longer one SHALL be replaced. A replacement that has already been inserted MUST NOT be replaced again.

#### Scenario: Full name and part
- **WHEN** a mail contains „Erika Musterfrau“ and the known values include both „Erika Musterfrau“ and its part „Musterfrau“
- **THEN** the full name is replaced by one replacement name

#### Scenario: Replacement not replaced again
- **WHEN** a mail contains „Erika Musterfrau“, and „Gast“ is also a known value (another guest's last name)
- **THEN** „Erika Musterfrau“ becomes „Gast 1“, and the „Gast“ in that replacement is not replaced again
