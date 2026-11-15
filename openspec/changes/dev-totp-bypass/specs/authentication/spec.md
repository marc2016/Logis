## ADDED Requirements

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
