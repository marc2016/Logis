## ADDED Requirements

### Requirement: Public address for passkeys
The public address under which users reach Logis (for example `https://logis.example.org`) SHALL be set through the environment variable `LOGIS_PUBLIC_URL`. Passkeys MUST be bound to the host of this address and MUST only be accepted from this origin. Without the setting, passkeys MUST NOT be offered and the system MUST otherwise work unchanged. The documentation MUST state that changing the domain makes existing passkeys unusable.

#### Scenario: Configured domain
- **WHEN** the operator sets `LOGIS_PUBLIC_URL=https://logis.example.org` and a user adds a passkey there
- **THEN** the passkey works on `https://logis.example.org` and not on any other address

#### Scenario: Not set
- **WHEN** `LOGIS_PUBLIC_URL` is empty
- **THEN** the sign-in page and „Mein Konto“ offer no passkeys, and sign-in with password and code works
