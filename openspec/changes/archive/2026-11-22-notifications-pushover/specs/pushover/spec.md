# Spec Delta

## Purpose

Delivers notifications to users' phones through the service Pushover. An admin connects Logis to Pushover once; every user decides with their own key whether they receive anything this way.

## ADDED Requirements

### Requirement: Configure the Pushover application
On the page „Pushover“ under *Einstellungen*, an admin SHALL be able to store one Pushover application token for the whole system and to remove it. The token MUST be stored encrypted and MUST never be returned; the page MUST only show that a token is set. Users without the role `ADMIN` MUST NOT see or change the configuration. The page SHALL say that short texts of notifications are sent to Pushover.

#### Scenario: Store the token
- **WHEN** the admin enters a valid application token and saves
- **THEN** the page shows that a token is set, and the token itself is not shown

#### Scenario: Ordinary user
- **WHEN** a user without the role `ADMIN` requests the configuration
- **THEN** the system responds with HTTP 403

#### Scenario: Remove the token
- **WHEN** the admin removes the token
- **THEN** no notification is sent through Pushover any more, and the users' keys and switches are kept

### Requirement: Check the token on save
Saving MUST check the application token with Pushover. A token Pushover rejects MUST be rejected with „Das Pushover-Token ist ungültig“. If Pushover cannot be reached, saving MUST be rejected with „Pushover ist nicht erreichbar“. In both cases the previous configuration MUST stay in effect.

#### Scenario: Wrong token
- **WHEN** the admin saves a token Pushover does not know
- **THEN** the system rejects it with „Das Pushover-Token ist ungültig“, and the previous token is still used

#### Scenario: Pushover unreachable
- **WHEN** the admin saves a token while Pushover cannot be reached
- **THEN** the system rejects it with „Pushover ist nicht erreichbar“

### Requirement: Own Pushover key
Under „Mein Konto“, a user SHALL be able to store their own Pushover user key and to remove it, once an admin has stored an application token. Saving MUST check the key with Pushover and reject an unknown key with „Der Pushover-Schlüssel ist ungültig“. The key MUST be stored encrypted and MUST never be returned. A user MUST only set and remove their own key. Without an application token, the card SHALL say „Pushover ist noch nicht eingerichtet“ and offer no input.

#### Scenario: Store the key
- **WHEN** Anna enters her Pushover user key and saves
- **THEN** the card shows that a key is set, and the switches in the column „Pushover“ can be used

#### Scenario: Unknown key
- **WHEN** Anna saves a key Pushover does not know
- **THEN** the system rejects it with „Der Pushover-Schlüssel ist ungültig“, and no key is stored

#### Scenario: Remove the key
- **WHEN** Anna removes her key
- **THEN** she gets no Pushover notifications, and her switches are kept for a later key

#### Scenario: Not set up
- **WHEN** no application token is stored and Anna opens „Mein Konto“
- **THEN** the card says „Pushover ist noch nicht eingerichtet“

### Requirement: Test notification
A user with a stored key SHALL be able to choose „Testnachricht senden“. The system MUST send the notification „Testnachricht von Logis“ to that user through Pushover at once and MUST report a failure with „Die Testnachricht konnte nicht gesendet werden“.

#### Scenario: Send a test
- **WHEN** Anna chooses „Testnachricht senden“
- **THEN** her devices show „Testnachricht von Logis“

#### Scenario: Pushover unreachable
- **WHEN** Anna chooses „Testnachricht senden“ while Pushover cannot be reached
- **THEN** the page shows „Die Testnachricht konnte nicht gesendet werden“

### Requirement: What is sent to Pushover
The Pushover channel MUST send only the application token, the user's key, the title, the short text and the link of a notification. It MUST NOT send the long text, the user's name or e-mail address, or client addresses. Titles and texts longer than Pushover accepts MUST be shortened with „…“ rather than rejected.

#### Scenario: Booking awaiting review
- **WHEN** a booking of "Haus Seeufer" from 2027-07-12 to 2027-07-19 awaits review and Anna has switched on Pushover
- **THEN** Pushover receives the title „Neue Buchung zu prüfen“ and a text naming "Haus Seeufer" and the period, and no guest name

#### Scenario: Long text
- **WHEN** the short text of a digest exceeds the length Pushover accepts
- **THEN** it is sent shortened and ends with „…“

### Requirement: Keys Pushover no longer accepts
If Pushover answers that a user's key is no longer valid, the system MUST stop sending to that key and MUST show „Der Pushover-Schlüssel ist nicht mehr gültig“ on the user's card until the user stores a key again. Other failures (Pushover unreachable, temporary errors, limit reached) MUST NOT change the stored key, and the notification is not sent again.

#### Scenario: Key revoked
- **WHEN** Anna deleted her Pushover account and an event occurs for her
- **THEN** the next visit of „Mein Konto“ shows „Der Pushover-Schlüssel ist nicht mehr gültig“, and later events are not sent to that key

#### Scenario: Temporary failure
- **WHEN** Pushover answers an event with a server error
- **THEN** Anna's key stays valid, and the next event is sent as usual
