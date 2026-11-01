# Spec Delta

## Purpose

Logis is used by several people. Only an admin decides who gets access. This capability covers roles as well as creating, disabling and resetting users.

## ADDED Requirements

### Requirement: Roles
Every user MUST have exactly one of the roles `ADMIN` or `USER`. Only users with the role `ADMIN` SHALL have access to user management. Calls to user management by a `USER` MUST be answered with HTTP 403.

#### Scenario: User without admin rights
- **WHEN** a signed-in `USER` retrieves the user list via the API
- **THEN** the system responds with HTTP 403

#### Scenario: Menu entry
- **WHEN** a signed-in `USER` looks at the app shell
- **THEN** they see no entry „Benutzer“

### Requirement: No self-registration
The system MUST NOT offer a way to create an account for oneself. Users SHALL be created by an admin only.

#### Scenario: Sign-in page
- **WHEN** a visitor who is not signed in opens the sign-in page
- **THEN** there is no way to register there

### Requirement: List users
An admin SHALL see all users with username, display name, role, status (active/disabled), two-factor status (set up/pending) and time of last sign-in. Password hashes, TOTP secrets and recovery codes MUST NOT be returned.

#### Scenario: List
- **WHEN** an admin opens user management
- **THEN** they see all users with the details listed above

### Requirement: Create users
An admin SHALL be able to create a user with username, display name, role and an initial password. Usernames MUST be unique, regardless of case. The new user MUST change their password and set up two-factor authentication at first sign-in.

#### Scenario: Create
- **WHEN** an admin creates a user "anna" with role `USER` and a valid initial password
- **THEN** "anna" appears in the list as active with two-factor authentication pending

#### Scenario: Duplicate username
- **WHEN** an admin tries to create a user "Anna" and "anna" already exists
- **THEN** the system rejects this with the message „Benutzername ist bereits vergeben“

#### Scenario: First sign-in of the new user
- **WHEN** "anna" signs in for the first time with the initial password
- **THEN** they must set up two-factor authentication and set their own password before they see the overview

### Requirement: Edit users
An admin SHALL be able to change a user's display name and role.

#### Scenario: Change role
- **WHEN** an admin changes the role of "anna" to `ADMIN`
- **THEN** "anna" has access to user management on their next request

### Requirement: Disable and re-enable users
An admin SHALL be able to disable and re-enable users. Disabling MUST end every existing session of the user immediately. Users are not deleted, so that later domain data stays assigned to them.

#### Scenario: Disable
- **WHEN** an admin disables "anna" while they are signed in
- **THEN** their next request is answered with HTTP 401, and they can no longer sign in

#### Scenario: Re-enable
- **WHEN** an admin enables "anna" again
- **THEN** they can sign in again with their previous credentials and their two-factor authentication

### Requirement: Reset password and two-factor authentication
An admin SHALL be able to reset a user's password to a new initial password. They SHALL also be able to reset the user's two-factor authentication. In both cases every existing session of the user MUST end. After two-factor authentication has been reset, the user MUST set it up again at the next sign-in, and their old recovery codes MUST be invalid.

#### Scenario: Reset password
- **WHEN** an admin resets the password of "anna"
- **THEN** they can only sign in with the new initial password and must then set their own password

#### Scenario: Reset two-factor authentication
- **WHEN** an admin resets the two-factor authentication of "anna" because they lost their phone
- **THEN** "anna" must set up two-factor authentication again after entering their password

### Requirement: At least one active admin
The system MUST prevent the last active admin from being disabled or downgraded to `USER`. An admin MUST NOT be able to disable themselves.

#### Scenario: Last admin
- **WHEN** the only active admin tries to change their own role to `USER`
- **THEN** the system rejects this with the message „Es muss mindestens ein aktiver Admin bestehen bleiben“

#### Scenario: Disable oneself
- **WHEN** an admin tries to disable their own account
- **THEN** the system rejects this
