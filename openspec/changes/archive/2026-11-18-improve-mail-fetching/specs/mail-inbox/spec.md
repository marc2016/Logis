# Spec Delta

## MODIFIED Requirements

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

## ADDED Requirements

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
