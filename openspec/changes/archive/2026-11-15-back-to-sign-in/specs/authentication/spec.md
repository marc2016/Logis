## ADDED Requirements

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
