## MODIFIED Requirements

### Requirement: Protection of remembered devices
The credential of a remembered device MUST change each time it starts a session. If a credential that was already replaced is presented again, the system MUST forget that device and respond with HTTP 401. Requests sent at the same time within one minute MUST NOT count as reuse. A credential presented by a different browser or operating system than the one it was issued to MUST be rejected, and the device forgotten. A credential presented from a country other than the one it was issued in MUST be rejected likewise; if either country is unknown, the country is not compared. The system MUST store credentials only in a form that cannot be used to sign in.

#### Scenario: Copied credential used later
- **WHEN** a copy of Anna's device credential is presented after Anna's device has already used it
- **THEN** the system responds with HTTP 401, and Anna's device must sign in again too

#### Scenario: Parallel requests
- **WHEN** Anna opens a page on a remembered device after the session expired, and the page sends several requests at once
- **THEN** all requests succeed

#### Scenario: Other browser
- **WHEN** a credential issued to Firefox on Windows is presented by Chrome on Android
- **THEN** the system responds with HTTP 401 and forgets the device

#### Scenario: Other country
- **WHEN** a credential issued in Germany is presented from Brazil by the same browser
- **THEN** the system responds with HTTP 401 and forgets the device

### Requirement: Manage signed-in devices
A fully signed-in user SHALL see their remembered devices under „Mein Konto“ in the card „Angemeldete Geräte“, each with browser, operating system, sign-in date, last use and the location of the last use if known, and the current device marked „Dieses Gerät“. They SHALL be able to sign out each device with „Abmelden“, and SHALL be able to end all other remembered devices and all other sessions with „Alle anderen Geräte abmelden“. A user MUST only see and change their own devices.

#### Scenario: List devices
- **WHEN** Anna signed in with „Angemeldet bleiben“ on a laptop and a phone and opens „Mein Konto“ on the laptop
- **THEN** they see both devices, and the laptop is marked „Dieses Gerät“

#### Scenario: Location of the last use
- **WHEN** Anna's phone was last used in Hamburg
- **THEN** the phone's entry shows „Hamburg, Deutschland“

#### Scenario: Sign out another device
- **WHEN** Anna chooses „Abmelden“ for the phone
- **THEN** the phone disappears from the list, and its next request without a valid session is answered with HTTP 401

#### Scenario: Sign out all other devices
- **WHEN** Anna chooses „Alle anderen Geräte abmelden“ on the laptop
- **THEN** all other devices and sessions have ended, and the laptop stays signed in

#### Scenario: Foreign device
- **WHEN** Anna tries to sign out a device of another user
- **THEN** the system responds with HTTP 404
