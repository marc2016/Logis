# authentication Specification

## Purpose

Logis holds confidential financial data and is reachable from the internet. This capability ensures that only authorized users can access the interface and API after two-factor sign-in, and it governs the creation of the first admin account.

## Requirements

### Requirement: Initial admin
If no user exists at startup, the system SHALL create a user with the role `ADMIN` using the username and password from the deployment configuration. If this configuration is missing or the password is shorter than 12 characters while no user exists yet, startup MUST abort with an understandable error message. If a user already exists, the system MUST ignore the configuration and change no user data.

#### Scenario: First start
- **WHEN** the application starts with an empty database, a configured admin username and a valid password
- **THEN** an admin with these credentials exists afterwards, for whom two-factor authentication is not yet set up

#### Scenario: First start without configuration
- **WHEN** the application starts with an empty database and without admin configuration
- **THEN** startup aborts, and the log names the missing settings

#### Scenario: Password too short
- **WHEN** the application starts with an empty database and an admin password of 8 characters
- **THEN** startup aborts, and the log names the minimum length

#### Scenario: Later start with a changed password in the configuration
- **WHEN** a user already exists and the application starts with a different configured admin password
- **THEN** the stored password stays unchanged

### Requirement: Password rules and secure storage
Passwords MUST be at least 12 characters long. They MUST be stored only as a salted hash using a scheme designed for passwords. They MUST NOT be stored in plain text, logged or returned by the API.

#### Scenario: Stored password
- **WHEN** a user has been created
- **THEN** the database contains no plain-text password for them

#### Scenario: Password too short is rejected
- **WHEN** a user tries to set a new password with 11 characters
- **THEN** the system rejects this with a message about the minimum length

### Requirement: Two-step sign-in
Sign-in with a password SHALL happen in two steps: first username and password, then a valid TOTP code or a recovery code. Only after both steps MUST a full session exist, held via an HttpOnly cookie. Until then, access to domain endpoints MUST NOT be possible. On errors in the first step, the message MUST leave open whether the username or the password was wrong. Alternatively, a passkey SHALL sign in in a single step (see „Sign-in with a passkey“).

#### Scenario: Successful sign-in
- **WHEN** a user with two-factor authentication set up enters valid credentials and then a valid TOTP code
- **THEN** they are signed in and see the overview in the app shell

#### Scenario: Password only
- **WHEN** a user has entered valid credentials but not yet confirmed a TOTP code, and retrieves the module list
- **THEN** the system responds with HTTP 401

#### Scenario: Wrong password
- **WHEN** the user enters a valid username with a wrong password
- **THEN** they stay signed out and see the message „Benutzername oder Passwort ist falsch“

#### Scenario: Unknown user
- **WHEN** the user enters an unknown username
- **THEN** they see the same message as for a wrong password

#### Scenario: Wrong TOTP code
- **WHEN** the user enters a wrong code in the second step
- **THEN** they stay signed out, see the message „Code ist ungültig“ and can try again

#### Scenario: Reused TOTP code
- **WHEN** a TOTP code that the user has already signed in with successfully is used again within its validity
- **THEN** the system rejects it

#### Scenario: Disabled user
- **WHEN** a disabled user enters correct credentials
- **THEN** they stay signed out and see the same message as for wrong credentials

### Requirement: Mandatory two-factor setup
A user without two-factor authentication set up SHALL have to go through setup after the first step. The system MUST show a secret as a QR code and as text. Setup MUST only be complete after confirmation with a valid code from the authenticator app. After that, the system MUST show 10 recovery codes exactly once. Before setup is complete, access to domain endpoints MUST NOT be possible.

#### Scenario: Admin's first sign-in
- **WHEN** the initial admin signs in for the first time with username and password
- **THEN** they see the setup with a QR code instead of the overview

#### Scenario: Complete setup
- **WHEN** the user confirms a valid code from the authenticator app
- **THEN** they see 10 recovery codes with a note to keep them safe, and are fully signed in afterwards

#### Scenario: Setup with a wrong code
- **WHEN** the user enters a wrong code during setup
- **THEN** two-factor authentication is not set up, and the user can try again

### Requirement: Recovery codes
Each recovery code SHALL be usable exactly once in the second sign-in step instead of a TOTP code. The codes MUST be stored only as hashes. A user SHALL be able to generate new codes, which invalidates all previous ones.

#### Scenario: Sign-in with a recovery code
- **WHEN** the user enters an unused recovery code in the second step
- **THEN** they are signed in, and this code is invalid afterwards

#### Scenario: Generate new codes
- **WHEN** the signed-in user generates new recovery codes under „Mein Konto“
- **THEN** they see 10 new codes, and none of the old codes works anymore

