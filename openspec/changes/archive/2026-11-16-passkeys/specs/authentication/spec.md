## ADDED Requirements

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

## MODIFIED Requirements

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
