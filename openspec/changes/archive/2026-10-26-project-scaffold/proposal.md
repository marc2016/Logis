# Proposal

## Why

Logis is meant to become a self-hosted platform for overview and taxes. It covers one's own finances, real estate, vacation rental and investments. Several users access it over the internet. The repo contains no code yet. Before the first domain module (vacation rental) can be built, it needs a solid and secured frame: a modular monolith with fixed module boundaries, an app shell that modules plug into, sign-in for multiple users with two-factor protection, and a Docker setup that runs on the mini PC (amd64) behind an existing reverse proxy.

Guiding decisions from exploration that apply beyond this change:

- Logis serves overview and taxes (Anlage V, EÜR, statements for partners) and is **not** double-entry bookkeeping.
- Architecture: **modular monolith**, one application with strictly separated modules that can be switched on and off via configuration. There is no runtime plugin system.
- Base package `app.logis`.
- Frontend: React, TypeScript, Tailwind and shadcn/ui in a **light design**, with a collapsible sidebar on the left that contains one group per module.
- Operations: three containers (nginx with the React build, Spring Boot, Postgres) plus a backup container. Builds run directly on the mini PC for now.
- **Reachable from the internet** through an existing reverse proxy that handles TLS.
- **Multiple users** with roles. Two-factor sign-in (TOTP) is mandatory for everyone.
- **Sharing per object:** Whoever creates a shareable object (e.g. an account or a property) is its owner. They can invite existing users with read access (`VIEWER`) or write access (`EDITOR`). Subordinate data such as transactions or bookings inherit the rights. The system role `ADMIN` grants no automatic access to domain data. Invitations by link or email are not planned for now. The permission model arrives with `core-ledger`.

Roadmap in which this change is the first step:

1. `project-scaffold` (this change)
2. `core-ledger`: legal entities, accounts, categories, transactions
3. `core-bank-import`: CSV/CAMT import, duplicate detection
4. `realestate-minimal`: properties and units
5. `vacation-rental`: bookings (manual entry, multiple channels), occupancy calendar

Later: AI inbox, reports, investments, private finances, long-term rentals and utility cost statements.

## What Changes

- New monorepo layout with `backend/` (Spring Boot 3, Java 21, Maven), `frontend/` (Vite, React, TypeScript, Tailwind, shadcn/ui) and Compose files for production and development.
- A backend scaffold as a modular monolith: a core area `app.logis.core` and modules under `app.logis.modules.*`. Module boundaries are enforced by an automated test.
- A mechanism to switch modules on and off. The backend delivers the list of active modules to the frontend.
- An empty placeholder module `vacationrental` that proves the mechanism end to end and is the starting point for change 5.
- Database migrations with a separate Postgres schema per module.
- App shell in the frontend: a module registry, a left sidebar with one group per active module, collapsible to icon mode, plus an overview placeholder, „Mein Konto“ and user management for admins.
- Sign-in for multiple users:
  - Sign-in with password and **mandatory TOTP**, set up at first sign-in, with recovery codes.
  - Brute-force protection (rate limit and lockout after failed attempts).
  - Limited session duration, CSRF protection and secure cookies.
  - The first admin is created from the environment at first startup.
- User management by admins: create users, disable them, reset password and 2FA. Roles `ADMIN` and `USER`. There is no self-registration.
- Docker setup for production behind a reverse proxy:
  - nginx is the only entry point and forwards `/api` to the backend. Backend and database are reachable internally only.
  - Security headers, health checks and a daily database backup container.
- Docker setup for development that starts only Postgres. Backend and Vite run locally with hot reload.

## Capabilities

### New Capabilities

- `module-system`: Modules can be enabled and disabled via configuration. The system exposes the active modules, and disabled modules are unreachable.
- `app-shell`: The frame of the web interface with sidebar navigation, grouped by active modules. The sidebar is collapsible and has routing for module pages.
- `authentication`: Sign-in with password and TOTP, recovery codes, brute-force protection, session duration, sign-out, protection of all domain endpoints, changing one's own password and creation of the initial admin.
- `user-management`: Admins manage users and roles, disable users and reset password or 2FA.
- `deployment`: Operational behavior of the container stack behind a reverse proxy. This includes the single entry point, internal services, security headers, health checks and automatic database backups.

### Modified Capabilities

None. No specs exist yet.

## Impact

- **Code:** New are `backend/`, `frontend/`, `ops/`, `compose.yml`, `compose.dev.yml`, `.env.example` and `README.md`.
- **Dependencies:**
  - Backend: Spring Boot 3 (Web, Security, Data JPA, Validation, Actuator), Spring Modulith, Flyway, PostgreSQL, a TOTP library, Testcontainers.
  - Frontend: Vite, React, React Router, TanStack Query, Tailwind CSS, shadcn/ui and a QR code renderer.
- **Operations:**
  - The mini PC needs Docker and Docker Compose as well as an existing reverse proxy with TLS.
  - Secrets such as the DB password, admin password and the key for encrypting the TOTP secrets come from `.env`, not from the images.
- **Requirement for `core-ledger`:** Sharing per object (see above). The scaffold only defines the system roles `ADMIN`/`USER`. Rights on domain data are added with the first shareable object.
