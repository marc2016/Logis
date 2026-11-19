# mail-export Specification

## Purpose

Lets an admin export pseudonymized copies of stored mails from mailboxes whose owners consented, for example to improve mail recognition, and records every export.

## Requirements

### Requirement: Consent to the admin export
The mailbox settings SHALL offer the switch „Admins dürfen meine Mails pseudonymisiert exportieren“, off by default and for existing mailboxes. Only the mailbox owner SHALL be able to change it. While it is off, no admin MUST be able to see the mailbox in the export or export any of its mails. Switching it off MUST take effect for every export started afterwards.

#### Scenario: No consent
- **WHEN** Anna has not switched on the consent and an admin opens „Mail-Export“
- **THEN** Anna's mailbox is not offered, and an export request for it is answered with HTTP 404

#### Scenario: Give and revoke consent
- **WHEN** Anna switches the consent on, the admin exports, and Anna switches it off again
- **THEN** the first export contains Anna's mails, and a later export request for Anna's mailbox is answered with HTTP 404

#### Scenario: Only the owner decides
- **WHEN** an admin tries to switch on the consent for Anna's mailbox
- **THEN** the system rejects it, and the consent stays off

### Requirement: Export mails as an admin
On the page „Mail-Export“ under *Einstellungen*, an admin SHALL be able to export the stored mails of one or more consenting mailboxes, filtered by period (received from, to), result and agency, which combine with each other. Deleted mails MUST never be exported. The export MUST need a fresh sign-in like the rest of the admin area. Users without the role `ADMIN` MUST NOT be able to export.

#### Scenario: Export unrecognised mails of an agency
- **WHEN** the admin chooses Anna's mailbox, the result „Nicht erkannt“ and the agency „Fewo & Meer“, and confirms the warning
- **THEN** they download a ZIP with exactly Anna's unrecognised Fewo & Meer mails

#### Scenario: Deleted mails
- **WHEN** Anna deleted a mail in the inbox and the admin exports all of Anna's mails
- **THEN** the deleted mail is not in the export

#### Scenario: Ordinary user
- **WHEN** a user without the role `ADMIN` requests an export
- **THEN** the system responds with HTTP 403

#### Scenario: Too many mails
- **WHEN** the filters match more than 2000 mails
- **THEN** the export is rejected with „Zu viele Mails für einen Export. Bitte den Zeitraum oder die Filter einschränken.“

### Requirement: Warning before the download
Before the download, the interface SHALL show „Pseudonymisiert nach bestem Bemühen. Nicht erkannte Namen und Adressen können enthalten sein.“ and SHALL only download after the admin confirms it.

#### Scenario: Cancel the warning
- **WHEN** the admin chooses „Exportieren“ and then „Abbrechen“ in the warning
- **THEN** nothing is downloaded and no export is recorded

### Requirement: Export format
The export SHALL be a ZIP file with one `.eml` file per mail and a file `index.csv`. Each `.eml` file SHALL keep the structure of the original mail: its text and HTML parts, their character sets and transfer encodings, and forwarded or attached mails. Attachments that are not mails or text, such as PDFs and images, MUST be removed.

#### Scenario: Forwarded mail
- **WHEN** a mail that Anna forwarded with the booking mail attached is exported
- **THEN** its `.eml` file still contains the attached mail with its text and HTML parts

#### Scenario: Attachment removed
- **WHEN** a mail with a PDF invoice attached is exported
- **THEN** its `.eml` file contains no PDF

### Requirement: Export index
The file `index.csv` SHALL list one row per exported mail with the file name, the received date, the pseudonymized sender and subject, the result, the kind, the agency and the mailbox owner's username.

#### Scenario: Index row
- **WHEN** Anna's unrecognised Fewo & Meer mail from 2026-11-03 is exported
- **THEN** `index.csv` has a row with its file name, 2026-11-03, the result „Nicht erkannt“, the agency „Fewo & Meer“ and Anna's username

### Requirement: Pseudonymization
Each exported mail SHALL be pseudonymized in its subject, its address headers and every text and HTML part, including forwarded and attached mails. The same original value MUST get the same replacement within one export. Booking numbers and phone numbers SHALL keep their format. Dates and amounts SHALL stay unchanged. Pseudonymization MUST NOT send mails to an AI service.

#### Scenario: Same guest in two mails
- **WHEN** the confirmation and the cancellation of the same guest are exported together
- **THEN** both show the same replacement name

#### Scenario: Dates and amounts
- **WHEN** a booking confirmation with arrival 23.12.2026 and rent 840,00 € is exported
- **THEN** both values are unchanged in the exported mail

### Requirement: Replaced values
Pseudonymization MUST replace:
- the guest values that the recognition rules read from the mail;
- the guest names, addresses, phone numbers, e-mail addresses and booking numbers of the bookings the mailbox owner can see;
- e-mail addresses, phone numbers and IBANs found by pattern.

#### Scenario: Recognised booking mail
- **WHEN** a Fewo & Meer confirmation for guest „Erika Musterfrau“ with phone „0170 1234567“ and booking number 250905 is exported
- **THEN** the exported mail contains none of these values

#### Scenario: Unrecognised cancellation of a known guest
- **WHEN** an unrecognised mail mentions „Erika Musterfrau“, whose booking is in Anna's calendar
- **THEN** the name is replaced in the exported mail

#### Scenario: E-mail address by pattern
- **WHEN** an unrecognised mail contains the address „erika@example.org“, which no booking knows
- **THEN** the address is replaced

### Requirement: Export log
Every export SHALL be recorded per mailbox with the admin, the time, the number of mails and the filters. The page „Mail-Export“ SHALL list the recorded exports, newest first. The mailbox owner SHALL see their last export in the mailbox settings, with date, number of mails and admin. The log MUST NOT contain mail content.

#### Scenario: Owner sees the export
- **WHEN** the admin exported 42 of Anna's mails
- **THEN** Anna's mailbox settings show the last export with today's date, 42 mails and the admin's name

#### Scenario: Admin sees the log
- **WHEN** the admin opens „Mail-Export“ after two exports
- **THEN** both are listed with mailbox, number of mails and filters, the newest first

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
