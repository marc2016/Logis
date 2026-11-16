# Tasks

## 1. Data and configuration

- [x] 1.1 Add migration `V20261115.1200__core_passkeys.sql` with `core.passkeys` and `core.users.webauthn_user_handle` (design D3). Verify with a migration test that existing users stay unchanged and that Hibernate validates.
- [x] 1.2 Add `com.yubico:webauthn-server-core` (latest stable 2.x, exact version) and `logis.auth.public-url` from `LOGIS_PUBLIC_URL`. Create the `RelyingParty` only when the setting is set, with RP ID, origin and name „Logis“ (design D1, D2). Set `http://localhost:5173` in `application-dev.yml`. Verify with a test that there is no bean and `me.passkeys` is false without the setting, and that the bean exists with the setting.

## 2. Backend

- [x] 2.1 Add `PasskeyStore` (JDBC) and `PasskeyCredentialRepository` (Yubico `CredentialRepository`). Verify through the sign-in tests in `PasskeysTest`, which look credentials up by id and user handle.
- [x] 2.2 Add the management endpoints under `/api/account/passkeys` (design D5): password check, registration options and finish, list, rename and delete, all scoped to the session user. Verify with an integration test using a software authenticator:
  - adding with a wrong password gives „Das aktuelle Passwort ist falsch.“;
  - a passkey can be added, listed, renamed and deleted;
  - the passkey of another user answers 404;
  - the endpoints are rejected before two-factor setup is complete.
- [x] 2.3 Add the sign-in endpoints `/api/auth/passkey/options` and `/api/auth/passkey` (design D4), with the session start through `completeSecondFactor`, the counter check, the lockout and the failure log. Verify in an integration test:
  - a passkey sign-in gives `AUTHENTICATED` and access to a protected endpoint;
  - a user with a required password change gets `PASSWORD_CHANGE_REQUIRED`;
  - a disabled user, an unknown credential or a wrong signature give „Anmeldung mit Passkey fehlgeschlagen“ and count as failures;
  - a locked user is rejected;
  - a reused challenge is rejected.
- [x] 2.4 Let `UserAdminService.resetTotp` delete the user's passkeys (design D6). Verify in `PasskeysTest` that a passkey stops working after the reset.

## 3. Frontend

- [x] 3.1 Add `@simplewebauthn/browser` (exact version), the API types and `passkeys` in the session type. Verify with `npm run build`.
- [x] 3.2 Add „Mit Passkey anmelden“ to the sign-in page, shown only when passkeys are configured, with a silent cancel and the server error message (design D7). Verify in `LoginPage.test.tsx` with a mocked `startAuthentication`:
  - success navigates to the overview;
  - a cancel shows nothing;
  - a rejection shows „Anmeldung mit Passkey fehlgeschlagen“;
  - the button is hidden without configuration.
- [x] 3.3 Add the card „Passkeys“ to the account page: list, add with password and name, rename, delete with confirmation, hidden without configuration. Verify in `AccountPage.test.tsx` with a mocked `startRegistration`.

## 4. Deployment and documentation

- [x] 4.1 Add `LOGIS_PUBLIC_URL` to `.env.example` and `compose.yml`, extend the nginx rate-limited location to `passkey`, and document in the README the setting and the domain binding. Verify that `docker compose config` resolves the variable and that the nginx config test in the image build passes.

## 5. Integration

- [x] 5.1 Add an e2e test that adds a passkey and signs in with it, using Chromium's virtual authenticator through CDP (`WebAuthn.addVirtualAuthenticator`), with `LOGIS_PUBLIC_URL` set in the e2e environment. Verify that it type-checks (`npx tsc --noEmit` in `e2e`).
- [x] 5.2 Run `cd backend && ./mvnw test` and `cd frontend && npm test && npm run lint && npm run build`, and verify that all pass.
