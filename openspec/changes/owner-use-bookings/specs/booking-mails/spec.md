# Spec Delta

## ADDED Requirements

### Requirement: Fewo & Meer owner block notice
The system SHALL recognise a Fewo & Meer mail whose subject reads „Eigentümerblockung <Nr>, <Objekt>, vom TT.MM.JJJJ bis TT.MM.JJJJ“ as an owner block notice with booking number, property name, arrival and departure. The unit SHALL be found through the same property assignment as for other mails of the agency, and an unknown property name MUST leave the mail waiting for a unit. If no booking of the agency with that number exists, it SHALL create an owner use with that number and period; if the period is taken, the mail MUST stay in the inbox as „Zeitraum belegt“. The inbox SHALL show the result „Eigenbelegung angelegt“.

#### Scenario: Read an owner block notice
- **WHEN** the mail "Eigentümerblockung 275001, Haus Seeufer - Nordhorn, vom 09.06.2027 bis 14.06.2027" from Fewo & Meer arrives
- **THEN** it is read as an owner block notice of Fewo & Meer with booking number 275001, property "Haus Seeufer - Nordhorn", arrival 2027-06-09 and departure 2027-06-14

#### Scenario: Unknown property
- **WHEN** the owner block notice names a property that is not assigned to a unit yet
- **THEN** the mail waits in the inbox for „Einheit zuordnen“, and assigning the unit creates the owner use

#### Scenario: Period taken
- **WHEN** the owner block notice covers a period in which the unit has a guest booking
- **THEN** no owner use is created and the mail shows „Zeitraum belegt“

## MODIFIED Requirements

### Requirement: Effects on bookings
A recognised mail SHALL have exactly one of these effects, decided by agency and booking number: a new booking awaiting review, a proposed change or cancellation on the existing booking, or only attaching the mail when nothing differs. An owner block notice SHALL instead create an owner use directly, without review, or, for a known booking number, attach the mail and take over a different period directly. A block notice MUST NOT create or change a booking: it SHALL be attached to the booking on the mapped unit that is not cancelled and has exactly the same period, and otherwise MUST stay in the inbox as „nicht zugeordnet“. Whenever a booking is created or changed, the original mail MUST be attached to it as a document.

#### Scenario: Confirmation creates a booking to review
- **WHEN** the confirmation for booking 250903 is fetched
- **THEN** a booking „zu prüfen“ from 23.12.2026 to 27.12.2026 exists with the mail attached

#### Scenario: Short-notice notice for a known booking
- **WHEN** the „Last-Minute Buchung 250905“ mail has the same period and guests as booking 250905
- **THEN** the mail is attached to the booking and nothing else changes

#### Scenario: Cancellation mail
- **WHEN** the mail "Stornierung 250905, Haus Seeufer - Nordhorn" is fetched
- **THEN** booking 250905 shows a proposed cancellation „zu prüfen“ with the mail attached

#### Scenario: Block notice for a known stay
- **WHEN** booking "3e98f765kl" of VVV Nordhorn runs from 2027-05-15 to 2027-05-18 and the Fewo & Meer block notice 250904 for that period is fetched
- **THEN** the mail is attached to booking "3e98f765kl", and its booking number and agency stay unchanged

#### Scenario: Block notice before the booking mail
- **WHEN** the block notice 250904 is fetched and the unit has no booking from 2027-05-15 to 2027-05-18
- **THEN** no booking is created, the mail appears in the inbox as „nicht zugeordnet“, and retrying it after the VVV booking mail was processed attaches it to that booking

#### Scenario: Block notice with a different period
- **WHEN** a booking runs from 2027-05-15 to 2027-05-19 and the block notice for 15.05.2027 to 18.05.2027 is fetched
- **THEN** the mail appears in the inbox as „nicht zugeordnet“

#### Scenario: Owner block notice creates an owner use
- **WHEN** the mail "Eigentümerblockung 275001, Haus Seeufer - Nordhorn, vom 09.06.2027 bis 14.06.2027" is fetched and "Haus Seeufer - Nordhorn" of Fewo & Meer is assigned to "Haus Seeufer"
- **THEN** "Haus Seeufer" has an owner use from 2027-06-09 to 2027-06-14 with booking number 275001, it is not „zu prüfen“, the mail is attached, and the inbox shows „Eigenbelegung angelegt“

#### Scenario: Owner block notice again with another period
- **WHEN** owner use 275001 runs from 2027-06-09 to 2027-06-14 and an owner block notice 275001 for 10.06.2027 to 14.06.2027 is fetched
- **THEN** the owner use runs from 2027-06-10 to 2027-06-14 and the mail is attached
