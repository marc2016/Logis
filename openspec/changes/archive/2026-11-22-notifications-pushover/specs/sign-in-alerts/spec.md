# Spec Delta

## ADDED Requirements

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
