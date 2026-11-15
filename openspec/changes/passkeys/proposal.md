# Proposal

## Why

Every sign-in takes a password and then a code from the authenticator app. A passkey does both in one step: the device holds a private key and unlocks it with Face ID, Touch ID or a PIN. That is faster than typing a code, and it is resistant to phishing, because a passkey only works on the Logis domain. Password and TOTP stay, as a fallback when a device with a passkey is not at hand.

## What Changes

- **Sign-in with a passkey.** The sign-in page offers „Mit Passkey anmelden“.
  - A passkey with user verification signs the user in completely, without password or code.
  - A required change of the start password still follows.
  - Disabled users, locked users and the IP rate limit apply as for the other sign-in steps.
- **Manage passkeys under „Mein Konto“.**
  - A fully signed-in user can add a passkey after confirming their current password. They give it a name, for example „MacBook“.
  - The list shows each passkey with its name, creation date and last use.
  - Passkeys can be renamed and deleted.
- **TOTP stays mandatory.** Two-factor setup remains required for every user. A passkey can only be added after it, so recovery codes and the authenticator app always remain a way back in.
- **Admin reset.** Resetting a user's two-factor authentication also deletes all their passkeys.
- **Public address.** A new setting `LOGIS_PUBLIC_URL` (for example `https://logis.example.org`) defines the domain that passkeys are bound to. Without it, passkeys are not offered and everything works as before.
  - Development uses `http://localhost:5173`.
  - Changing the domain later makes existing passkeys unusable. The README says so.

## Capabilities

### New Capabilities

### Modified Capabilities
- `authentication`:
  - New requirements for signing in with a passkey and for managing passkeys.
  - „Two-step sign-in“ names the passkey as the alternative to password and code.
- `user-management`: resetting two-factor authentication also deletes the user's passkeys.
- `deployment`: a new requirement for the public address that passkeys are bound to.

## Impact

- **Backend (`core.auth`):**
  - WebAuthn ceremonies with the Yubico `webauthn-server-core` library.
  - A credential repository on a new table.
  - Endpoints for sign-in (options, verification) and for managing passkeys under `/api/account/passkeys`.
  - Challenges are kept in the session.
  - The lockout and the failure log cover passkey attempts.
  - The admin reset of two-factor authentication deletes the user's passkeys.
- **Database:** a new migration in the `core` schema:
  - a new table `core.passkeys`;
  - a nullable column `core.users.webauthn_user_handle`, filled on the first registration.
  - Existing data stays unchanged.
- **Frontend:**
  - `@simplewebauthn/browser` for the browser API.
  - A button on the sign-in page, shown only when passkeys are configured.
  - A passkey card on the account page.
- **Deployment:**
  - `LOGIS_PUBLIC_URL` in `.env.example`, `compose.yml` and the README.
  - nginx applies the sign-in rate limit to the passkey sign-in endpoints as well.
- **Tests:**
  - Backend tests with a software authenticator.
  - Frontend tests with a mocked browser API.
  - An e2e test with Chromium's virtual authenticator.
