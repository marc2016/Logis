# Tasks

## 1. Repo foundation

- [x] 1.1 Create `.gitignore` (Java, Node, `.env`, `backups/`), `.editorconfig` and `.env.example` with all settings from design.md D5, D6, D9, D12 and D13. Verify: `git status` does not show `.env` and `backups/`, and `.env.example` contains no real secrets.
- [x] 1.2 Create `compose.dev.yml` with Postgres 17 on `localhost:5432` and a named volume. Verify: `docker compose -f compose.dev.yml up -d` starts, and `pg_isready` reports "accepting connections".

## 2. Backend scaffold

- [x] 2.1 Create the Spring Boot 3 project in `backend/`: Maven Wrapper, Java 21, `groupId app.logis`, dependencies Web, Security, Data JPA, Validation, Actuator, Flyway, PostgreSQL, Spring Modulith, `dev.samstevens.totp`, Testcontainers. Verify: `./mvnw verify` builds successfully.
- [x] 2.2 Create the package structure according to D2 (`core.auth`, `core.users`, `core.modules`, `core.web`, `shared`, `modules.vacationrental`) and write a Modulith verify test. Verify: The test is green and fails as soon as an access from `core` to `modules` is added for testing.
- [x] 2.3 Create `application.yml` (datasource from the environment, `ddl-auto=validate`, Flyway locations according to D4, management port 8081 with `health` only, `server.error.include-*=never`, `forward-headers-strategy=native`, session timeout 30 min) and `application-dev.yml` according to D14. Verify: The backend starts with the `dev` profile against `compose.dev.yml`.
- [x] 2.4 Create Flyway migrations for the schemas `core` and `vacationrental` as well as `core.users` and `core.recovery_codes` according to D5. Verify: After startup, schemas and tables exist with a unique index on `lower(username)`.
- [x] 2.5 Implement global error handling (`@RestControllerAdvice` with `ProblemDetail` without details). Verify: An integration test confirms that a deliberately thrown error returns 500 without stack trace, exception names or version information.

## 3. Module system (backend)

- [x] 3.1 Implement `ModuleDescriptor` and `GET /api/modules` in `core.modules`. Verify: An integration test returns the registered descriptors.
- [x] 3.2 Create `VacationRentalModuleConfiguration` with `@ConditionalOnProperty` (D3), descriptor (`vacationrental`, „Ferienvermietung“) and `GET /api/vacationrental/status`. Verify: Integration tests cover the scenarios "active: list contains the module and status returns 200" and "disabled: module missing from the list and status returns 404".
- [x] 3.3 Integration test "data is kept": write a record into the schema `vacationrental` (test table from a test migration), start with the module disabled, then start with the module enabled. Verify: The record is present and unchanged.

## 4. Users and basic security (backend)

- [x] 4.1 Create the `User` entity with repository, `PasswordEncoder` (delegating, bcrypt) and central password validation (at least 12 characters). Verify: Unit tests show that the hash does not equal the plain text, that `matches` succeeds, and that 11 characters are rejected.
- [x] 4.2 Implement AES-GCM encryption in `shared` with the key from `LOGIS_ENCRYPTION_KEY`. If the key is missing or invalid, startup fails. Verify: A unit test confirms the round trip and that tampered ciphertext raises an error. An integration test confirms that startup without a key aborts with a message.
- [x] 4.3 Implement the initial admin runner according to D5. Verify: Integration tests cover four cases: an empty DB with valid configuration creates the admin with role `ADMIN` and without TOTP, missing configuration makes startup fail, a password with 8 characters makes startup fail, and an existing user stays unchanged.
- [x] 4.4 Implement the basic security configuration: 401 entry point, `CookieCsrfTokenRepository`, public paths (`/api/auth/**`, `/api/health`), domain paths require `ROLE_USER`/`ROLE_ADMIN`, `/api/admin/**` requires `ROLE_ADMIN`, `SessionRegistry`. Verify: Integration tests confirm that `GET /api/modules` without a session returns 401 and a POST without a CSRF token returns 403.

## 5. Multi-step sign-in (backend)

