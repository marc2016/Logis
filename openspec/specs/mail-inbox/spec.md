# mail-inbox Specification

## Purpose

The mail inbox fetches mails from a user's IMAP mailbox and hands them to the parts of Logis that understand them, such as vacation rental for booking mails. It stores each mail once, shows what happened to it, and lets the user act on it.

## Requirements

### Requirement: Configure a mailbox
A user SHALL be able to configure one IMAP mailbox with host, port, username, password, folder, a cut-off date and an on/off switch. The password MUST be stored encrypted and MUST never be returned by the system. Saving MUST test the connection and reject unreachable or wrongly authenticated mailboxes with „Das Postfach ist nicht erreichbar oder die Anmeldung ist fehlgeschlagen“.

#### Scenario: Set up the mailbox
- **WHEN** Anna enters the IMAP data of the folder "Buchungen" and the cut-off date 2026-11-01
- **THEN** the settings are saved, and the password field shows only that a password is set

#### Scenario: Wrong password
- **WHEN** Anna saves the mailbox with a wrong password
- **THEN** the system rejects this with the message „Das Postfach ist nicht erreichbar oder die Anmeldung ist fehlgeschlagen“

### Requirement: Poll from the cut-off date
For an enabled mailbox, the system SHALL fetch new mails regularly, at least every 10 minutes, without user action. It MUST only consider mails received on or after the cut-off date, MUST process each mail once (by its Message-ID), and MUST NOT change, move or delete mails in the mailbox. The user SHALL be able to fetch older mails from a chosen date once. Only one fetch SHALL run per mailbox at a time: a manual fetch started while another fetch of the same mailbox runs MUST be rejected with „Es läuft bereits ein Abruf.“, and the regular poll MUST skip a mailbox that is being fetched. Fetched mails SHALL be processed one by one while the fetch runs, so they appear in the inbox before the fetch ends.

#### Scenario: Old mails are left out
- **WHEN** the folder contains a cancellation from 2024 and the cut-off date is 2026-11-01
- **THEN** the cancellation is not fetched

#### Scenario: Fetch older mails
- **WHEN** Anna fetches older mails from 2025-01-01
- **THEN** the mails from 2025 on are processed once, and later polls do not process them again

#### Scenario: Fetch the same period twice
- **WHEN** Anna fetches older mails from 2025-01-01 and later again from 2024-01-01
- **THEN** the second fetch only adds mails from 2024, and no mail appears twice in the inbox

#### Scenario: Second fetch while one runs
- **WHEN** a fetch of older mails is running and Anna chooses „Jetzt abrufen“
- **THEN** the system rejects this with „Es läuft bereits ein Abruf.“ and the running fetch continues

### Requirement: Mail handlers
Parts of Logis SHALL be able to register as handlers for fetched mails. Each mail MUST be offered to the handlers in a fixed order until one takes it. A mail no handler takes MUST be listed as „nicht erkannt“ and MAY be processed again later. Handlers of a disabled module MUST NOT be asked, and fetching MUST continue while a module is disabled.

#### Scenario: Module disabled
- **WHEN** vacation rental is disabled and a booking confirmation arrives
- **THEN** the mail is fetched and listed as „nicht erkannt“, and after enabling the module and retrying it creates the booking

### Requirement: Inbox
A user SHALL see the mails fetched from their mailbox with date, sender, subject, and what the handling part reported (kind, result, linked record). They SHALL be able to open the mail text, retry processing, ignore a mail and delete it; handling parts MAY offer further actions, such as assigning a unit. Deleting MUST remove the stored content including guest data and MUST keep the mail from being fetched again. Other users MUST NOT see a user's inbox. The only exception is the pseudonymized export by an admin, which needs the owner's consent (see `mail-export`).

#### Scenario: Retry after assigning a unit
- **WHEN** Anna assigns a unit to a mail „Einheit zuordnen“
- **THEN** the mail is processed again and shows „Buchung angelegt“

#### Scenario: Delete a mail
- **WHEN** Anna deletes a mail in the inbox
- **THEN** it disappears from the list together with its content, and the next poll does not fetch it again

#### Scenario: Another user's inbox
- **WHEN** an admin without Anna's consent looks for Anna's mails
- **THEN** they find neither the inbox nor an export of it

### Requirement: Open the linked record
If a handling part reports a linked record for a mail, the inbox SHALL offer to open that record directly on its own page.

#### Scenario: Open the created booking
- **WHEN** Anna opens a mail „Buchung angelegt“ in the inbox and chooses „Zur Buchung“
- **THEN** the page of the created booking opens

