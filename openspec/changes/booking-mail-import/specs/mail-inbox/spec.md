# Spec Delta

## Purpose

The mail inbox fetches mails from a user's IMAP mailbox and hands them to the parts of Logis that understand them, such as vacation rental for booking mails. It stores each mail once, shows what happened to it, and lets the user act on it.

## ADDED Requirements

### Requirement: Configure a mailbox
A user SHALL be able to configure one IMAP mailbox with host, port, username, password, folder, a cut-off date and an on/off switch. The password MUST be stored encrypted and MUST never be returned by the system. Saving MUST test the connection and reject unreachable or wrongly authenticated mailboxes with „Das Postfach ist nicht erreichbar oder die Anmeldung ist fehlgeschlagen“.

#### Scenario: Set up the mailbox
- **WHEN** Anna enters the IMAP data of the folder "Buchungen" and the cut-off date 2026-11-01
- **THEN** the settings are saved, and the password field shows only that a password is set

#### Scenario: Wrong password
- **WHEN** Anna saves the mailbox with a wrong password
- **THEN** the system rejects this with the message „Das Postfach ist nicht erreichbar oder die Anmeldung ist fehlgeschlagen“

### Requirement: Poll from the cut-off date
For an enabled mailbox, the system SHALL fetch new mails regularly, at least every 10 minutes, without user action. It MUST only consider mails received on or after the cut-off date, MUST process each mail once (by its Message-ID), and MUST NOT change, move or delete mails in the mailbox. The user SHALL be able to fetch older mails from a chosen date once.

#### Scenario: Old mails are left out
- **WHEN** the folder contains a cancellation from 2024 and the cut-off date is 2026-11-01
- **THEN** the cancellation is not fetched

#### Scenario: Fetch older mails
- **WHEN** Anna fetches older mails from 2025-01-01
- **THEN** the mails from 2025 on are processed once, and later polls do not process them again

### Requirement: Mail handlers
Parts of Logis SHALL be able to register as handlers for fetched mails. Each mail MUST be offered to the handlers in a fixed order until one takes it. A mail no handler takes MUST be listed as „nicht erkannt“ and MAY be processed again later. Handlers of a disabled module MUST NOT be asked, and fetching MUST continue while a module is disabled.

#### Scenario: Module disabled
- **WHEN** vacation rental is disabled and a booking confirmation arrives
- **THEN** the mail is fetched and listed as „nicht erkannt“, and after enabling the module and retrying it creates the booking

### Requirement: Inbox
A user SHALL see the mails fetched from their mailbox with date, sender, subject, and what the handling part reported (kind, result, linked record). They SHALL be able to open the mail text, retry processing, ignore a mail and delete it; handling parts MAY offer further actions, such as assigning a unit. Deleting MUST remove the stored content including guest data and MUST keep the mail from being fetched again. Other users MUST NOT see a user's inbox.

#### Scenario: Retry after assigning a unit
- **WHEN** Anna assigns a unit to a mail „Einheit zuordnen“
- **THEN** the mail is processed again and shows „Buchung angelegt“

#### Scenario: Delete a mail
- **WHEN** Anna deletes a mail in the inbox
- **THEN** it disappears from the list together with its content, and the next poll does not fetch it again
