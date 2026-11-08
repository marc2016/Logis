# booking-mails Specification

## Purpose

Booking mails turn the mails of rental agencies (confirmations, short-notice notices, changes and cancellations) into bookings and changes that the user confirms, with the original mail attached. It is the vacation rental handler of the mail inbox.

## Requirements

### Requirement: Recognise booking mails
The system SHALL recognise the mail type and read its fields with fixed rules for known agencies: Fewo & Meer confirmations, short-notice notices, block notices and cancellations, and DS Destination Solutions confirmations. A mail MUST count as coming from Fewo & Meer if it is sent from `fewoundmeer.de` or `fewoscout.com`, or if its text names Fewo & Meer. Read fields MUST include agency, booking number, property name, arrival, departure, adults, children, and where present pets, rent and guest data; a block notice carries only booking number, property name, period and intermediary. Amounts and dates MUST be read in German notation. For DS confirmations the total price MUST be used as the expected rent, and the agency MUST be taken from the line „Vermittler (Stornierung/Änderung)“: "Holidu" if the intermediary mentions Holidu or the line is missing, otherwise the intermediary's name without the suffix „in Kooperation mit …“.

#### Scenario: Fewo & Meer confirmation
- **WHEN** the mail "Neue Buchung für Haus Seeufer - Nordhorn" with booking number 275031, "2 Erwachsene, 1 Kind", 23.12.2026–27.12.2026 and "559,00 EUR" arrives
- **THEN** it is read as a confirmation of Fewo & Meer with 2 adults, 1 child and an expected rent of 559.00

#### Scenario: Fewo & Meer confirmation from FeWoScout without signature
- **WHEN** the mail "Neue Buchung für Haus Seeufer - Nordhorn" from `info@fewoscout.com` with booking number 274907, "2 Erwachsene", 01.11.2026–06.11.2026 and "454,32 EUR" arrives, and its text does not mention Fewo & Meer
- **THEN** it is read as a confirmation of Fewo & Meer with 2 adults and an expected rent of 454.32

#### Scenario: Holidu/DS confirmation
- **WHEN** a "Buchungsmitteilung (Buchungsnummer: 6a31h227wy)" with "REISEPREIS: 309,08 EUR" for "Ferienhaus Seeufer" and the intermediary „Holidu GmbH in Kooperation mit "DS Destination Solutions"“ arrives
- **THEN** it is read as a confirmation of Holidu with booking number "6a31h227wy" and an expected rent of 309.08

#### Scenario: VVV Nordhorn booking through DS
- **WHEN** a "Buchungsmitteilung (Buchungsnummer: 6a29b55l9j)" with the intermediary „VVV-Stadt- und Citymarketing Nordhorn e.V.“, „Reisende: 2 Erwachsene(r), 2 Kind(er)“ and "REISEPREIS: 447,00 EUR" arrives
- **THEN** it is read as a confirmation of the agency "VVV-Stadt- und Citymarketing Nordhorn e.V." with 2 adults, 2 children and an expected rent of 447.00

#### Scenario: Fewo & Meer block notice
- **WHEN** the mail "Neue Blockung für Haus Seeufer - Nordhorn,275206, vom 15.05.2027 bis 18.05.2027 durch den VVV Nordhorn" arrives
- **THEN** it is read as a block notice of Fewo & Meer with booking number 275206, property "Haus Seeufer - Nordhorn", arrival 2027-05-15, departure 2027-05-18 and intermediary "VVV Nordhorn"

### Requirement: Forwarded mails
Forwarded booking mails MUST be recognised like the originals, whether forwarded inline (with „WG:“ or „Fwd:“ and quoted text) or as an attachment.

#### Scenario: Forwarded by the user
- **WHEN** Anna forwards the confirmation of booking 275505 to the mailbox with the subject "WG: Neue Buchung für Haus Seeufer - Nordhorn"
- **THEN** it is read as a confirmation of Fewo & Meer like the original

#### Scenario: Forwarded as attachment
- **WHEN** the Holidu confirmation arrives as an attachment of a forwarded mail
- **THEN** the attached original is read, and the inbox shows its subject

### Requirement: AI fallback
If AI is enabled by the administrator, mails that no rule recognises SHALL be read by an AI model into the same fields, including the mail type. If AI is disabled or the result is incomplete, the mail MUST be listed as not recognised. Mails recognised by a rule MUST NOT be sent to the AI.

#### Scenario: Unknown change mail with AI
- **WHEN** AI is enabled and an unknown change mail for booking 275505 arrives
- **THEN** the AI result proposes the change on that booking like a rule result would

