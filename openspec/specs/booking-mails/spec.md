# booking-mails Specification

## Purpose

Booking mails turn the mails of rental agencies (confirmations, short-notice notices, changes and cancellations) into bookings and changes that the user confirms, with the original mail attached. It is the vacation rental handler of the mail inbox.

## Requirements

### Requirement: Recognise booking mails
The system SHALL recognise the mail type and read its fields with fixed rules for known agencies: Fewo & Meer confirmations, short-notice notices and cancellations, and Holidu/DS confirmations. Read fields MUST include agency, booking number, property name, arrival, departure, adults, children, and where present pets, rent and guest data. Amounts and dates MUST be read in German notation. For Holidu/DS the total price MUST be used as the expected rent.

#### Scenario: Fewo & Meer confirmation
- **WHEN** the mail "Neue Buchung für Haus Seeufer - Nordhorn" with booking number 275031, "2 Erwachsene, 1 Kind", 23.12.2026–27.12.2026 and "559,00 EUR" arrives
- **THEN** it is read as a confirmation of Fewo & Meer with 2 adults, 1 child and an expected rent of 559.00

#### Scenario: Holidu/DS confirmation
- **WHEN** a "Buchungsmitteilung (Buchungsnummer: 6a31h227wy)" with "REISEPREIS: 309,08 EUR" for "Ferienhaus Seeufer" arrives
- **THEN** it is read as a confirmation of Holidu with booking number "6a31h227wy" and an expected rent of 309.08

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
The system SHALL map the property name of a mail to a unit per agency. The first time a name is unknown, the mail MUST stay in the inbox as „Einheit zuordnen“. Once the user assigns a unit, the mapping MUST be remembered, and that mail and later mails with this name MUST be processed with it. Only units on which the mailbox owner has at least `EDITOR` MUST be offered.

#### Scenario: Two names for one house
- **WHEN** Anna has assigned "Haus Seeufer - Nordhorn" (Fewo & Meer) and "Ferienhaus Seeufer" (Holidu) to the same unit
- **THEN** mails of both agencies create bookings on that unit without asking again

### Requirement: Effects on bookings
A recognised mail SHALL have exactly one of these effects, decided by agency and booking number: a new booking awaiting review, a proposed change or cancellation on the existing booking, or only attaching the mail when nothing differs. Whenever a booking is created or changed, the original mail MUST be attached to it as a document.

#### Scenario: Confirmation creates a booking to review
- **WHEN** the confirmation for booking 275031 is fetched
- **THEN** a booking „zu prüfen“ from 23.12.2026 to 27.12.2026 exists with the mail attached

#### Scenario: Short-notice notice for a known booking
- **WHEN** the „Last-Minute Buchung 275505“ mail has the same period and guests as booking 275505
- **THEN** the mail is attached to the booking and nothing else changes

#### Scenario: Cancellation mail
- **WHEN** the mail "Stornierung 275505, Haus Seeufer - Nordhorn" is fetched
- **THEN** booking 275505 shows a proposed cancellation „zu prüfen“ with the mail attached

### Requirement: Mails without a booking effect
A cancellation or change for an unknown booking number MUST stay in the inbox as „nicht zugeordnet“. A new booking that would overlap another booking MUST NOT be created and MUST stay in the inbox as „Zeitraum belegt“.

#### Scenario: Confirmation for an occupied period
- **WHEN** a confirmation arrives for a period in which the unit already has a booking
- **THEN** no booking is created, and the mail appears in the inbox as „Zeitraum belegt“

#### Scenario: Cancellation without booking
- **WHEN** the cancellation of booking 236678 from 2024 is fetched and no such booking exists
- **THEN** the mail appears in the inbox as „nicht zugeordnet“
