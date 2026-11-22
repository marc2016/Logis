# booking-mails Specification

## Purpose

Booking mails turn the mails of rental agencies (confirmations, short-notice notices, changes and cancellations) into bookings and changes that the user confirms, with the original mail attached. It is the vacation rental handler of the mail inbox.

## Requirements

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

### Requirement: Forwarded mails
Forwarded booking mails MUST be recognised like the originals, whether forwarded inline (with „WG:“ or „Fwd:“ and quoted text) or as an attachment. Changes that forwarding makes to the spacing between a field label and its value (spaces next to tabs, several spaces) MUST NOT prevent recognition.

#### Scenario: Forwarded by the user
- **WHEN** Anna forwards the confirmation of booking 250905 to the mailbox with the subject "WG: Neue Buchung für Haus Seeufer - Nordhorn"
- **THEN** it is read as a confirmation of Fewo & Meer like the original

#### Scenario: Forwarded as attachment
- **WHEN** the Holidu confirmation arrives as an attachment of a forwarded mail
- **THEN** the attached original is read, and the inbox shows its subject

#### Scenario: Forwarded with changed spacing
- **WHEN** Anna forwards the confirmation of booking 250902 and the forwarded text reads „Buchungsnummer ⇥250902“ and „Anreise ⇥ 01.11.2026“, with a space before and after the tab
- **THEN** it is read as a confirmation of Fewo & Meer with booking number 250902, arrival 2026-11-01, the guest's name and address, like the original

### Requirement: AI fallback
If AI is enabled by the administrator, mails that no rule recognises SHALL be read by an AI model into the same fields, including the mail type. If AI is disabled or the result is incomplete, the mail MUST be listed as not recognised. Mails recognised by a rule MUST NOT be sent to the AI.

#### Scenario: Unknown change mail with AI
- **WHEN** AI is enabled and an unknown change mail for booking 250905 arrives
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
- **WHEN** booking 250905 references „Fewo & Meer“, Anna has added the mail name „FEWO & MEER e.K.“, and a cancellation naming „FEWO & MEER e.K.“ with number 250905 arrives
- **THEN** booking 250905 shows a proposed cancellation „zu prüfen“

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

### Requirement: Fewo & Meer owner block notice
The system SHALL recognise a Fewo & Meer mail whose subject reads „Eigentümerblockung <Nr>, <Objekt>, vom TT.MM.JJJJ bis TT.MM.JJJJ“ as an owner block notice with booking number, property name, arrival and departure. The unit SHALL be found through the same property assignment as for other mails of the agency, and an unknown property name MUST leave the mail waiting for a unit. If no booking of the agency with that number exists but the unit has an owner use that is not cancelled, has no booking number and has exactly the same period, the mail SHALL be attached to it and SHALL set its booking number and agency. Otherwise it SHALL create an owner use with that number and period; if the period is taken, the mail MUST stay in the inbox as „Zeitraum belegt“. The inbox SHALL show the result „Eigenbelegung angelegt“.

#### Scenario: Read an owner block notice
- **WHEN** the mail "Eigentümerblockung 275001, Haus Seeufer - Nordhorn, vom 09.06.2027 bis 14.06.2027" from Fewo & Meer arrives
- **THEN** it is read as an owner block notice of Fewo & Meer with booking number 275001, property "Haus Seeufer - Nordhorn", arrival 2027-06-09 and departure 2027-06-14

#### Scenario: Unknown property
- **WHEN** the owner block notice names a property that is not assigned to a unit yet
- **THEN** the mail waits in the inbox for „Einheit zuordnen“, and assigning the unit creates the owner use

#### Scenario: Owner use recorded by hand first
- **WHEN** Anna has recorded an „Eigenbelegung“ without booking number on "Haus Seeufer" from 2027-06-09 to 2027-06-14 and the owner block notice 275001 for that period is fetched
- **THEN** no second owner use is created, the mail is attached to Anna's owner use, and it now has booking number 275001 and agency Fewo & Meer

#### Scenario: Period taken
- **WHEN** the owner block notice covers a period in which the unit has a guest booking
- **THEN** no owner use is created and the mail shows „Zeitraum belegt“

### Requirement: Ticket receipts
The system SHALL recognise a mail from Fewo & Meer whose text confirms receipt of the owner's mail under a ticket ID („vielen Dank für Ihre E-Mail“ and „Ticket-ID:“) as a ticket receipt. A ticket receipt SHALL be shown in the inbox as „Ignoriert“ with the kind „Eingangsbestätigung“, the agency „Fewo & Meer“ and the ticket ID as reference. It MUST NOT create, change or attach to a booking, and it MUST NOT be sent to the AI. A mail of the same ticket without the receipt text MUST be handled as before.

