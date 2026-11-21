## ADDED Requirements

### Requirement: Statement mails
The system SHALL recognise the statement mails of agencies and create a statement from each one, also when the mail is forwarded:

- a Fewo & Meer mail with the subject „Ihre Abrechnung für `<Monat Jahr>` - `<Objekt>`“: the period is that calendar month, the property is the object named in the subject;
- an owner statement notice of the Holidu booking system with the subject „Ihre Abrechnung: `<Rechnungsnummer>`“ and the text „Abrechnung für den Zeitraum vom `<Datum>` bis zum `<Datum>`“: period and invoice number are taken from the mail.

The statement SHALL be created for the unit mapped to the property, with the agency as provider. Its statement date SHALL be the invoice date of a read PDF, otherwise the day the mail was received. A PDF attached to the mail SHALL be stored as a document of the statement. The mail SHALL show as „Abrechnung angelegt“ with the kind „Abrechnung“ and link to the statement. Statement mails MUST be recognised only by the rules and MUST NOT be sent to an AI service, neither the mail nor its PDF.

#### Scenario: Fewo & Meer statement
- **WHEN** Fewo & Meer sends „Ihre Abrechnung für März 2027 - Haus Düne“ with a statement PDF and „Haus Düne“ is mapped to a unit
- **THEN** a statement of Fewo & Meer for that unit with the period 01.03.2027 – 31.03.2027 exists, the PDF is one of its documents, and the mail shows as „Abrechnung angelegt“ with a link to the statement

#### Scenario: Unknown property
- **WHEN** the object in the subject is not mapped to a unit yet
- **THEN** the mail waits as „Einheit zuordnen“, and assigning a unit creates the statement

#### Scenario: Notice without a property
- **WHEN** an owner statement notice of the Holidu system arrives and all property mappings of its agency point to the same unit
- **THEN** the statement is created for that unit with the period and the invoice number from the mail, without lines, and the PDF is attached

#### Scenario: Notice without a clear unit
- **WHEN** such a notice arrives and its agency has no property mapping or mappings to several units
- **THEN** the mail waits as „Einheit zuordnen“; the unit chosen there is used for this and later notices of the agency

#### Scenario: Invoice number already recorded
- **WHEN** a statement mail carries an invoice number that a statement of the same provider already has
- **THEN** no second statement is created, and the mail shows as „Angehängt“ with the detail „Abrechnung bereits erfasst“ and links to the existing statement

#### Scenario: Mail without a readable PDF
- **WHEN** a statement mail has no PDF, or its PDF cannot be read
- **THEN** the statement is still created from the mail's data without lines, and the mail's detail says „PDF nicht gelesen“

#### Scenario: Stored mails
- **WHEN** the owner uses „Erneut verarbeiten“ on a statement mail stored as „Nicht erkannt“
- **THEN** it is processed like a new statement mail

#### Scenario: No AI
- **WHEN** AI recognition is switched on and a statement mail arrives
- **THEN** nothing of the mail is sent to the AI service

### Requirement: Fewo & Meer statement PDF
The system SHALL read the statement PDF of Fewo & Meer: invoice number, invoice date, and for each tenant the rental period, the rent, the additional services and the commission including VAT, and the balance („Saldo“). For each tenant row the gross SHALL be rent plus additional services, the commission SHALL be the commission including VAT, and the cleaning SHALL be 0. The balance SHALL be kept as the statement's stated payout.

A row SHALL be assigned to the guest booking of the statement's unit whose period equals the row's rental period. Rows of the same tenant whose periods follow each other without a gap SHALL first be tried together, with their amounts added, against a booking with the combined period. A row that finds no booking, or whose booking is already on another statement, SHALL be kept as an open line of the statement. The tenant name MUST NOT decide the assignment.

#### Scenario: Rows match bookings
- **WHEN** the PDF lists a tenant from 05.03.2027 to 12.03.2027 with rent 700,00, additional services 90,00 and commission including VAT 125,00, and the unit has a booking with that period
- **THEN** the booking is on the statement with gross 790,00, commission 125,00, cleaning 0,00 and net 665,00

#### Scenario: Booking of the previous month
- **WHEN** the March statement lists a tenant from 26.02.2027 to 03.03.2027 and the unit has a booking with that period
- **THEN** the booking is assigned although it starts before the statement's period

#### Scenario: Extension row
- **WHEN** the PDF lists the same tenant from 14.03.2027 to 15.03.2027 as „Verlängerungsbuchung“ and from 15.03.2027 to 18.03.2027, and the unit has one booking from 14.03.2027 to 18.03.2027
- **THEN** that booking is on the statement with the amounts of both rows added

#### Scenario: Extension not taken over
- **WHEN** the same rows arrive and the unit has a booking from 15.03.2027 to 18.03.2027 only
- **THEN** that booking takes the amounts of its own row, and the extension row is an open line

#### Scenario: No booking for a row
- **WHEN** no booking of the unit has the row's period
- **THEN** the row is an open line of the statement with tenant, period, gross and commission

#### Scenario: Booking already settled
- **WHEN** the booking with the row's period is on another statement
- **THEN** the row is an open line with the note „bereits abgerechnet“, and the other statement is unchanged

#### Scenario: Owner use
- **WHEN** only an owner use has the row's period
- **THEN** the row is an open line
