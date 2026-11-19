## MODIFIED Requirements

### Requirement: Disable and re-enable users
An admin SHALL be able to disable and re-enable users. Disabling MUST end every existing session of the user immediately and MUST forget all their remembered devices. Users are not deleted, so that later domain data stays assigned to them.

#### Scenario: Disable
- **WHEN** an admin disables "anna" while they are signed in
- **THEN** their next request is answered with HTTP 401, and they can no longer sign in

#### Scenario: Disable a user with a remembered device
- **WHEN** an admin disables "anna", whose laptop is remembered, and enables them again later
- **THEN** the laptop must sign in again with password and code or a passkey

#### Scenario: Re-enable
- **WHEN** an admin enables "anna" again
- **THEN** they can sign in again with their previous credentials and their two-factor authentication

### Requirement: Reset password and two-factor authentication
An admin SHALL be able to reset a user's password to a new initial password. They SHALL also be able to reset the user's two-factor authentication. In both cases every existing session of the user MUST end, and all their remembered devices MUST be forgotten. After two-factor authentication has been reset, the user MUST set it up again at the next sign-in, their old recovery codes MUST be invalid, and all their passkeys MUST be deleted.

#### Scenario: Reset password
- **WHEN** an admin resets the password of "anna"
- **THEN** they can only sign in with the new initial password and must then set their own password

#### Scenario: Reset two-factor authentication
- **WHEN** an admin resets the two-factor authentication of "anna" because they lost their phone
- **THEN** "anna" must set up two-factor authentication again after entering their password

#### Scenario: Reset removes passkeys
- **WHEN** an admin resets the two-factor authentication of "anna", who had a passkey on her lost phone
- **THEN** that passkey can no longer be used to sign in, and "anna" has no passkeys under „Mein Konto“

#### Scenario: Reset forgets remembered devices
- **WHEN** an admin resets the two-factor authentication of "anna", whose lost phone was remembered
- **THEN** the phone can no longer start a session without signing in