### Requirement: Brute-force protection
After 5 consecutive failed sign-in attempts for a user, whether in the password or the code step, the system MUST temporarily block further attempts for that user. The lockout SHALL grow with each further failure, up to at most 15 minutes. A successful sign-in MUST reset the counter. In addition, the number of sign-in requests per client IP MUST be limited. Failed sign-ins MUST be logged with username, client IP and time, but without password or code.

#### Scenario: Lockout after failed attempts
- **WHEN** 5 sign-in attempts for a user fail in a row and the correct credentials are entered afterwards
- **THEN** the sign-in is rejected, and the user sees „Zu viele Fehlversuche, bitte später erneut versuchen“

#### Scenario: End of lockout
- **WHEN** the lockout period has expired and the user enters correct credentials
- **THEN** the sign-in continues

#### Scenario: Many requests from one IP
- **WHEN** a client IP sends a very large number of sign-in requests in a short time
- **THEN** further requests from that IP are rejected with HTTP 429

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

### Requirement: Protected API
All endpoints under `/api/` except those of the sign-in steps, the session query and health MUST require a full session. Without one, the system SHALL respond with HTTP 401 and MUST NOT redirect to an HTML sign-in page.

#### Scenario: Call without a session
- **WHEN** a client without a session retrieves the module list
- **THEN** the system responds with HTTP 401

### Requirement: Cross-site request forgery protection
State-changing API calls (POST, PUT, PATCH, DELETE) MUST send a valid CSRF token. Otherwise the system SHALL reject the call with HTTP 403.

#### Scenario: Call without a token
- **WHEN** a client with a valid session but without a CSRF token sends a POST call to the API
- **THEN** the system responds with HTTP 403

### Requirement: Redirect to sign-in in the interface
If a user who is not signed in opens a page of the application, the interface SHALL show the sign-in. After a successful sign-in it MUST return to the originally requested page.

#### Scenario: Deep link without a session
- **WHEN** a user who is not signed in opens the URL of the occupancy page and then signs in completely
- **THEN** they see the occupancy page after signing in

#### Scenario: Session expires
- **WHEN** the session has expired and the interface receives a response with HTTP 401
- **THEN** the interface shows the sign-in

### Requirement: Sign-in without second factor in development
For local development, the system SHALL offer a setting that lets a correct username and password complete the sign-in without the TOTP step and without TOTP setup. A required change of the start password MUST still be asked for. Existing TOTP secrets and recovery codes MUST stay unchanged. The setting MUST be off by default and SHALL only be switched on in the development profile. If it is on while the development profile is not active, the system MUST refuse to start. While it is on, the system MUST log a warning at startup.

#### Scenario: Local sign-in
- **WHEN** the backend runs with the development profile and the setting on, and Anna enters her correct username and password
- **THEN** she is signed in and sees the overview, without being asked for a code

#### Scenario: Start password still has to be changed
- **WHEN** the setting is on in development and a new user signs in with the start password set by the administrator
- **THEN** they are asked to choose their own password before they get access

#### Scenario: Wrong password
- **WHEN** the setting is on in development and Anna enters a wrong password
- **THEN** she stays signed out and sees „Benutzername oder Passwort ist falsch“

#### Scenario: Setting outside development
- **WHEN** the backend is started with the setting on but without the development profile
- **THEN** it does not start and reports that the setting is only allowed in development

#### Scenario: Production
- **WHEN** the backend runs in production, where the setting is not set
- **THEN** sign-in requires a TOTP code or a recovery code as before

### Requirement: Leave an unfinished sign-in
After the password step, the TOTP code step, the two-factor setup and the required password change SHALL offer „Zurück zur Anmeldung“. Choosing it MUST end the unfinished sign-in on the server and SHALL show the empty password step. Reloading the page afterwards MUST show the password step, not the step that was left. Failed attempts that already counted towards the lockout MUST stay counted.

#### Scenario: Wrong account
- **WHEN** Anna has entered the password of the wrong account and is asked for a code, and chooses „Zurück zur Anmeldung“
- **THEN** she sees the empty password step and can sign in with another account

#### Scenario: Reload after leaving
- **WHEN** Anna chooses „Zurück zur Anmeldung“ on the code step and reloads the page
- **THEN** she still sees the password step

#### Scenario: Leave the two-factor setup
- **WHEN** a new user is in the two-factor setup and chooses „Zurück zur Anmeldung“
- **THEN** two-factor authentication is not set up, and they see the password step

### Requirement: Sign-in with a passkey
If passkeys are configured, the sign-in page SHALL offer „Mit Passkey anmelden“. A passkey registered by the user, used with user verification on the device (for example Face ID, Touch ID or a PIN), SHALL sign the user in completely without password or TOTP code. If the user has to change their start password, this MUST still be asked for before access to domain endpoints. A passkey of a disabled user, an unknown passkey or a failed verification MUST NOT sign in, and the message MUST be „Anmeldung mit Passkey fehlgeschlagen“. Failed passkey sign-ins of a known user MUST count towards the lockout like failed password or code attempts, a locked user MUST NOT be signed in with a passkey, and the per-IP limit of sign-in requests MUST apply. A passkey MUST only work on the configured domain.

