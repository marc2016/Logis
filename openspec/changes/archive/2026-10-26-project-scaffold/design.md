# Design

## Context

This is a greenfield project: so far the repo only contains `openspec/`. Motivation and guiding decisions are in proposal.md, the requirements in `specs/`.

These constraints shape the design:

- There is one developer and operator, but **multiple users**.
- Logis is **reachable from the internet** through an existing reverse proxy on the mini PC that handles TLS.
- Development happens on a Mac with **arm64**, operation on a mini PC with **amd64** (`x86_64`). Builds run directly on the mini PC for now.
- This is financial data. Two-factor sign-in, brute-force protection, hardening and backups are therefore mandatory.
- Subsequent changes (ledger, real estate, vacation rental) should plug in as modules without rebuilding the frame.

## Goals / Non-Goals

**Goals:**
- Fixed module boundaries whose violation makes a test fail, rather than only being noticed in code review.
- A new module needs exactly one package in the backend and exactly one registry entry in the frontend.
- A sign-in that is defensible on the internet without an authentication layer in front of it.
- The stack starts with a single command on the mini PC.
- Local development offers hot reload for backend and frontend without rebuilding the images.

**Non-Goals:**
- TLS termination and certificates. The existing reverse proxy handles that.
- Access rights to domain data (e.g. per legal entity). See Open Questions; the topic comes with `core-ledger`.
- A forgot-password function via email. Instead an admin resets the password; there is no email sending yet.
- Passkeys/WebAuthn. These could be added later as a second factor or replacement.
- CI, an image registry and release versioning.
- Backing up documents or files. There are none yet; the topic comes with the AI inbox.

## Decisions

### D1: Repo layout

```
logis/
  backend/            Maven, Spring Boot 3.x, Java 21
  frontend/           Vite, React, TypeScript, Tailwind, shadcn/ui
  ops/backup/         backup script for the backup container
  compose.yml         production: frontend, backend, db, backup
  compose.dev.yml     development: db only
  .env.example
  README.md           start, reverse proxy, development, backup/restore
```

### D2: Spring Modulith for module boundaries

```
app.logis
  LogisApplication
  core/                   core
    auth/                 sign-in steps, TOTP, recovery, lockout, sessions
    users/                user, roles, admin API, "my account"
    modules/              ModuleDescriptor, GET /api/modules
    web/                  error handling, health
  shared/                 cross-cutting without domain logic (crypto, later money)
  modules/
    vacationrental/       placeholder: GET /api/vacationrental/status
```

- A test with `ApplicationModules.of(LogisApplication.class).verify()` checks that modules only use the API packages of other modules and that there are no cycles.
- `modules.*` may access `core`, but `core` never accesses `modules.*`.
- *Alternative:* separate Maven modules per domain module. That gives harder boundaries through compile dependencies, but costs a lot of build configuration for a single person. Switching later is possible because the package boundaries are the same.

### D3: Switching modules on and off

- Every module has an `@Configuration` with `@ConditionalOnProperty(prefix = "logis.modules.<id>", name = "enabled", matchIfMissing = true)`. All beans of the module, including the controllers, hang off this configuration. If the module is disabled, no controllers exist and Spring responds with 404.
- Every active module registers a `ModuleDescriptor` bean (`id`, `label`, `order`). `GET /api/modules` returns all registered descriptors.
- In Docker the settings are set through environment variables (`LOGIS_MODULES_VACATIONRENTAL_ENABLED=false`, relaxed binding).
- Switching applies globally. Enabling modules per user is deliberately not planned as long as data access is an open topic.
- *Alternative:* feature flags in the database with an admin interface. That would be switchable at runtime, but would need bean refresh and UI. For a single operator, a restart and `.env` are enough.

### D4: Database and migrations

- There is one Postgres instance (version 17) and **one schema per area**: `core` and one per module (`vacationrental` etc.).
- There is one Flyway instance with the locations `classpath:db/migration/core` and `classpath:db/migration/<module>`. Version numbers are globally unique and assigned by timestamp (`V20261025_1200__core_users.sql`).
- **Migrations of all modules always run, including for disabled modules.** This keeps schema and data intact when a module is disabled.
- JPA runs with `ddl-auto=validate`; Flyway is the only source of the schema.
- *Alternative:* one Flyway instance per module with its own history table. That causes ordering problems when modules set foreign keys to `core`.