- [x] 5.1 Implement the sign-in state machine according to D6: `POST /api/auth/login`, `GET /api/auth/me` with `stage`, stage authorities, new session ID on every transition. Verify: Integration tests cover that after the password step `stage=TOTP_REQUIRED` or `TOTP_ENROLLMENT_REQUIRED` applies and `GET /api/modules` returns 401. Wrong password, unknown and disabled user lead to the same error message.
- [x] 5.2 Implement TOTP setup: `POST /api/auth/totp/setup` (secret and otpauth URI, encrypted in the session or stored provisionally) and `POST /api/auth/totp/confirm` (check code, store secret, generate 10 recovery codes and return them once). Verify: Integration tests confirm that a valid code completes setup and returns 10 codes, that a wrong code stores nothing, and that `totp_secret_enc` in the DB does not equal the plain-text secret.
- [x] 5.3 Implement TOTP verification: `POST /api/auth/totp` with a TOTP code (±1 step, reuse protection via `totp_last_step`) or a recovery code (single use). Verify: Integration tests with a fixed `Clock` show that a valid code signs in, a reused code is rejected, and a recovery code works once and is rejected the second time.
- [x] 5.4 Implement the stage `PASSWORD_CHANGE_REQUIRED` and `POST /api/auth/password`. Verify: An integration test confirms that a user with `password_change_required` is only `AUTHENTICATED` after the TOTP step once the password has been changed.
- [x] 5.5 Implement lockout according to D7: counter across password and code step, growing lockout up to 15 min, dummy hash for unknown users, log line `auth.failed`. Verify: Integration tests with a fixed `Clock` show that after 5 failures even correct data is rejected with „Zu viele Fehlversuche“, that sign-in continues once the lockout has expired, that a success resets the counter, and that the log contains neither password nor code.
- [x] 5.6 Implement session duration: inactivity 30 min and a filter for the absolute limit of 12 h. Verify: Integration tests with manipulated time or a fixed `Clock` confirm 401 after inactivity and 401 after 12 h despite activity.
- [x] 5.7 Implement `POST /api/auth/logout`. Verify: An integration test confirms that the old cookie gets 401 afterwards.

## 6. User management and my account (backend)

- [x] 6.1 Implement `GET/POST /api/admin/users` and `PATCH /api/admin/users/{id}` according to D8. Verify: Integration tests confirm: A `USER` gets 403. Creating results in an active user with `password_change_required` and pending TOTP. "Anna" is rejected if "anna" exists. The response contains no hashes or secrets.
- [x] 6.2 Implement disabling and re-enabling with immediate termination of sessions via `SessionRegistry`. Verify: An integration test confirms that the user's running session gets 401 after disabling, that the user cannot sign in, and that they can sign in again after re-enabling.
- [x] 6.3 Implement `reset-password` and `reset-totp`. Verify: Integration tests confirm that the sessions end, that a password change is forced after a password reset, and that re-setup is forced after a TOTP reset and old recovery codes are invalid.
- [x] 6.4 Implement the admin invariants: do not downgrade or disable the last active admin, do not disable oneself, with `FOR UPDATE`. Verify: Integration tests cover both rejections including the message.
- [x] 6.5 Implement `POST /api/account/password` (end other sessions) and `POST /api/account/recovery-codes`. Verify: Integration tests confirm that a wrong current password is rejected, that the second session gets 401 after a change, and that old codes are invalid after regenerating.

## 7. Health (backend)

- [x] 7.1 Implement `GET /api/health` with a DB check according to D10. Verify: An integration test returns 200 with `{"status":"UP"}` and no further fields while the DB is running, and 503 with the test container stopped.

## 8. Frontend scaffold

- [x] 8.1 Create the Vite, React and TypeScript project in `frontend/` with Tailwind v4, shadcn/ui (`new-york`, lucide), React Router, TanStack Query, `qrcode.react`, Vitest and Testing Library. Set the Vite proxy `/api` to `localhost:8080`. Verify: `npm run build` and `npm test` run successfully.
- [x] 8.2 Add the shadcn components `sidebar`, `button`, `input`, `input-otp`, `label`, `card`, `dialog`, `table`, `badge`, `select`, `sonner`, `separator`, `tooltip`, `sheet`, `dropdown-menu` and `alert`. Verify: The files are under `src/components/ui/`, and the build is green.
- [x] 8.3 Implement `lib/api.ts`: `credentials: "include"`, `X-XSRF-TOKEN` from the cookie, redirect to `/login?next=…` on 401, errors in `ProblemDetail` format as a German message. Verify: A unit test confirms that the header is set and that 401 redirects.

## 9. App shell and module registry (frontend)

- [x] 9.1 Create the type `LogisModule` and `app/registry.ts` according to D11. A hook `useActiveModules` filters the registry based on `GET /api/modules`. Verify: A unit test shows that only modules from the API response are returned.
- [x] 9.2 Build `AppShell` with `AppSidebar`: core group, one group per active module, „Einstellungen > Benutzer“ only for `ADMIN`, user menu with „Mein Konto“ and „Abmelden“, `collapsible="icon"`, highlighting of the active entry. Verify: Component tests confirm that an active module appears as a group, a disabled one is missing, and a `USER` sees no entry „Benutzer“.
- [x] 9.3 Set up the router: core routes, module routes from active modules under `/<basePath>`, protected admin routes, `NotFound` inside the shell. Verify: Component tests confirm that the URL of a disabled module shows `NotFound` and that `/einstellungen/benutzer` also shows `NotFound` for a `USER`.
- [x] 9.4 Create placeholder pages: „Übersicht“ (`/`) and the module `vacationrental` with `basePath` "ferienvermietung" and the entry „Belegung“. Verify: Open manually in dev mode. A click changes the page without reloading, the URL changes, and the entry is highlighted.
- [x] 9.5 Fix the light theme: shadcn color tokens in `index.css` for the light theme only, no `dark` variants, `color-scheme: light`. Verify: Manually with dark mode in macOS or emulated `prefers-color-scheme: dark` in DevTools. The interface stays light, including form fields and scrollbars.
- [x] 9.6 Check responsive behavior: At smartphone width the sidebar opens as an overlay, and the collapsed state persists after a reload. Verify: Manually in DevTools at 390 px width and with a reload.

