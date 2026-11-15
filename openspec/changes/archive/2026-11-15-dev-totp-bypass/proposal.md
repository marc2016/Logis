# Proposal

## Why

Signing in locally takes a code from the authenticator app every time. The replay protection also rejects reusing a code within its 30-second window. This slows down everyday development and manual testing against the local stack. The two-factor flow itself is covered by the end-to-end tests, which run with the production profile, so local development does not need to go through it.

## What Changes

- **New setting.** `logis.auth.dev-totp-bypass` (default `false`). When it is on, a correct username and password complete the sign-in:
  - There is no TOTP step and no TOTP setup.
  - A required change of the start password still follows.
  - Existing TOTP secrets and recovery codes stay untouched.
- **Development profile.** The setting is set to `true` only in `application-dev.yml`, which `./start.sh` uses. It is not offered in `.env.example` or `compose.yml`.
- **Startup guard.** If the setting is on and the profile `dev` is not active, the backend refuses to start with a clear error. When it is on in `dev`, a warning is logged at startup.
- **Unchanged.** Production, the test profile and the end-to-end tests keep the full two-factor flow.

## Capabilities

### New Capabilities

### Modified Capabilities
- `authentication`: a new requirement for signing in without the second factor in local development, limited to the development profile.

## Impact

- **Backend (`core.auth`):**
  - `AuthProperties` gets the flag.
  - `AuthFlowService.checkPassword` completes the sign-in when the flag is on.
  - A startup guard checks the active profiles.
- **Configuration:** `application-dev.yml`.
- **Frontend:** none. The sign-in page already follows whatever stage the server returns, including `AUTHENTICATED` right after the password.
- **Tests:**
  - A unit test for the guard.
  - An integration test for sign-in with the flag on.
  - The existing `LoginFlowTest` keeps covering the normal flow.
- **Database:** none.