### D5: Users, roles and sessions

- Table `core.users`:
  - Master data: `id`, `username` (unique via `lower(username)`), `display_name`, `role` (`ADMIN`/`USER`), `enabled`.
  - Password: `password_hash`, `password_change_required`.
  - Two-factor: `totp_secret_enc` (nullable), `totp_last_step`.
  - Lockout: `failed_attempts`, `locked_until`.
  - Timestamps: `last_login_at`, `created_at`.
- Table `core.recovery_codes` (`user_id`, `code_hash`, `used_at`).
- Passwords are stored via `DelegatingPasswordEncoder`, defaulting to bcrypt. The minimum length of 12 characters is validated centrally.
- **Session:** Spring session cookie (`HttpOnly`, `SameSite=Lax`, `Secure` according to `X-Forwarded-Proto`). Inactivity timeout 30 min via `server.servlet.session.timeout`. A filter checks the absolute limit of 12 h based on a sign-in timestamp in the session.
- Sessions are held in memory. **Sessions are ended through a session version:** `core.users.session_version` is incremented on disabling, on resets and on a password change. A filter (`SessionAuthenticationFilter`) compares it on every request with the version in the session, also checks inactivity (30 min) and absolute duration (12 h), and builds the SecurityContext with the **currently stored** role. Role changes therefore take effect on the next request.
  - *Originally planned:* a `SessionRegistry`. During implementation it turned out that roles stored there would only update after a new sign-in. In addition, the new approach costs only one primary-key query per request.
- Without a full session there is a 401 via an `HttpStatusEntryPoint` and no redirect.
- **CSRF:** `CookieCsrfTokenRepository` with a readable `XSRF-TOKEN` cookie. The frontend sends the header `X-XSRF-TOKEN`.
- **Initial admin:** An `ApplicationRunner` creates it if `core.users` is empty. It uses `LOGIS_ADMIN_USERNAME` and `LOGIS_ADMIN_PASSWORD`, assigns the role `ADMIN`, sets `password_change_required=false` and leaves TOTP empty, so that setup is forced at first sign-in. Missing or too short values make startup fail.
- *Alternative:* JWT in local storage. That is more vulnerable to XSS, and ending a session immediately on disabling is not possible with it.

### D6: Multi-step sign-in as a state machine

Spring Security 6 has no built-in MFA. Sign-in is therefore modeled as state in the session:

```
                POST /api/auth/login (user+pw ok)
  [anonymous] --------------------------------------> [PASSWORD_OK]
                                                         |
                       totp_secret_enc == null           |  totp present
                  +--------------------------------------+------------------+
                  v                                                         v
       [TOTP_ENROLLMENT_REQUIRED]                                    [TOTP_REQUIRED]
         POST /api/auth/totp/setup  -> secret + otpauth URI            POST /api/auth/totp
         POST /api/auth/totp/confirm (code ok) -> 10 recovery codes      (TOTP or recovery code ok)
                  |                                                         |
                  +-------------------------+-------------------------------+
                                            v
                         password_change_required ?
                         yes |                          | no
                             v                          v
               [PASSWORD_CHANGE_REQUIRED]         [AUTHENTICATED]  -> ROLE_ADMIN / ROLE_USER
                 POST /api/auth/password ---------------^
```

- Every intermediate state is its own authority (`AUTH_STAGE_*`) without domain rights. Only `AUTHENTICATED` gets `ROLE_USER` or `ROLE_ADMIN`. Domain endpoints require one of these roles, which automatically satisfies the spec requirement "only after both steps".
- On a state change the session ID is reissued (protection against session fixation).
- `GET /api/auth/me` returns `{ stage, user? }`. The frontend derives from it which step to show.
- **TOTP:** RFC 6238 with SHA-1, 6 digits, 30 s and ±1 time step tolerance. A reused code is rejected via `totp_last_step`, because a code is only valid if its step is greater than the last one used. Library: `dev.samstevens.totp`.
- **TOTP secret encrypted:** AES-256-GCM with the key from `LOGIS_ENCRYPTION_KEY` (Base64, 32 bytes). This makes secrets in backups worthless without the key. If the key is missing, startup fails.
- **Recovery codes:** 10 codes of 10 characters each in Base32, grouped (`xxxxx-xxxxx`). They are stored as bcrypt hashes, and `used_at` makes each code single-use.
- *Alternative:* Spring Security 7 with built-in MFA. It is not available given the Spring Boot 3.x constraint. On a later upgrade it can be checked whether the custom state machine can be replaced.

