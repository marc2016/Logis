# Proposal

## Why

Users are signed out after 30 minutes without a request, after 12 hours at the latest, and on every backend restart, because sessions live in memory. Each time they have to enter password and TOTP code again, and unsaved input is lost. A device the user trusts should stay signed in.

## What Changes

- **„Angemeldet bleiben“ at sign-in.** The sign-in page gets a checkbox „Angemeldet bleiben“, off by default. It applies to sign-in with password and code and to sign-in with a passkey.
  - After a complete sign-in, the device stays signed in without asking for password, code or passkey.
  - The device stays signed in for 30 days after its last use, and for at most 90 days after the sign-in. After that the user signs in normally again.
  - When the session expires (inactivity, 12 hours, restart), the next request silently starts a new session for the remembered device.
- **Theft protection.**
  - The device credential changes on every use. If an old one is used again, all credentials from that sign-in are revoked and the device must sign in again.
  - A credential presented by a different browser or operating system than the one it was issued to is revoked.
  - Changing the own password, an admin resetting password or two-factor authentication, and disabling the user end all remembered devices. When the user changes their own password, the current device stays signed in.
- **Fresh sign-in for administration.** A session restored from a remembered device has no access to the admin area. The interface shows „Für den Admin-Bereich bitte erneut anmelden“ with a button to sign in again. Domain data stays accessible.
- **Signed-in devices under „Mein Konto“.**
  - A card „Angemeldete Geräte“ lists the remembered devices of the user: browser and operating system, sign-in date, last use. The current device is marked „Dieses Gerät“.
  - Each device can be signed out („Abmelden“).
  - „Alle anderen Geräte abmelden“ ends all other remembered devices and all other sessions.
- **Sign out.** „Abmelden“ also forgets the current device.
- **Out of scope:** warning mails for new devices or locations, location (GeoIP) checks and outgoing mail. They follow as separate changes.

## Capabilities

### New Capabilities

### Modified Capabilities
- `authentication`:
  - New requirements: stay signed in, manage signed-in devices, fresh sign-in for the admin area.
  - „Session duration“ names the remembered device as the way a new session starts without signing in.
  - „Sign out“ also forgets the device.
  - „Change own password“ also ends remembered devices on other devices.
- `user-management`: disabling a user and resetting password or two-factor authentication also end the user's remembered devices.

## Impact

- **Backend (`core.auth`):**
  - A remembered-device store on a new table, with token issue, rotation, reuse detection and revocation.
  - `SessionAuthenticationFilter` restores a session from the device cookie when there is no valid session.
  - The sign-in steps take the „Angemeldet bleiben“ choice and issue the cookie when the sign-in completes.
  - `/api/admin/**` requires a session from a real sign-in.
  - Endpoints under `/api/account/devices` to list devices, sign out one device and sign out all other devices.
  - Revocation follows the existing `sessionVersion`, so the admin actions in `core.users` need no new calls.
  - Scheduled clean-up of expired device entries.
- **Database:** a new migration in the `core` schema with a new table `core.remembered_devices`. Existing data stays unchanged.
- **Frontend:**
  - Checkbox on the sign-in page.
  - Card „Angemeldete Geräte“ on the account page.
  - Notice with a sign-in button when the admin area asks for a fresh sign-in.
- **Configuration:** sliding and absolute duration as settings under `logis.auth.remember-me`, defaults 30 and 90 days.
- **Tests:**
  - Backend tests for restore, rotation, reuse, browser change, durations, revocation and the admin restriction.
  - Frontend tests for the checkbox, the device card and the admin notice.
  - An e2e test: sign in with „Angemeldet bleiben“, lose the session, stay signed in, sign out a device.