#### Scenario: Sign in with a passkey
- **WHEN** Anna chooses „Mit Passkey anmelden“ and confirms with Touch ID on her MacBook, where she has added a passkey
- **THEN** she is signed in and sees the overview, without entering password or code

#### Scenario: Cancelled on the device
- **WHEN** Anna chooses „Mit Passkey anmelden“ and cancels the prompt on her device
- **THEN** she stays on the sign-in page and can sign in with password and code

#### Scenario: Disabled user
- **WHEN** a disabled user signs in with their passkey
- **THEN** they stay signed out and see „Anmeldung mit Passkey fehlgeschlagen“

#### Scenario: Locked user
- **WHEN** Anna is locked after 5 failed attempts and then uses her passkey
- **THEN** the sign-in is rejected with „Zu viele Fehlversuche, bitte später erneut versuchen“

#### Scenario: Not configured
- **WHEN** no public address is configured
- **THEN** the sign-in page offers no passkey sign-in, and sign-in with password and code works as before

### Requirement: Manage passkeys
A fully signed-in user SHALL be able to add passkeys under „Mein Konto“ after confirming their current password, and give each a name of at most 64 characters. They SHALL see their passkeys with name, creation date and last use, and SHALL be able to rename and delete them. A user MUST only see and change their own passkeys. Adding a passkey MUST NOT be possible before two-factor setup is complete, and having passkeys MUST NOT remove the need for two-factor setup, TOTP or recovery codes. Data that identifies the authenticator beyond the public key, signature counter and transports MUST NOT be stored.

#### Scenario: Add a passkey
- **WHEN** Anna chooses „Passkey hinzufügen“ under „Mein Konto“, enters her current password, names it „MacBook“ and confirms with Touch ID
- **THEN** „MacBook“ appears in her list of passkeys with today's date

#### Scenario: Wrong password
- **WHEN** Anna enters a wrong current password when adding a passkey
- **THEN** no passkey is added, and she sees „Das aktuelle Passwort ist falsch.“

#### Scenario: Delete a passkey
- **WHEN** Anna deletes the passkey „Altes iPhone“
- **THEN** it disappears from her list and can no longer be used to sign in

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

### Requirement: Own e-mail address
Under „Mein Konto“, a fully signed-in user SHALL see their e-mail address and SHALL be able to change it. The change MUST be confirmed with a 6-digit code that Logis sends to the new address. The address MUST only change once the code is entered. A code MUST be valid for 30 minutes and for 5 attempts, and a new request MUST replace an earlier code.

#### Scenario: Change the address
- **WHEN** Anna enters „anna.neu@example.org“ and then the code from the mail sent to that address
- **THEN** „anna.neu@example.org“ is their address

#### Scenario: Wrong code
- **WHEN** Anna enters a wrong code
- **THEN** the address stays unchanged, and they see „Der Code ist ungültig oder abgelaufen.“

#### Scenario: Too many attempts
- **WHEN** Anna has entered a wrong code 5 times
- **THEN** the code is no longer accepted, even if correct, and Anna has to request a new one

#### Scenario: Expired code
- **WHEN** Anna enters the code 31 minutes after requesting it
- **THEN** the address stays unchanged, and they see „Der Code ist ungültig oder abgelaufen.“

### Requirement: Notice to the old address
After a user changed their own address, Logis SHALL send the notice „Deine E-Mail-Adresse in Logis wurde geändert.“ to the previous address, if there was one, naming the new address only partially (for example „a…@example.org“). A failure to send this notice MUST NOT undo the change.

#### Scenario: Notice sent
- **WHEN** Anna changes their address from „anna@example.org“ to „anna.neu@example.org“
- **THEN** „anna@example.org“ receives the notice, which does not contain the full new address

### Requirement: Address change needs outgoing mail
If no outgoing mail server is configured, users MUST NOT be able to change their own address. The card SHALL show „Der E-Mail-Versand ist nicht eingerichtet. Bitte wende dich an einen Admin.“ instead of the change form.

#### Scenario: No outgoing mail server
- **WHEN** no outgoing mail server is configured and Anna opens „Mein Konto“
- **THEN** they see their address and the notice, but no way to change it

### Requirement: Notice about a missing address
A signed-in user without an e-mail address SHALL see the notice „Bitte hinterlege eine E-Mail-Adresse unter Mein Konto.“ in the app, with a link to „Mein Konto“, until they have an address.

#### Scenario: Existing user without an address
- **WHEN** Anna, created before e-mail addresses existed, signs in
- **THEN** they see the notice with the link to „Mein Konto“

#### Scenario: Address added
- **WHEN** Anna has confirmed an address
- **THEN** the notice no longer appears