### D7: Brute-force protection

It consists of two layers:

1. **nginx:** `limit_req` on `/api/auth/` keyed on the real client IP (see D9), about 10 requests per minute with a burst of 5. Exceeding it returns 429.
2. **Backend per user:** `failed_attempts` counts failures in the password and the code step. From 5 failures on, `locked_until = now + min(2^(n-5), 15) min` applies. A success resets the counter. A lockout is checked before the password is checked.

- **Unknown user:** The backend still runs a bcrypt comparison against a dummy hash. This way the response time does not reveal whether a user exists.
- Failures are written as a structured log line `auth.failed user=<name> ip=<ip> stage=<stage>`, without password and without code.

### D8: User management

- Admin endpoints under `/api/admin/users`: `GET` (list), `POST` (create with initial password, setting `password_change_required=true`), `PATCH /{id}` (display name, role, `enabled`), `POST /{id}/reset-password` and `POST /{id}/reset-totp`.
- "My account" under `/api/account`: `POST /password` (current and new password) and `POST /recovery-codes` (regenerate).
- A service checks the invariant "at least one active admin" and the rule "do not disable oneself" in the same transaction. Counting active admins uses `SELECT … FOR UPDATE`, so that two concurrent downgrades cannot let each other through.
- Users are never deleted, only disabled. Domain data will later be assigned to them as creator or editor.

### D9: Reverse proxy, headers and hardening

```
 Internet --TLS--> Reverse proxy (existing, e.g. Traefik/Caddy/NPM)
                        |  HTTP, X-Forwarded-For / -Proto
                        v
            ${LOGIS_BIND:-127.0.0.1}:${LOGIS_PORT:-8080}
                        |
                 frontend (nginx)  -- set_real_ip_from ${LOGIS_TRUSTED_PROXIES}
                        |              real_ip_header X-Forwarded-For
                        v
                 backend :8080     -- server.forward-headers-strategy=native
                                      (Tomcat RemoteIpValve, internalProxies = frontend network)
```

- **Bind address:** default `127.0.0.1`, suitable for a proxy on the same host. If the proxy runs as a container in the same Docker network, `LOGIS_BIND`/`LOGIS_PORT` are set accordingly or the external network is attached, as described in the README.
- **nginx accepts the client IP** only from `LOGIS_TRUSTED_PROXIES` and passes `X-Forwarded-For` and `X-Forwarded-Proto` on to the backend. The backend trusts only nginx in the internal network.
- **nginx sets the security headers** for all responses (`add_header … always`):
  - `Strict-Transport-Security: max-age=31536000`
  - `Content-Security-Policy: default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'`. `unsafe-inline` is only needed for styles because Radix/shadcn set inline styles. Scripts stay strict.
  - `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: same-origin`
  - Plus `server_tokens off`.
- **Error responses:** A global `@RestControllerAdvice` returns a uniform `ProblemDetail` without details. `server.error.include-stacktrace=never` and `include-message=never`.
- The QR code for TOTP is rendered in the frontend from the `otpauth://` URI. The secret therefore never goes to a third-party QR service, and the CSP stays at `'self'`.

### D10: Health

- The Actuator `health` is exposed **internally only** on the management port `8081`. The backend's Docker health check uses it. The security configuration allows only the health endpoint there without sign-in.
- A dedicated `GET /api/health` (without sign-in) checks the database with `SELECT 1` and returns only `{"status":"UP"}` or 503.
- nginx proxies only `/api/`, so `/actuator` is unreachable from outside.

### D11: Frontend architecture

