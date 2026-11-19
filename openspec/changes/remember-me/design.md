# Design

## Context

- **Sessions.** Sessions are Tomcat in-memory sessions (no Spring Session), cookie `LOGIS_SESSION`. A restart ends all of them.
- **Sign-in state.** `AuthSession` keeps user id, stage, `sessionVersion`, `LOGIN_AT` and `LAST_SEEN` as session attributes. `SessionAuthenticationFilter` checks idle (30 min) and absolute (12 h) timeouts, the enabled flag and `sessionVersion` on every request, then builds the `SecurityContext` per request. Nothing is stored in a Spring Security context repository.
- **Ways to reach `AUTHENTICATED`.**
  - `AuthController`: `/totp/confirm`, `/totp`, `/password`, and `/login` when the development TOTP bypass is on.
  - `PasskeyController`: `/api/auth/passkey`.
- **Revocation.** `User.invalidateSessions()` increments `sessionVersion`. Callers:
  - user password change (`MyAccountController`), which keeps the current session via `AuthSession.updateVersion`;
  - forced password change;
  - admin disable, password reset and TOTP reset (`UserAdminService`).
- **Interface.** `api.ts` redirects to the sign-in page on 401. „Zurück zur Anmeldung“ calls `/api/auth/logout`. The header's sign-out calls the same endpoint.
- **Proxy.** The real client address and the `https` scheme come from the reverse proxy via forwarded headers, so `request.isSecure()` is reliable.

## Goals / Non-Goals

**Goals:**
- A remembered device survives idle timeout, absolute timeout and backend restarts.
- Revocation reuses `sessionVersion`, so every existing "end all sessions" path also forgets remembered devices without new calls.
- A stolen device cookie is useful for as short a time as possible.

**Non-Goals:**
- Location checks, warning mails and outgoing mail (follow-up changes). The table gets no IP or location columns yet.
- Persisting HTTP sessions (Spring Session JDBC). Remembered devices make it unnecessary for this problem.
- Showing sessions without „Angemeldet bleiben“ in the device list.

## Decisions

### D1. Own remembered-device store instead of Spring's `RememberMeServices`
`PersistentTokenBasedRememberMeServices` relies on a `UserDetailsService` and puts the result into the `SecurityContext`. Logis builds the context per request from its own session attributes, and its sign-in is multi-stage. A small service in `core.auth` (`RememberedDeviceService`) uses the same series/token pattern and plugs into `SessionAuthenticationFilter` and the existing sign-in endpoints.

### D2. Series and token, hashed
- Each remembered device is one row with a random `series` (32 bytes) and the SHA-256 hash of a random `token` (32 bytes).
- Cookie `LOGIS_REMEMBER` = `base64url(series) + ":" + base64url(token)`, `HttpOnly`, `Secure` when the request is secure, `SameSite=Lax`, `Path=/api`, `Max-Age` up to the absolute expiry.
- A plain SHA-256 is enough because the token has 256 random bits. A slow password hash is not needed. Hashes are compared in constant time.
- Unknown series: clear the cookie and stay anonymous. No 401 is produced by the filter itself. The normal authorization answers protected endpoints with 401.

### D3. Rotation with a grace window
- Each time a device starts a session, the token is replaced. The row keeps the previous hash and `rotated_at`.
- If the presented token matches the previous hash within 60 seconds of `rotated_at`, the request gets a session without a new cookie. The browser already has the newer cookie from the response that rotated. This covers the parallel requests a page sends after its session expired.
- If the presented token matches neither the current hash nor a previous one within the window, the series counts as stolen. The row is deleted, the cookie cleared, and the event logged with user, series id and client IP (`LoginAttemptService.logFailure`, stage `REMEMBER_REUSE`). It does not count towards the lockout: only the holder of a cookie can trigger it, and counting would let a thief lock the user out.
- Rotation runs in one transaction with `SELECT … FOR UPDATE` on the row, so two parallel restores cannot both rotate.

### D4. Browser and operating system binding
- At issue time, the `User-Agent` is reduced to a browser family (Firefox, Chrome, Edge, Safari, Opera, Samsung Internet, other) and an OS family (Windows, macOS, iOS, Android, Linux, other). A small parser class in `core.auth` does this.
- A restore with a different family pair deletes the row (as in D3).
- Families, not versions, are compared, so browser updates do not sign users out.
- The same values are shown in the device list. No new dependency is needed, and a library like uap-java would be heavy for this purpose.

### D5. Durations
- `expires_at` = sign-in time + 90 days. It is fixed and never extended.
- The sliding limit is `last_used_at` + 30 days.
- `last_used_at` is set on restore. While a session is active, the filter refreshes it at most once per hour, tracked by a session attribute, to avoid a write per request.
- Settings: `logis.auth.remember-me.sliding` (30d), `absolute` (90d), `reuse-grace` (1m).
- A daily scheduled job deletes rows past either limit and rows whose `session_version` no longer matches the user's.

### D6. Revocation via `sessionVersion`
- Each row stores the user's `sessionVersion` at issue time. A restore only succeeds if it equals the current one and the user is enabled.
- All existing callers of `invalidateSessions()` therefore also end remembered devices. `core.users` needs no change.
- Own password change and „Alle anderen Geräte abmelden“: increment the version, then write the new version to the current session (as today) **and** to the current device's row, so that the current device stays remembered.
- The device list only shows rows with the current version.

