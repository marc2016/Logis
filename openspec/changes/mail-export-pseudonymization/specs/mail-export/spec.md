## MODIFIED Requirements

### Requirement: Replaced values
Pseudonymization MUST replace:
- the guest values that the recognition rules read from the mail;
- the guest names, addresses, phone numbers, e-mail addresses and booking numbers of the bookings the mailbox owner can see;
- the mailbox owner's display name and username;
- the names of the legal entities the owner can see;
- street, postal code and city of the properties the owner can see;
- e-mail addresses, phone numbers and IBANs found by pattern.

A full name SHALL be replaced as a whole. A single part of a name SHALL only be replaced on its own when it has at least three letters, starts with an uppercase letter, and is not a legal form such as „GbR“, „GmbH“, „KG“ or „e.K.“.

#### Scenario: Recognised booking mail
- **WHEN** a Fewo & Meer confirmation for guest „Erika Musterfrau“ with phone „0170 1234567“ and booking number 250905 is exported
- **THEN** the exported mail contains none of these values

#### Scenario: Unrecognised cancellation of a known guest
- **WHEN** an unrecognised mail mentions „Erika Musterfrau“, whose booking is in Anna's calendar
- **THEN** the name is replaced in the exported mail

#### Scenario: E-mail address by pattern
- **WHEN** an unrecognised mail contains the address „erika@example.org“, which no booking knows
- **THEN** the address is replaced

#### Scenario: Owner and legal entity
- **WHEN** Anna Beispiel owns the GbR „Anna Beispiel GbR“ and an agency writes „Sehr geehrte(r) Anna Beispiel GbR“
- **THEN** neither „Anna Beispiel“ nor „Beispiel“ remains in the exported mail, and „GbR“ elsewhere in the text stays

#### Scenario: Property address
- **WHEN** a statement mail names Anna's property address „Musterweg 7, 12345 Beispielstadt“
- **THEN** street, postal code and city are replaced

#### Scenario: Lowercase name particle
- **WHEN** a guest of Anna is named „Lisa van der Berg“ and another mail reads „bis Ende der Herbstferien“
- **THEN** „der“ stays in that mail

### Requirement: Export index
The file `index.csv` SHALL list one row per exported mail with the file name, the received date, the pseudonymized sender and subject, the result, the kind, the agency and the mailbox owner's username. Result and kind SHALL be the German labels the inbox shows, such as „Buchung angelegt“ and „Bestätigung“. A code without a label SHALL appear as it is.

#### Scenario: Index row
- **WHEN** Anna's unrecognised Fewo & Meer mail from 2026-11-03 is exported
- **THEN** `index.csv` has a row with its file name, 2026-11-03, the result „Nicht erkannt“, the agency „Fewo & Meer“ and Anna's username

#### Scenario: Vacation rental labels
- **WHEN** a booking confirmation that created a booking is exported
- **THEN** its row shows the result „Buchung angelegt“ and the kind „Bestätigung“

## ADDED Requirements

### Requirement: Agency addresses
E-mail addresses on the domains of the agencies that the recognition rules know SHALL stay unchanged in the export, in headers and in text. Addresses on the domains of senders whose mails a handler took SHALL stay unchanged as well. Addresses at public mail providers MUST be replaced in every case.

#### Scenario: Agency writes from another domain
- **WHEN** Fewo & Meer's booking mails come from one agency domain and a ticket reply comes from its other agency domain
- **THEN** the sender address of the ticket reply stays unchanged
