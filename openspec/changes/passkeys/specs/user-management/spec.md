## MODIFIED Requirements

### Requirement: Reset password and two-factor authentication
An admin SHALL be able to reset a user's password to a new initial password. They SHALL also be able to reset the user's two-factor authentication. In both cases every existing session of the user MUST end. After two-factor authentication has been reset, the user MUST set it up again at the next sign-in, their old recovery codes MUST be invalid, and all their passkeys MUST be deleted.

#### Scenario: Reset password
- **WHEN** an admin resets the password of "anna"
- **THEN** they can only sign in with the new initial password and must then set their own password

#### Scenario: Reset two-factor authentication
- **WHEN** an admin resets the two-factor authentication of "anna" because they lost their phone
- **THEN** "anna" must set up two-factor authentication again after entering their password

#### Scenario: Reset removes passkeys
- **WHEN** an admin resets the two-factor authentication of "anna", who had a passkey on her lost phone
- **THEN** that passkey can no longer be used to sign in, and "anna" has no passkeys under „Mein Konto“