```
frontend/src/
  main.tsx, App.tsx         router, QueryClient, AuthGate
  app/
    layout/AppShell.tsx     SidebarProvider + AppSidebar + <Outlet/>
    registry.ts             list of all frontend modules
    NotFound.tsx
  core/
    auth/                   LoginFlow (password -> TOTP | setup -> recovery codes
                            -> password change if required), useSession, api
    account/                "my account": change password, regenerate recovery codes
    admin/users/            user list, create/edit dialogs, resets
    dashboard/              overview (placeholder)
  modules/
    vacationrental/
      index.ts              export const module: LogisModule
      pages/                BelegungPage (placeholder)
  lib/api.ts                fetch wrapper: credentials, XSRF header, 401 -> sign-in
  components/ui/            shadcn components (generated, in the repo)
```

- **The module contract:**
  ```ts
  interface LogisModule {
    id: string;                 // == backend module ID
    label: string;              // "Ferienvermietung"
    icon: LucideIcon;
    order: number;
    basePath: string;           // "ferienvermietung"
    navItems: { label: string; path: string; icon?: LucideIcon }[];
    routes: RouteObject[];      // relative to /<basePath>
  }
  ```
- All frontend modules are bundled statically. At runtime the shell loads `GET /api/modules` and shows only active modules, both in the sidebar and in the routes.
- **Sidebar:** shadcn `Sidebar` with `collapsible="icon"`. At the top is the core group („Übersicht“), then one group per module. At the bottom are the section „Einstellungen > Benutzer“, visible only to `ADMIN`, and the user menu in the footer („Mein Konto“, „Abmelden“). The sidebar stores its collapsed state in a cookie. On mobile devices it opens as a sheet overlay.
- **AuthGate:** It reads `stage` from `GET /api/auth/me` and shows the matching step of the sign-in flow or the shell. It keeps the `next` parameter across all steps.
- **QR code:** A small library (`qrcode.react`) renders an SVG locally.
- **Routing:** React Router (data router) with German URLs (`/ferienvermietung/belegung`, `/konto`, `/einstellungen/benutzer`).
- **Server state:** TanStack Query. The API wrapper redirects to `/login?next=<path>` on 401.
- **Styling:** Tailwind v4 and shadcn/ui with the preset `radix-nova` (Radix, lucide, font Geist). The current shadcn CLI no longer offers the style `new-york`. **Light theme only.** There is no `dark` class, and the system setting is ignored. The color tokens live in `index.css` as CSS variables (shadcn convention), so that a dark mode could be added later without touching the components.

### D12: Containers

```
          Reverse proxy (existing)
                    |
   ${LOGIS_BIND}:${LOGIS_PORT}
           +--------v--------+
           | frontend (nginx)|   /     -> SPA (try_files ... /index.html)
           |  headers, limit |   /api/ -> http://backend:8080
           +--------+--------+
                    | net: internal
           +--------v--------+        +-----------+
           | backend         |------->| db        |  volume: pgdata
           | :8080 app       |        | postgres17|
           | :8081 mgmt      |        +-----^-----+
           +-----------------+              |
                                      +-----+-----+
                                      | backup    |  bind: ./backups
                                      +-----------+
```

- **Frontend image:** `node:24-alpine` builds, `nginx:1.28-alpine` serves. Compared to the plan, these are the current LTS and stable lines. A script in `/docker-entrypoint.d/` generates the `set_real_ip_from` lines from `LOGIS_TRUSTED_PROXIES` at startup. nginx hides the backend's security headers and sets them itself, so that no header appears twice.
- The Dockerfiles do without BuildKit features such as cache mounts, so they also build with a classic Docker builder.
- **Backend image:** `eclipse-temurin:21-jdk` builds, `eclipse-temurin:21-jre` runs the application as a non-root user.
- All base images are multi-arch and without `platform:` settings; the build runs natively on amd64 and arm64.
- `depends_on` with `condition: service_healthy`. Only `frontend` has `ports:`.
- Health checks: `db` with `pg_isready`, `backend` with `:8081/actuator/health`, `frontend` with `/api/health`.

### D13: Backups

