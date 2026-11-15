# Design

## Context

- `LoginPage` shows a step for each stage. When there is no local step yet, the stage comes from `GET /api/auth/me`, which reads it from the server session.
- `TotpStep` and `EnrollmentStep` already receive `onRestart`. It is called only when the server reports an expired intermediate step, and it only resets the local step to `ANONYMOUS`.
- The server session keeps its stage. On the next load, `/me` returns it again.
- `POST /api/auth/logout` is permitted for every stage (`/api/auth/**`) and invalidates the session.

## Goals / Non-Goals

**Goals:**
- A visible way back from every intermediate step that also clears the server state.

**Non-Goals:**
- Changing the lockout, the session duration or the expired-step handling.
- A way back from the one-time display of the recovery codes. At that point the user is already signed in.

## Decisions

### D1. Sign out, then show the password step
- The link calls `POST /api/auth/logout`, then invalidates the cached session query and sets the step to `ANONYMOUS`.
- If the request fails, for example because the network is gone, the step is reset anyway. The next sign-in replaces the session, because `AuthSession.start` invalidates the old one.
- *Alternative:* only reset the local step, as `onRestart` does today. Rejected because a reload brings the old step back, which is exactly the trap.

### D2. One shared component
- A small `BackToSignIn` button (variant `link`) is used by `TotpStep`, `EnrollmentStep` and `PasswordChangeStep`.
- The existing `onRestart` keeps serving the expired case, and it now runs the same sign-out.

## Risks / Trade-offs

- [The user leaves the password change after a reset by the admin] → They stay signed out. The next sign-in asks for the password change again. No access is granted in between.