### D7. Where the cookie is issued
- `/api/auth/login` and `/api/auth/passkey` accept `rememberMe: boolean`. It is stored as a session attribute (`REMEMBER_REQUESTED`) of the sign-in session that `AuthSession.start` creates.
- Every endpoint that returns `AUTHENTICATED` calls `RememberedDeviceService.onAuthenticated(request, response, user)`:
  - It first deletes the row of any `LOGIS_REMEMBER` cookie the request carries, even if that row belongs to another user.
  - It then issues a new device only if the attribute is set.
- A sign-in left at `PASSWORD_CHANGE_REQUIRED` never issues a cookie. The cookie is issued after the password change completes.

### D8. Restore in `SessionAuthenticationFilter`
- Restore runs when the request has no session in a signed-in stage, a `LOGIS_REMEMBER` cookie is present, and the request is not a `POST` to `/api/auth/**`. Sign-in steps and sign-out must not restore.
- A successful restore calls `AuthSession.start(…, AUTHENTICATED, …)`, which gives a fresh session id. It also sets `DEVICE_ID` and `RESTORED=true` and continues with the normal context building.
- `GET /api/auth/me` restores too, so the interface finds the user without a 401 round trip.
- A sign-in session (`DEVICE_ID` set, `RESTORED` absent) also stores `DEVICE_ID`, so the device list can mark „Dieses Gerät“.

### D9. Fresh sign-in for `/api/admin/**`
- Admin authorization becomes `hasRole("ADMIN")` **and** not `RESTORED`. The filter grants `ROLE_ADMIN` only to non-restored sessions and adds a marker authority `REAUTH_REQUIRED` for restored admin sessions.
- A dedicated handler answers `/api/admin/**` requests from such sessions with HTTP 403 and a ProblemDetail of type `urn:logis:reauthentication-required` and detail „Für den Admin-Bereich bitte erneut anmelden“.
- `api.ts` turns this into an `ApiError` with `reauthenticationRequired`. The admin pages then render a notice with „Erneut anmelden“.
- „Erneut anmelden“ calls the new `POST /api/auth/reauthenticate`. It invalidates the session and starts an anonymous session with `SKIP_RESTORE=true`, so that `/me` does not restore again, and keeps the device cookie. The interface then shows the sign-in page with „Angemeldet bleiben“ pre-checked.
- The completed sign-in replaces the device (D7). The returned session is not restored, so the admin area works.
- Alternative considered: a separate "step-up" stage without leaving the session. It would add a second sign-in flow. Reusing the normal sign-in is simpler.

### D10. Sign out and device endpoints
- `/api/auth/logout`: deletes the row of the presented cookie, clears the cookie and invalidates the session. „Zurück zur Anmeldung“ uses the same endpoint. Using it while a device is remembered (for example during „Erneut anmelden“) forgets the device, and the user signs in normally, which they are doing anyway.
- `GET /api/account/devices`: own devices with current version: `id`, `browser`, `os`, `createdAt`, `lastUsedAt`, `current`.
- `DELETE /api/account/devices/{id}`: own device only. Otherwise 404.
- `POST /api/account/devices/sign-out-others`: see D6. Afterwards deletes all other rows of the user.

### D11. Data model
New migration `V20261119.1200__core_remembered_devices.sql`:

```
core.remembered_devices
  id                  BIGINT identity PK
  user_id             BIGINT NOT NULL -> core.users(id) ON DELETE CASCADE
  series              BYTEA NOT NULL UNIQUE
  token_hash          BYTEA NOT NULL
  previous_token_hash BYTEA
  rotated_at          TIMESTAMPTZ
  session_version     INT NOT NULL
  browser             VARCHAR(32) NOT NULL
  os                  VARCHAR(32) NOT NULL
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
  last_used_at        TIMESTAMPTZ NOT NULL
  expires_at          TIMESTAMPTZ NOT NULL
  index on user_id
```

It only adds a table. Existing rows are unaffected, and no data migration is needed.

## Risks / Trade-offs

- [Stolen cookie works until it is noticed] → Rotation detects reuse as soon as the real device comes back. The browser/OS binding blocks naive replay on another device type. The device list and „Alle anderen Geräte abmelden“ give manual control. Location checks and warning mails follow.
- [Same browser and OS on the thief's device] → The binding does not help here. This is an accepted limit until the location check.
- [Admin account on a remembered device] → No admin actions without a fresh sign-in (D9).
- [Grace window lets a copied previous token in for 60 s] → It only works right after a rotation by the real device. The session it gets has the same rights and is visible to the user.
- [Parallel restores hitting a row lock] → Short transactions. A lost race falls into the grace window.
- [Users on shared computers check the box] → It is off by default, and signing out forgets the device.

## Migration Plan

1. Deploy: Flyway adds the table. Nothing changes for users until they check „Angemeldet bleiben“.
2. Rollback: the previous release ignores the table and the cookie. Users sign in normally. The table can stay or be dropped in a later migration.