- A `backup` container is based on `postgres:17` with a matching `pg_dump` version. It runs `ops/backup/backup.sh` as a loop: `pg_dump -Fc` to `/backups/logis_<YYYYMMDD_HHMMSS>.dump`, deleting files older than `BACKUP_RETENTION_DAYS` (default 14), daily at `BACKUP_TIME` (default `03:00`).
- Bind mount `./backups` on the host.
- The README explicitly points out that **`LOGIS_ENCRYPTION_KEY` must be backed up separately from the backups**. Without it the TOTP secrets cannot be decrypted after a restore, and all users would have to set up 2FA again.
- Restoring (`pg_restore --clean --if-exists`) is documented in the README and tested by hand.

### D14: Development environment

- `compose.dev.yml` starts only `db` on `localhost:5432`.
- The backend runs with the `dev` profile and devtools. `application-dev.yml` contains a dev admin and a fixed dev encryption key, `Secure` cookies are switched off (HTTP on localhost), and lockout is active with short times.
- The frontend runs with `npm run dev`. The Vite proxy forwards `/api` to `http://localhost:8080`.

### D15: Tests

- **Backend:** JUnit 5. This includes the Modulith verify test and integration tests with Testcontainers (Postgres 17) for the whole sign-in state machine, TOTP replay, recovery codes, lockout, session timeouts (with `Clock` injection), user management including the admin invariant, module switching, health, error responses and the initial admin runner.
- **Frontend:** Vitest and Testing Library for registry filtering, the AuthGate steps, visibility of admin entries and sidebar rendering.
- **Stack:** A smoke checklist in the README checks port and bind address, headers, 429 on many sign-ins, `/api/health`, the closed DB port, `/actuator` and the backup.

## Risks / Trade-offs

- [Custom MFA state machine instead of a framework solution, i.e. security-critical custom code] → It is small and state-based, every transition has an integration test, and an established TOTP library is used. When upgrading to Spring Security 7 it will be checked whether it can be replaced.
- [Sessions are held in memory, a restart signs everyone out] → That is acceptable. Spring Session JDBC can be added if needed; the `SessionRegistry` then has to move along.
- [Without a forgot-password function an admin is needed] → With one operator and few users that is defensible. For the emergency of the only admin, the README describes a CLI route (an SQL statement sets `password_change_required` and a new hash, or clears `totp_secret_enc`).
- [If `LOGIS_ENCRYPTION_KEY` is lost, 2FA becomes invalid for everyone] → The README requires a separate backup. Recovery through the admin reset or the SQL emergency route remains possible.
- [The reverse proxy is misconfigured, e.g. no `X-Forwarded-Proto` or a wrong IP] → `Secure` cookies or the rate limit then do not work correctly. The smoke checklist explicitly checks cookie attributes and the logged client IP.
- [`style-src 'unsafe-inline'` in the CSP] → This is needed for Radix inline styles. The risk is low because `script-src` stays strict.
- [One shared Flyway history for all modules] → Timestamp versions with a module prefix prevent collisions.
- [Spring Boot 3.x instead of the current major version] → This is a deliberate constraint. The upgrade will be its own change.
- [Backups live on the same host] → This does not protect against hardware failure. An off-host copy should follow soon and is noted in the README.

## Migration Plan

As a first installation there is no migration of existing data. Setup works like this: `git clone`, create `.env` from `.env.example` (generate the key with `openssl rand -base64 32`), `docker compose up -d --build`, then set up a route to `LOGIS_BIND:LOGIS_PORT` with TLS in the existing reverse proxy. Afterwards set up 2FA at the first sign-in as admin. For a rollback, check out an older Git state and rebuild. If schema changes go wrong, the last backup is restored.

## Open Questions

- **Details of the sharing model (for `core-ledger`):** The basic decision has been made: sharing per object with `OWNER`/`EDITOR`/`VIEWER`, subordinate data inherits, only existing users are invited (see proposal.md). Still open is how transactions on an account are handled that are also assigned to an object. Proposal: write only with rights on the account, read with rights on the account or the object. Also open is which minimal user data (display name, username) non-admins may see when choosing whom to invite. Neither changes the scaffold.
