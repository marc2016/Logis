# Design

## Context

- **Sign-in flow.** `AuthController` and `AuthFlowService` run a staged sign-in: `ANONYMOUS`, then TOTP setup or TOTP, then an optional `PASSWORD_CHANGE_REQUIRED`, then `AUTHENTICATED`.
  - The stage lives in the HTTP session (`AuthSession`). `AuthSession.start` invalidates any old session, which protects against session fixation.
  - Failures go through `LoginAttemptService`, which counts them per user and logs unknown usernames.
  - `completeSecondFactor` resets the counter and decides between password change and `AUTHENTICATED`.
- **Account.** `MyAccountController` (`/api/account`) changes the password after checking the current one. It answers a wrong password with „Das aktuelle Passwort ist falsch.“
- **Admin reset.** `UserAdminService.resetTotp` already calls the auth package (`RecoveryCodeService`) to delete recovery codes.
- **nginx.** It rate-limits `^/api/auth/(login|totp|password)` per client IP. The CSP has `connect-src 'self'`, and there is no Permissions-Policy, so WebAuthn works in the top-level page.
- **Frontend.** The sign-in page follows the stage returned by the server. The account page has cards for the password and the recovery codes.
- **Versions.** Spring Boot 3.5. No WebAuthn library is in use yet.

## Goals / Non-Goals

**Goals:**
- Passkey sign-in as a full alternative to password plus code, with user verification required.
- Passkeys as an addition: TOTP and recovery codes stay mandatory and unchanged.
- No effect on installations without `LOGIS_PUBLIC_URL`.

**Non-Goals:**
- Passkey autofill in the username field (conditional mediation). This can follow later.
- Passkeys instead of TOTP after the password step.
- Attestation and restricting authenticator models.
- Passkey management by admins beyond deleting all of them on reset.

## Decisions

### D1. Library: Yubico `webauthn-server-core`
- The server side uses `com.yubico:webauthn-server-core`, the latest stable 2.x when the change is applied. It provides `RelyingParty`, `startRegistration`/`finishRegistration` and `startAssertion`/`finishAssertion`, and JSON options that match the browser API.
- *Alternative:* Spring Security's WebAuthn support. Rejected because it is built around Spring Security's own login filter and user details, while Logis runs its own staged session flow.
- *Alternative:* webauthn4j directly. Rejected because it is lower level and needs more code for the same ceremonies.

### D2. Relying party from `LOGIS_PUBLIC_URL`
- The setting is `logis.auth.public-url`, set from `LOGIS_PUBLIC_URL`. When it is set, its host is the RP ID and its origin is the only allowed origin. The RP name is „Logis“.
- When it is empty, the `RelyingParty` bean is not created, `/api/auth/passkey/*` and `/api/account/passkeys/*` answer 404, and `GET /api/auth/me` reports `passkeys: false`.
- `application-dev.yml` sets `http://localhost:5173`. Browsers treat localhost as a secure context.
- *Alternative:* derive the origin from request headers. Rejected because the RP ID must not depend on headers a client can influence.

### D3. Storage
Migration `V20261115.1200__core_passkeys.sql`:

```
core.users
  + webauthn_user_handle  BYTEA UNIQUE   -- random 32 bytes, set on first registration

core.passkeys
  id               BIGINT identity PK
  user_id          BIGINT NOT NULL -> core.users(id) ON DELETE CASCADE
  credential_id    BYTEA NOT NULL UNIQUE
  public_key_cose  BYTEA NOT NULL
  sign_count       BIGINT NOT NULL
  transports       VARCHAR(128)
  name             VARCHAR(64) NOT NULL
  created_at       TIMESTAMPTZ NOT NULL
  last_used_at     TIMESTAMPTZ
```

- A random user handle is used instead of the user id, so a passkey reveals no internal id and survives a username change.
- The column is nullable and filled lazily, so no data migration is needed.
- `PasskeyCredentialRepository` implements Yubico's `CredentialRepository` on these tables.

### D4. Sign-in endpoints
- `POST /api/auth/passkey/options` starts an assertion with no username, which lets the browser offer discoverable passkeys. It uses `UserVerificationRequirement.REQUIRED`. It stores the request JSON in a fresh, anonymous session and returns the options.
- `POST /api/auth/passkey` finishes the assertion with the stored request. On success:
  - it updates the signature counter and `last_used_at`;
  - it calls `AuthSession.start(...)` with the stage from `completeSecondFactor`, which gives a new session id and either the password change or full access.
- Failure handling:
  - An unknown credential, a failed signature or a disabled user leads to 401 „Anmeldung mit Passkey fehlgeschlagen“.
  - For a known user the failure goes through `LoginAttemptService.recordFailure` with the stage `PASSKEY`, and a locked user gets the usual lockout message.
  - An unknown credential is only logged.
- A signature counter that goes backwards is rejected. Synced passkeys report 0, which is accepted.
- nginx: the rate-limited location becomes `^/api/auth/(login|totp|password|passkey)`.

### D5. Management endpoints
These are under `/api/account/passkeys` and require `AUTHENTICATED`. The list, rename and delete calls are scoped to the session user. Ids of other users answer 404.
- `GET` lists the passkeys with id, name, created and last used.
- `POST /registration/options` takes `{password}`. A wrong password gives 400 with the existing message. It sets the user handle if missing and starts a registration with:
  - `ResidentKeyRequirement.REQUIRED`, so the passkey is discoverable;
  - `UserVerificationRequirement.REQUIRED`;
  - the user's existing credentials excluded.

  The request is stored in the session.
- `POST /registration` takes `{name, credential}` and finishes the registration with the stored request. The name is trimmed, must not be empty, and has at most 64 characters.
- `PUT /{id}` renames and `DELETE /{id}` deletes.
- The password check on adding means a stolen session alone cannot plant a lasting credential.

### D6. Admin reset
`UserAdminService.resetTotp` also calls `PasskeyService.deleteAll(userId)`, in the same way it already deletes recovery codes.

### D7. Frontend
- `@simplewebauthn/browser` wraps `navigator.credentials` and the base64url conversion of the server's JSON.
- **Sign-in page:** when `me.passkeys` is true, it shows the button „Mit Passkey anmelden“ under the password form. If the user cancels on the device (`NotAllowedError`), nothing is shown. A server rejection shows the error.
- **Account page:** the card „Passkeys“ has the list (name, „Erstellt am“, „Zuletzt verwendet“), rename, delete with confirmation, and „Passkey hinzufügen“. Adding opens a dialog with the current password and a name, then the browser prompt. The card is hidden when passkeys are not configured, and it explains that passkeys stay bound to this address.

## Risks / Trade-offs

- [The domain changes later] → Existing passkeys stop working, and users sign in with password and code and add new ones. The README states this next to `LOGIS_PUBLIC_URL`.
- [The proxy serves Logis under several hostnames] → Only the configured origin works for passkeys. Other hostnames keep password and code.
- [Synced passkeys keep the counter at 0] → A counter of 0 is accepted, and only a decreasing non-zero counter is rejected.
- [A new library in the security path] → It is a well-known library, pinned to an exact version and covered by tests with a software authenticator.

## Migration Plan

1. The migration adds the table and the nullable column. Nothing changes for existing users.
2. Set `LOGIS_PUBLIC_URL` in `.env` to the address users open, then restart. Users add passkeys under „Mein Konto“.
3. Rollback: the old image ignores the new table and column, so no restore is needed.
