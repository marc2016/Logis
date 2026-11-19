## ADDED Requirements

### Requirement: Stay signed in
The sign-in page SHALL offer the checkbox „Angemeldet bleiben“, off by default, for sign-in with password and code and for sign-in with a passkey. If it is checked when the sign-in completes, the system SHALL remember the device: when the device has no valid session, its next request MUST start a new full session without password, code or passkey. If it is not checked, the sign-in MUST behave as before.

#### Scenario: Session expired on a remembered device
- **WHEN** Anna signed in with „Angemeldet bleiben“, makes no request for 2 hours and then opens the overview
- **THEN** they see the overview without signing in

#### Scenario: Backend restart
- **WHEN** Anna signed in with „Angemeldet bleiben“ and the backend is restarted
- **THEN** their next request succeeds without signing in

#### Scenario: Without the checkbox
- **WHEN** Anna signs in without „Angemeldet bleiben“ and makes no request for 30 minutes
- **THEN** the system responds to the next request with HTTP 401

#### Scenario: Passkey sign-in
- **WHEN** Anna checks „Angemeldet bleiben“ and signs in with a passkey
- **THEN** the device is remembered as after sign-in with password and code

#### Scenario: Start password not yet changed
- **WHEN** a new user checks „Angemeldet bleiben“ and leaves the sign-in at the required password change
- **THEN** the device is not remembered

### Requirement: Duration of a remembered device
A remembered device MUST be forgotten 30 days after it last started a session or made a request, and at the latest 90 days after the sign-in that remembered it. After that, the user MUST sign in with password and code or a passkey.

#### Scenario: Unused for 30 days
- **WHEN** Anna's remembered device makes no request for more than 30 days
- **THEN** the next request without a valid session is answered with HTTP 401

#### Scenario: Regular use
- **WHEN** Anna uses a remembered device every day
- **THEN** they stay signed in until 90 days after the sign-in, and then have to sign in again

### Requirement: Protection of remembered devices
The credential of a remembered device MUST change each time it starts a session. If a credential that was already replaced is presented again, the system MUST forget that device and respond with HTTP 401. Requests sent at the same time within one minute MUST NOT count as reuse. A credential presented by a different browser or operating system than the one it was issued to MUST be rejected, and the device forgotten. The system MUST store credentials only in a form that cannot be used to sign in.

#### Scenario: Copied credential used later
- **WHEN** a copy of Anna's device credential is presented after Anna's device has already used it
- **THEN** the system responds with HTTP 401, and Anna's device must sign in again too

#### Scenario: Parallel requests
- **WHEN** Anna opens a page on a remembered device after the session expired, and the page sends several requests at once
- **THEN** all requests succeed

#### Scenario: Other browser
- **WHEN** a credential issued to Firefox on Windows is presented by Chrome on Android
- **THEN** the system responds with HTTP 401 and forgets the device

### Requirement: Manage signed-in devices
A fully signed-in user SHALL see their remembered devices under „Mein Konto“ in the card „Angemeldete Geräte“, each with browser, operating system, sign-in date and last use, and the current device marked „Dieses Gerät“. They SHALL be able to sign out each device with „Abmelden“, and SHALL be able to end all other remembered devices and all other sessions with „Alle anderen Geräte abmelden“. A user MUST only see and change their own devices.

#### Scenario: List devices
- **WHEN** Anna signed in with „Angemeldet bleiben“ on a laptop and a phone and opens „Mein Konto“ on the laptop
- **THEN** they see both devices, and the laptop is marked „Dieses Gerät“

#### Scenario: Sign out another device
- **WHEN** Anna chooses „Abmelden“ for the phone
- **THEN** the phone disappears from the list, and its next request without a valid session is answered with HTTP 401

#### Scenario: Sign out all other devices
- **WHEN** Anna chooses „Alle anderen Geräte abmelden“ on the laptop
- **THEN** all other devices and sessions have ended, and the laptop stays signed in

#### Scenario: Foreign device
- **WHEN** Anna tries to sign out a device of another user
- **THEN** the system responds with HTTP 404

### Requirement: Fresh sign-in for the admin area
A session started by a remembered device MUST NOT have access to the admin area. For admin endpoints the system SHALL respond with HTTP 403, and the interface SHALL show „Für den Admin-Bereich bitte erneut anmelden“ with the button „Erneut anmelden“. Domain endpoints MUST stay accessible. After signing in again, the admin area MUST be accessible.

#### Scenario: Admin on a remembered device
- **WHEN** an admin's session expired and was started again by the remembered device, and they open the user management
- **THEN** they see „Für den Admin-Bereich bitte erneut anmelden“ instead of the users

#### Scenario: Sign in again
- **WHEN** the admin chooses „Erneut anmelden“ and signs in with password and code
- **THEN** they see the user management

#### Scenario: Domain data stays accessible
- **WHEN** an admin's session was started by the remembered device and they open the overview
- **THEN** they see the overview

## MODIFIED Requirements

### Requirement: Session duration
A session MUST end after 30 minutes of inactivity and at the latest 12 hours after sign-in. On a remembered device, the next request after the end of a session SHALL start a new session (see „Stay signed in“).

#### Scenario: Inactivity
- **WHEN** a signed-in user makes no request for 30 minutes and then retrieves the module list
- **THEN** the system responds with HTTP 401

#### Scenario: Absolute duration
- **WHEN** more than 12 hours have passed since sign-in, even with continuous activity
- **THEN** the system responds to the next request with HTTP 401

#### Scenario: Remembered device
- **WHEN** more than 12 hours have passed since sign-in on a remembered device
- **THEN** the next request starts a new session and succeeds

### Requirement: Sign out
The system SHALL offer a sign-out that ends the session on the server. Signing out MUST also forget the current device if it is remembered.

#### Scenario: Sign out
- **WHEN** the signed-in user signs out
- **THEN** they see the sign-in page, and further API calls with the old session cookie are answered with HTTP 401

#### Scenario: Sign out on a remembered device
- **WHEN** Anna signs out on a remembered device and opens the overview again
- **THEN** they see the sign-in page, and the device no longer appears under „Angemeldete Geräte“

### Requirement: Change own password
A signed-in user SHALL be able to change their password by providing the current password. Other sessions and other remembered devices of the same user MUST end afterwards; the current device MUST stay signed in and, if it was remembered, stay remembered. If the password was reset by an admin, the user MUST change it after the next sign-in before they can use domain endpoints.

#### Scenario: Change password
- **WHEN** the user enters the current and a new valid password under „Mein Konto“
- **THEN** the new password applies, and their sessions on other devices have ended

#### Scenario: Remembered devices after a password change
- **WHEN** Anna changes their password on a remembered laptop while a phone is remembered too
- **THEN** the phone must sign in again, and the laptop stays remembered

#### Scenario: Wrong current password
- **WHEN** the user provides a wrong current password
- **THEN** the password stays unchanged

#### Scenario: Forced change
- **WHEN** a user whose password an admin has reset signs in
- **THEN** they must set a new password after two-factor sign-in before they see the overview
