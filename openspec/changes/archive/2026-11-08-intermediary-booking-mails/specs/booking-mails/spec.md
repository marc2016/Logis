# Spec Delta

## MODIFIED Requirements

### Requirement: Recognise booking mails
The system SHALL recognise the mail type and read its fields with fixed rules for known agencies: Fewo & Meer confirmations, short-notice notices, block notices and cancellations, and DS Destination Solutions confirmations. A mail MUST count as coming from Fewo & Meer if it is sent from `fewoundmeer.de` or `fewoscout.com`, or if its text names Fewo & Meer. Read fields MUST include agency, booking number, property name, arrival, departure, adults, children, and where present pets, rent and guest data; a block notice carries only booking number, property name, period and intermediary. Amounts and dates MUST be read in German notation. For DS confirmations the total price MUST be used as the expected rent, and the agency MUST be taken from the line „Vermittler (Stornierung/Änderung)“: "Holidu" if the intermediary mentions Holidu or the line is missing, otherwise the intermediary's name without the suffix „in Kooperation mit …“.

#### Scenario: Fewo & Meer confirmation
- **WHEN** the mail "Neue Buchung für Haus Seeufer - Nordhorn" with booking number 250903, "2 Erwachsene, 1 Kind", 23.12.2026–27.12.2026 and "559,00 EUR" arrives
- **THEN** it is read as a confirmation of Fewo & Meer with 2 adults, 1 child and an expected rent of 559.00

#### Scenario: Fewo & Meer confirmation from FeWoScout without signature
- **WHEN** the mail "Neue Buchung für Haus Seeufer - Nordhorn" from `info@fewoscout.com` with booking number 250902, "2 Erwachsene", 01.11.2026–06.11.2026 and "432,10 EUR" arrives, and its text does not mention Fewo & Meer
- **THEN** it is read as a confirmation of Fewo & Meer with 2 adults and an expected rent of 432.10

#### Scenario: Holidu/DS confirmation
- **WHEN** a "Buchungsmitteilung (Buchungsnummer: 3e12f345gh)" with "REISEPREIS: 309,08 EUR" for "Ferienhaus Seeufer" and the intermediary „Holidu GmbH in Kooperation mit "DS Destination Solutions"“ arrives
- **THEN** it is read as a confirmation of Holidu with booking number "3e12f345gh" and an expected rent of 309.08

#### Scenario: VVV Nordhorn booking through DS
- **WHEN** a "Buchungsmitteilung (Buchungsnummer: 3e98f765kl)" with the intermediary „VVV-Stadt- und Citymarketing Nordhorn e.V.“, „Reisende: 2 Erwachsene(r), 2 Kind(er)“ and "REISEPREIS: 447,00 EUR" arrives
- **THEN** it is read as a confirmation of the agency "VVV-Stadt- und Citymarketing Nordhorn e.V." with 2 adults, 2 children and an expected rent of 447.00

#### Scenario: Fewo & Meer block notice
- **WHEN** the mail "Neue Blockung für Haus Seeufer - Nordhorn,250904, vom 15.05.2027 bis 18.05.2027 durch den VVV Nordhorn" arrives
- **THEN** it is read as a block notice of Fewo & Meer with booking number 250904, property "Haus Seeufer - Nordhorn", arrival 2027-05-15, departure 2027-05-18 and intermediary "VVV Nordhorn"

### Requirement: Effects on bookings
A recognised mail SHALL have exactly one of these effects, decided by agency and booking number: a new booking awaiting review, a proposed change or cancellation on the existing booking, or only attaching the mail when nothing differs. A block notice MUST NOT create or change a booking: it SHALL be attached to the booking on the mapped unit that is not cancelled and has exactly the same period, and otherwise MUST stay in the inbox as „nicht zugeordnet“. Whenever a booking is created or changed, the original mail MUST be attached to it as a document.

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
