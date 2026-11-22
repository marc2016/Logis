# sign-in-alerts Specification

## Purpose

Tells users by e-mail when their account is signed in from a device or country they have not used before, so that a stolen password, passkey or device cookie is noticed quickly.

## Requirements

### Requirement: Recognise devices
Every complete sign-in SHALL give the device a long-lived device identifier, valid for one year and independent of „Angemeldet bleiben“. A complete sign-in MUST count as being from a new device if the device presents no identifier that this user's earlier sign-ins gave it. Signing out MUST NOT remove the device identifier.

#### Scenario: Same browser again
- **WHEN** Anna signs in on their laptop, signs out, and signs in again on the same laptop
- **THEN** the second sign-in is not from a new device

#### Scenario: Other browser
- **WHEN** Anna, who always signed in with Firefox, signs in with Chrome for the first time
- **THEN** the sign-in is from a new device

### Requirement: Locate sign-ins
The system SHALL determine the country and city of each complete sign-in from the client address, using a local database only. Client addresses MUST NOT be sent to any third party. Addresses from local or private networks, and addresses the database does not know, MUST have no location. A complete sign-in MUST count as being from a new location if it has a country and the user had no sign-in from that country in the last 180 days.

#### Scenario: New country
- **WHEN** Anna, who signed in only from Germany in the last 180 days, signs in from Spain
- **THEN** the sign-in is from a new location

#### Scenario: Other city, same country
- **WHEN** Anna signs in from Munich after signing in from Hamburg before
- **THEN** the sign-in is not from a new location

#### Scenario: Local network
- **WHEN** Anna signs in from the address 192.168.1.20
- **THEN** the sign-in has no location and is not from a new location

### Requirement: Warning mail
After a complete sign-in from a new device or a new location, the system SHALL send the mail „Neue Anmeldung bei Logis“ to the user's e-mail address. The first complete sign-in of a user MUST NOT send a warning. Users without an address MUST NOT get a mail. If no outgoing mail server is configured, nothing is sent. The sign-in MUST NOT wait for or fail because of the mail.

#### Scenario: New device
- **WHEN** Anna signs in with Chrome for the first time, after signing in with Firefox before
- **THEN** Anna receives „Neue Anmeldung bei Logis“ naming „Chrome“ and the operating system

#### Scenario: Known device and country
- **WHEN** Anna signs in again on the laptop they use every day, from Germany
- **THEN** no mail is sent

#### Scenario: First sign-in
- **WHEN** a new user signs in for the first time
- **THEN** no warning is sent

#### Scenario: Mail server down
- **WHEN** Anna signs in from a new device while the outgoing mail server is unreachable
- **THEN** the sign-in succeeds as usual

### Requirement: Content of the warning mail
The warning mail SHALL name the time of the sign-in, the browser and operating system, the location or „unbekannt“, and what to do if it was not the user: change the password and choose „Alle anderen Geräte abmelden“ under „Mein Konto“. It MUST NOT contain the client address.

#### Scenario: Content
- **WHEN** Anna signs in with Chrome on Android from Hamburg as a new device
- **THEN** the mail names the time, „Chrome auf Android“, „Hamburg, Deutschland“ and the advice

### Requirement: Sign-in history
For every complete sign-in the system SHALL store the user, the time, a hash of the device identifier, browser, operating system, country and city. It MUST NOT store the client address. Entries older than 180 days MUST be deleted.

#### Scenario: No addresses stored
- **WHEN** Anna signs in from 203.0.113.7
- **THEN** the stored entry contains no part of that address

#### Scenario: Old entries
- **WHEN** an entry is older than 180 days
- **THEN** it is deleted at the next daily clean-up

### Requirement: Warning as a notification
The warning SHALL be the event type „Neue Anmeldung“ in the group „Sicherheit“ of the user's notifications. Its channel „E-Mail“ MUST be mandatory: the warning mail is sent as before, and a user MUST NOT be able to switch it off.

#### Scenario: Mail cannot be switched off
- **WHEN** Anna opens „Benachrichtigungen“
- **THEN** the switch „E-Mail“ for „Neue Anmeldung“ is on and disabled

#### Scenario: Unchanged for existing users
- **WHEN** Anna, who never opened „Benachrichtigungen“, signs in from a new device after the update
- **THEN** she receives the mail „Neue Anmeldung bei Logis“ with the same content as before

### Requirement: Warning by Pushover
A user SHALL be able to switch on Pushover for „Neue Anmeldung“. The system MUST then send, in addition to the mail and under the same conditions, a notification with the title „Neue Anmeldung bei Logis“ whose text names the browser, the operating system and the location or „unbekannt“. It MUST NOT contain the client address. A user without an e-mail address MUST still get it.

#### Scenario: New device with Pushover
- **WHEN** Anna, who switched on Pushover for „Neue Anmeldung“, signs in with Chrome on Android from Hamburg as a new device
- **THEN** her phone shows „Neue Anmeldung bei Logis“ with „Chrome auf Android, Hamburg, Deutschland“, and she also receives the mail

#### Scenario: Not switched on
- **WHEN** Bert, who did not switch on Pushover for „Neue Anmeldung“, signs in from a new device
- **THEN** he receives only the mail

#### Scenario: No e-mail address
- **WHEN** Carla, who has no e-mail address and switched on Pushover for „Neue Anmeldung“, signs in from a new device
- **THEN** she gets the Pushover notification