## 10. Sign-in, account and user management (frontend)

- [x] 10.1 Implement `AuthGate` and `LoginFlow`: password step, then depending on `stage` TOTP input (`input-otp`, link „Wiederherstellungscode verwenden“) or setup (QR code rendered locally, secret as text, confirmation code), display of the recovery codes with „Ich habe die Codes gesichert“, and a password change if required. `next` is kept across all steps. Verify: Component tests with a mocked API cover the flows: deep link, password, TOTP, target page. For a first sign-in: password, setup, codes, overview. The error messages „Benutzername oder Passwort ist falsch“, „Code ist ungültig“ and „Zu viele Fehlversuche…“ appear.
- [x] 10.2 Implement the page „Mein Konto“ (`/konto`): change password and regenerate recovery codes, with a confirmation dialog and display of the new codes. Verify: Component tests cover success and the message for a wrong current password.
- [x] 10.3 Implement user management (`/einstellungen/benutzer`): a table with status badges, the dialogs „Benutzer anlegen“ and „Bearbeiten“ and the actions disable/re-enable, „Passwort zurücksetzen“ and „2FA zurücksetzen“ with confirmation. Error messages from the API are shown. Verify: Component tests cover creating, the message for a duplicate name and the message „Es muss mindestens ein aktiver Admin bestehen bleiben“.
- [x] 10.4 Implement sign-out: `POST /api/auth/logout`, clear the query cache, redirect to sign-in. Verify: Manually in dev mode. After signing out, a reload shows the sign-in.

## 11. Containers and operations

- [x] 11.1 Create `backend/Dockerfile` as a multi-stage build (temurin 21 jdk to jre, non-root user, without `platform`). Verify: `docker build` succeeds on the Mac (arm64).
- [x] 11.2 Create `frontend/Dockerfile` and `frontend/nginx.conf` according to D9 and D12: SPA fallback, `/api/` to `backend:8080` with `X-Forwarded-*`, `set_real_ip_from` from the environment (via an `envsubst` template), `limit_req` on `/api/auth/`, security headers `always`, `server_tokens off`, no `/actuator`. Verify: `docker build` succeeds, and `nginx -t` in the container is successful.
- [x] 11.3 Create `compose.yml` according to D12: services `frontend`, `backend`, `db` and `backup`, internal network, only `frontend` with port `${LOGIS_BIND:-127.0.0.1}:${LOGIS_PORT:-8080}`, health checks and `depends_on: service_healthy`, volume `pgdata`, all settings from `.env`. Verify: `docker compose up -d --build` starts all services with status `healthy`.
- [x] 11.4 Create and wire up `ops/backup/backup.sh` according to D13. Verify: With `BACKUP_TIME` set one minute ahead for testing, a file `backups/logis_<timestamp>.dump` is created, and a test file made older than the retention period with `touch -d` is deleted on the next run.

## 12. Documentation and acceptance

- [x] 12.1 Write `README.md`: overview, prerequisites, setup, connecting the reverse proxy (bind address, `LOGIS_TRUSTED_PROXIES`, required headers, examples for Traefik/Caddy/NPM), generating the key and **backing it up separately**, local development, switching modules, backup and restore, SQL emergency procedure for the last admin, note about an external backup copy. Verify: Every command in the README has been run at least once.
- [x] 12.2 Smoke test of the stack locally: interface reachable only on the bind address, deep link after reload without an nginx 404, all security headers present (`curl -I`), no server version in the header, `/api/health` returns 200, `/actuator/health` returns no operational data, `nc -z localhost 5432` fails, more than 15 rapid sign-in requests result in 429, complete first sign-in with 2FA setup, and `LOGIS_MODULES_VACATIONRENTAL_ENABLED=false` makes the group disappear after a restart. Verify: All points are checked off.
- [x] 12.3 Restore test: restore a backup into an empty database, start the stack with the same `LOGIS_ENCRYPTION_KEY`. Verify: Sign-in with the existing admin and existing 2FA works.
