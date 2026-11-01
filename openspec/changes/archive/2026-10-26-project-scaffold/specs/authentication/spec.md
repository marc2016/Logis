# Spec Delta

## Purpose

Logis holds confidential financial data and is reachable from the internet. This capability ensures that only authorized users can access the interface and API after two-factor sign-in, and it governs the creation of the first admin account.

## ADDED Requirements

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
Sign-in SHALL happen in two steps: first username and password, then a valid TOTP code or a recovery code. Only after both steps MUST a full session exist, held via an HttpOnly cookie. Until then, access to domain endpoints MUST NOT be possible. On errors in the first step, the message MUST leave open whether the username or the password was wrong.

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
A session MUST end after 30 minutes of inactivity and at the latest 12 hours after sign-in.

#### Scenario: Inactivity
- **WHEN** a signed-in user makes no request for 30 minutes and then retrieves the module list
- **THEN** the system responds with HTTP 401

#### Scenario: Absolute duration
- **WHEN** more than 12 hours have passed since sign-in, even with continuous activity
- **THEN** the system responds to the next request with HTTP 401

### Requirement: Sign out
The system SHALL offer a sign-out that ends the session on the server.

#### Scenario: Sign out
- **WHEN** the signed-in user signs out
- **THEN** they see the sign-in page, and further API calls with the old session cookie are answered with HTTP 401

### Requirement: Change own password
A signed-in user SHALL be able to change their password by providing the current password. Other sessions of the same user MUST end afterwards. If the password was reset by an admin, the user MUST change it after the next sign-in before they can use domain endpoints.

#### Scenario: Change password
- **WHEN** the user enters the current and a new valid password under „Mein Konto“
- **THEN** the new password applies, and their sessions on other devices have ended

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