### Requirement: Bulk actions in the inbox
The inbox SHALL offer, for selected mails, „Erneut verarbeiten“, „Ignorieren“ and „Löschen“. A handling part MAY add bulk actions. The booking part SHALL add „Einheit zuordnen“ for mails waiting for a unit, which assigns the chosen unit to each mail's agency and property name and processes the waiting mails again. Mails a bulk action does not apply to MUST fail with a message instead of being changed.

#### Scenario: Assign a unit to several mails
- **WHEN** Anna selects three mails of Fewo & Meer for „Haus Seeufer - Nordhorn“ that wait for a unit and assigns „Haus Seeufer“
- **THEN** all three are processed again and show „Buchung angelegt“ or „angehängt“

#### Scenario: Delete old mails
- **WHEN** Anna selects 20 ignored mails and confirms „Löschen“
- **THEN** they disappear with their content and are not fetched again

### Requirement: Choose the date for older mails
The mailbox settings SHALL offer the quick ranges „1 Jahr“, „2 Jahre“ and „5 Jahre“, counted back from today, and a date field for any other start date. The date field SHALL accept a typed date in the form `TT.MM.JJJJ` and SHALL offer a calendar in which month and year can be chosen directly. „Ältere Mails abrufen“ MUST be enabled as soon as a valid date is chosen. A date that is incomplete or invalid MUST show „Bitte ein gültiges Datum eingeben.“, and a date in the future MUST show „Das Datum darf nicht in der Zukunft liegen.“. The settings MUST state that already fetched mails are skipped.

#### Scenario: Jump back several years
- **WHEN** Anna chooses „5 Jahre“ on 2026-11-18
- **THEN** the start date 18.11.2021 is set and „Ältere Mails abrufen“ is enabled

#### Scenario: Type a date
- **WHEN** Anna types 01.03.2023 into the date field
- **THEN** „Ältere Mails abrufen“ is enabled and fetches mails from 2023-03-01

#### Scenario: Incomplete date
- **WHEN** Anna types 01.03.20 and leaves the field
- **THEN** the field shows „Bitte ein gültiges Datum eingeben.“ and the button stays disabled

#### Scenario: Pick a year in the calendar
- **WHEN** Anna opens the calendar and selects the year 2022 and the month January
- **THEN** the calendar shows January 2022 without paging through the months in between

### Requirement: Fetch progress
While a fetch of the user's mailbox runs, whether started by „Jetzt abrufen“, by „Ältere Mails abrufen“ or by the regular poll, the mailbox settings SHALL show that it runs, from which date it fetches, how many of the found mails have been checked and how many of them were new, and a progress bar. The fetch buttons MUST be disabled while it runs. When the fetch ends, the settings SHALL show its result („Abruf abgeschlossen: 12 neue Mails“ or the error) and the inbox SHALL show the new mails without a manual reload.

#### Scenario: Progress of a long fetch
- **WHEN** Anna fetches older mails from 2023-01-01 and the folder holds 400 mails since then
- **THEN** the settings show the progress, for example „Mails ab 01.01.2023: 120 von 400 geprüft, 35 neu“, and the buttons are disabled

#### Scenario: Fetch ends
- **WHEN** the fetch has checked all 400 mails and 90 of them were new
- **THEN** the settings show „Abruf abgeschlossen: 90 neue Mails“ and the inbox lists them

#### Scenario: Fetch fails
- **WHEN** the mailbox becomes unreachable during a fetch
- **THEN** the settings show the error, the mails processed so far stay in the inbox, and the buttons are enabled again

### Requirement: Sort and filter the inbox
The inbox SHALL be sortable by each of its columns (Eingang, Absender, Betreff, Art, Ergebnis) in both directions, newest mails first by default. It SHALL offer filters for the result, the kind, the sender, the received period (from, to) and a text search in the subject, which combine with each other. A quick filter „Offen“ SHALL show only mails that need attention (such as „Nicht erkannt“, „Fehler“ or mails waiting for a unit). The active filters and sorting SHALL be kept in the page address, so they survive a reload, and SHALL be clearable at once. Bulk actions MUST apply only to selected mails that are visible.

#### Scenario: Show open mails
- **WHEN** Anna chooses the quick filter „Offen“
- **THEN** only mails that need attention are listed, and handled or ignored mails are hidden

#### Scenario: Combine filters
- **WHEN** Anna filters by the sender „Fewo & Meer“ and the period 01.01.2025 to 31.12.2025
- **THEN** only mails of that sender received in 2025 are listed

#### Scenario: Sort by subject
- **WHEN** Anna clicks the column header „Betreff“
- **THEN** the mails are sorted by subject in ascending order, and a second click sorts them in descending order

#### Scenario: Filters survive a reload
- **WHEN** Anna has chosen „Offen“, sorted by „Absender“ and reloads the page
- **THEN** the same filter and sorting are active
