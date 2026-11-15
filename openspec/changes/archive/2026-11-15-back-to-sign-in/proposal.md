# Proposal

## Why

After the password step, the sign-in waits for a TOTP code, for the two-factor setup, or for a new password. The page offers no way back. Someone who typed the wrong username, does not have their phone at hand, or wants to use another account is stuck. Reloading does not help either: the server keeps the unfinished sign-in in the session and shows the same step again. Locally this happens even across backend restarts, because the development tools keep sessions. The only way out today is deleting the session cookie by hand.

## What Changes

- **Link on every intermediate step.** The code step, the two-factor setup and the required password change get the link „Zurück zur Anmeldung“.
- **What the link does.** It ends the unfinished sign-in on the server through the existing sign-out, and then shows the empty password step.
- **What stays.** A failed attempt that already counted towards the lockout is not undone, and the lockout itself stays as it is.
- **Expired intermediate steps.** These already lead back to the password step. That behavior stays.

## Capabilities

### New Capabilities

### Modified Capabilities
- `authentication`: a new requirement for leaving an unfinished sign-in and returning to the password step.

## Impact

- **Frontend:** `LoginPage.tsx`. `TotpStep`, `EnrollmentStep` and `PasswordChangeStep` get the link, which calls `POST /api/auth/logout` and then shows the password step.
- **Tests:** `LoginPage.test.tsx`, and one case in the backend `LoginFlowTest` that signing out in an intermediate stage leaves the session anonymous.
- **Backend:** no change. `POST /api/auth/logout` is already open to every stage and invalidates the session.
- **Database:** none.