#### Scenario: AI disabled
- **WHEN** AI is disabled and a mail matches no rule
- **THEN** the mail appears in the inbox as „nicht erkannt“ and nothing is created

### Requirement: Unit mapping per agency
The system SHALL map the property name of a mail to a unit per agency, where the agency is the one resolved from the mail through the shared list of agencies. The first time a name is unknown, the mail MUST stay in the inbox as „Einheit zuordnen“. Once the user assigns a unit, the mapping MUST be remembered, and that mail and later mails with this name MUST be processed with it. Mappings MUST stay per mailbox owner, MUST follow their agency when it is renamed or merged, and MUST be found through any of the agency's mail names. Only units on which the mailbox owner has at least `EDITOR` MUST be offered.

#### Scenario: Two names for one house
- **WHEN** Anna has assigned "Haus Seeufer - Nordhorn" (Fewo & Meer) and "Ferienhaus Seeufer" (Holidu) to the same unit
- **THEN** mails of both agencies create bookings on that unit without asking again

#### Scenario: Mapping survives renaming
- **WHEN** Anna has assigned "Ferienhaus Seeufer" for „VVV-Stadt- und Citymarketing Nordhorn e.V.“ and then renames that agency to „VVV Nordhorn“
- **THEN** the next VVV mail for "Ferienhaus Seeufer" creates a booking without asking for the unit again

### Requirement: Effects on bookings
A recognised mail SHALL have exactly one of these effects, decided by agency and booking number: a new booking awaiting review, a proposed change or cancellation on the existing booking, or only attaching the mail when nothing differs. A block notice MUST NOT create or change a booking: it SHALL be attached to the booking on the mapped unit that is not cancelled and has exactly the same period, and otherwise MUST stay in the inbox as „nicht zugeordnet“. Whenever a booking is created or changed, the original mail MUST be attached to it as a document.

#### Scenario: Confirmation creates a booking to review
- **WHEN** the confirmation for booking 275031 is fetched
- **THEN** a booking „zu prüfen“ from 23.12.2026 to 27.12.2026 exists with the mail attached

#### Scenario: Short-notice notice for a known booking
- **WHEN** the „Last-Minute Buchung 275505“ mail has the same period and guests as booking 275505
- **THEN** the mail is attached to the booking and nothing else changes

#### Scenario: Cancellation mail
- **WHEN** the mail "Stornierung 275505, Haus Seeufer - Nordhorn" is fetched
- **THEN** booking 275505 shows a proposed cancellation „zu prüfen“ with the mail attached

#### Scenario: Block notice for a known stay
- **WHEN** booking "6a29b55l9j" of VVV Nordhorn runs from 2027-05-15 to 2027-05-18 and the Fewo & Meer block notice 275206 for that period is fetched
- **THEN** the mail is attached to booking "6a29b55l9j", and its booking number and agency stay unchanged

#### Scenario: Block notice before the booking mail
- **WHEN** the block notice 275206 is fetched and the unit has no booking from 2027-05-15 to 2027-05-18
- **THEN** no booking is created, the mail appears in the inbox as „nicht zugeordnet“, and retrying it after the VVV booking mail was processed attaches it to that booking

#### Scenario: Block notice with a different period
- **WHEN** a booking runs from 2027-05-15 to 2027-05-19 and the block notice for 15.05.2027 to 18.05.2027 is fetched
- **THEN** the mail appears in the inbox as „nicht zugeordnet“

### Requirement: Mails without a booking effect
A cancellation or change for an unknown booking number MUST stay in the inbox as „nicht zugeordnet“. A new booking that would overlap another booking MUST NOT be created and MUST stay in the inbox as „Zeitraum belegt“.

#### Scenario: Confirmation for an occupied period
- **WHEN** a confirmation arrives for a period in which the unit already has a booking
- **THEN** no booking is created, and the mail appears in the inbox as „Zeitraum belegt“

#### Scenario: Cancellation without booking
- **WHEN** the cancellation of booking 236678 from 2024 is fetched and no such booking exists
- **THEN** the mail appears in the inbox as „nicht zugeordnet“

### Requirement: Agency of imported bookings
A booking created from a mail SHALL reference the agency resolved from the mail's agency name. Matching a mail to an existing booking by booking number MUST compare agencies by identity, so a booking of an agency matches mails naming any of its mail names. A booking entered by hand without agency SHALL get the resolved agency of the first mail that matches it by number.

#### Scenario: Cancellation after renaming
- **WHEN** booking 275505 references „Fewo & Meer“, Anna has added the mail name „FEWO & MEER e.K.“, and a cancellation naming „FEWO & MEER e.K.“ with number 275505 arrives
- **THEN** booking 275505 shows a proposed cancellation „zu prüfen“
