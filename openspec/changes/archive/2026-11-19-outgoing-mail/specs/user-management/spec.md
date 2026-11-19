## MODIFIED Requirements

### Requirement: Create users
An admin SHALL be able to create a user with username, display name, e-mail address, role and an initial password. Usernames MUST be unique, regardless of case. The e-mail address MUST be given and MUST be a valid address. The new user MUST change their password and set up two-factor authentication at first sign-in.

#### Scenario: Create
- **WHEN** an admin creates a user "anna" with role `USER`, the address „anna@example.org“ and a valid initial password
- **THEN** "anna" appears in the list as active with two-factor authentication pending and with the address

#### Scenario: Duplicate username
- **WHEN** an admin tries to create a user "Anna" and "anna" already exists
- **THEN** the system rejects this with the message „Benutzername ist bereits vergeben“

#### Scenario: Missing e-mail address
- **WHEN** an admin tries to create a user without an e-mail address
- **THEN** the system rejects this with the message „Bitte eine gültige E-Mail-Adresse angeben.“

#### Scenario: First sign-in of the new user
- **WHEN** "anna" signs in for the first time with the initial password
- **THEN** they must set up two-factor authentication and set their own password before they see the overview

### Requirement: Edit users
An admin SHALL be able to change a user's display name, role and e-mail address. A changed address MUST be a valid address. An address set by an admin takes effect without confirmation.

#### Scenario: Change role
- **WHEN** an admin changes the role of "anna" to `ADMIN`
- **THEN** "anna" has access to user management on their next request

#### Scenario: Add the address of an existing user
- **WHEN** an admin enters „anna@example.org“ for "anna", who has no address yet
- **THEN** "anna" has this address, and the notice about the missing address disappears for them