#### Scenario: Receipt for an owner block request
- **WHEN** Fewo & Meer answers the owner's mail with „vielen Dank für Ihre E-Mail … Ticket-ID: ABC-123-45678“ under the subject „[#ABC-123-45678]: Eigentümerblockung“
- **THEN** the mail shows as „Ignoriert“ with the kind „Eingangsbestätigung“ and the reference „ABC-123-45678“, and no booking is created or changed

#### Scenario: Staff reply in the same ticket
- **WHEN** a mail with the subject „[#ABC-123-45678]: Eigentümerblockung“ contains a staff reply without the receipt text
- **THEN** it is handled as before, for example „Nicht erkannt“

#### Scenario: Receipt with AI enabled
- **WHEN** AI is enabled and a ticket receipt arrives
- **THEN** the mail is not sent to the AI

#### Scenario: Stored receipt processed again
- **WHEN** a receipt stored as „Nicht erkannt“ before this change is processed again with „Erneut verarbeiten“
- **THEN** it shows as „Ignoriert“ with the kind „Eingangsbestätigung“

### Requirement: Extension notices
The system SHALL recognise a Fewo & Meer mail that reports a „Verlängerungsbuchung“ with property, original period („Zeitraum ursprüngliche Buchung“), extension period („Verlängerung“) and number of adults and children as an extension notice, also when it is forwarded. The notice SHALL be matched to the guest booking on the unit mapped to the property whose period equals the original period. The new period SHALL run from the earlier arrival to the later departure of both periods. Extension notices MUST be recognised only by the rules.

#### Scenario: Extension before the stay
- **WHEN** Anna's booking for „Haus Düne“ runs from 14.09.2027 to 17.09.2027 and Fewo & Meer reports an extension from 12.09.2027 to 14.09.2027
- **THEN** a change to 12.09.2027 – 17.09.2027 is proposed on that booking, and the mail shows as „Änderung vorgeschlagen“ with the kind „Verlängerung“

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

### Requirement: Statement mails
The system SHALL recognise the statement mails of agencies and create a statement from each one, also when the mail is forwarded:

- a Fewo & Meer mail with the subject „Ihre Abrechnung für `<Monat Jahr>` - `<Objekt>`“: the period is that calendar month, the property is the object named in the subject;
- an owner statement notice of the booking system DS Destination Solutions, sent in the name of the agency, with the subject „Ihre Abrechnung: `<Rechnungsnummer>`“ and the text „Abrechnung für den Zeitraum vom `<Datum>` bis zum `<Datum>`“: period and invoice number are taken from the mail, the agency is the sender named in the mail.

The statement SHALL be created for the unit mapped to the property, with the agency as provider. Its statement date SHALL be the invoice date of a read PDF, otherwise the day the mail was received. A PDF attached to the mail SHALL be stored as a document of the statement. The mail SHALL show as „Abrechnung angelegt“ with the kind „Abrechnung“ and link to the statement. Statement mails MUST be recognised only by the rules and MUST NOT be sent to an AI service, neither the mail nor its PDF.

#### Scenario: Fewo & Meer statement
- **WHEN** Fewo & Meer sends „Ihre Abrechnung für März 2027 - Haus Düne“ with a statement PDF and „Haus Düne“ is mapped to a unit
- **THEN** a statement of Fewo & Meer for that unit with the period 01.03.2027 – 31.03.2027 exists, the PDF is one of its documents, and the mail shows as „Abrechnung angelegt“ with a link to the statement

#### Scenario: Unknown property
- **WHEN** the object in the subject is not mapped to a unit yet
- **THEN** the mail waits as „Einheit zuordnen“, and assigning a unit creates the statement

#### Scenario: Notice without a property
- **WHEN** an owner statement notice of DS Destination Solutions arrives and all property mappings of its agency point to the same unit
- **THEN** the statement is created for that unit with the period and the invoice number from the mail, without lines, and the PDF is attached

#### Scenario: Notice without a clear unit
- **WHEN** such a notice arrives and its agency has no property mapping or mappings to several units
- **THEN** the mail waits as „Einheit zuordnen“ for „Abrechnungen“; the unit chosen there is used for this and later notices of the agency

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
