# Spec Delta

## ADDED Requirements

### Requirement: Notify about bookings awaiting review
When a mail fetched by the regular poll creates a booking „zu prüfen“, the system SHALL raise the notification „Neue Buchung zu prüfen“; when it proposes a change or a cancellation, the notification „Änderung oder Stornierung zu prüfen“. Recipients MUST be the users with at least `EDITOR` on the booking's unit. Both are event types of the group „Ferienvermietung“. Mails fetched as older mails, mails processed again and mails received more than two days before MUST NOT raise them.

#### Scenario: New booking
- **WHEN** the poll imports a confirmation for "Haus Seeufer" from 2027-07-12 to 2027-07-19 and Anna, `OWNER` of the property, switched on Pushover for „Neue Buchung zu prüfen“
- **THEN** Anna gets „Neue Buchung zu prüfen“ naming "Haus Seeufer", the period and the agency, with a link to the booking

#### Scenario: Proposed cancellation
- **WHEN** the poll imports a cancellation for a known booking and Anna switched on „Änderung oder Stornierung zu prüfen“
- **THEN** Anna gets a notification that says a cancellation is proposed

#### Scenario: Viewer
- **WHEN** Bert is `VIEWER` of "Haus Seeufer" and a booking for it awaits review
- **THEN** Bert gets no notification, because he cannot confirm it

#### Scenario: Older mails
- **WHEN** Anna fetches older mails from 2025-01-01 and they create 40 bookings „zu prüfen“
- **THEN** no notification is sent

#### Scenario: Mail text
- **WHEN** Anna switched on „E-Mail“ for „Neue Buchung zu prüfen“ and a booking is imported
- **THEN** the mail names the property, the period, the agency, the guest, the number of guests and the expected rent

### Requirement: Arrival and departure digest
Every day at 18:00 (Europe/Berlin), the system SHALL raise the notifications „Anreisen morgen“ and „Abreisen morgen“ in the group „Ferienvermietung“, each as one notification per user listing the guest bookings that arrive or depart on the following day on units the user has a role on. Cancelled bookings, bookings „zu prüfen“ and owner uses MUST be left out. A user without such a booking MUST NOT get the notification.

#### Scenario: Two arrivals
- **WHEN** bookings on "Whg. 1" and "Whg. 2" arrive tomorrow and Anna, with a role on both, switched on Pushover for „Anreisen morgen“
- **THEN** at 18:00 Anna gets one notification „Anreisen morgen“ listing both units

#### Scenario: Nothing tomorrow
- **WHEN** no booking of Anna's units arrives tomorrow
- **THEN** Anna gets no „Anreisen morgen“

#### Scenario: Unconfirmed and cancelled bookings
- **WHEN** tomorrow's only arrivals are a booking „zu prüfen“ and a cancelled booking
- **THEN** no digest is sent

#### Scenario: Only own units
- **WHEN** Carla has a role on "Whg. 2" only, and bookings on "Whg. 1" and "Whg. 2" depart tomorrow
- **THEN** Carla's „Abreisen morgen“ lists only "Whg. 2"

#### Scenario: Guest names only by mail
- **WHEN** Anna switched on both channels for „Anreisen morgen“
- **THEN** the mail lists unit, guest and number of guests per arrival, and the Pushover notification lists only the units

### Requirement: Digest once per day
Each digest MUST be sent at most once per day. If Logis was not running at 18:00, the digests SHALL be sent when Logis runs again on the same day; a missed day MUST NOT be made up for on a later day.

#### Scenario: Restart in the evening
- **WHEN** Logis is restarted at 18:30 after the digests were sent at 18:00
- **THEN** no digest is sent a second time

#### Scenario: Down at 18:00
- **WHEN** Logis was stopped at 17:50 and is started again at 19:10 on the same day
- **THEN** the digests for the following day are sent after the start

#### Scenario: Down over midnight
- **WHEN** Logis was stopped at 17:50 and is started again the next morning
- **THEN** the missed digests are not sent
