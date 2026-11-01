# Spec Delta

## Purpose

Logis is self-hosted as a container stack on a mini PC and reachable from the internet through an existing reverse proxy with TLS. This capability defines the observable operational behavior: reachability, isolation of internal services, hardening, health checks, configuration and backups.

## ADDED Requirements

### Requirement: Single entry point for the reverse proxy
The stack MUST publish exactly one HTTP port on the host. Both the web interface and the API under `/api/` SHALL be reachable through it. Port number and bind address MUST be configurable, so that the port can be made reachable for the reverse proxy only.

#### Scenario: Interface and API on the same port
- **WHEN** the stack is running and the reverse proxy calls the configured port
- **THEN** the web interface is served, and calls under `/api/` reach the backend

#### Scenario: Deep link after reload
- **WHEN** the browser opens a subpage of the interface (e.g. `/ferienvermietung/belegung`) directly
- **THEN** the interface is served and not an HTTP 404 from the web server

#### Scenario: Bind address
- **WHEN** the bind address is set to `127.0.0.1`
- **THEN** the port is not reachable from other machines on the network

### Requirement: Running behind a reverse proxy
The system MUST accept `X-Forwarded-For` and `X-Forwarded-Proto` only from configured trusted proxy addresses. The accepted client IP MUST be used for rate limiting and for logging sign-ins. Session and CSRF cookies MUST be set with the `Secure` attribute if, according to the proxy, the request came over HTTPS.

#### Scenario: Client IP behind the proxy
- **WHEN** a failed sign-in arrives through the trusted proxy with `X-Forwarded-For: 203.0.113.7`
- **THEN** the system logs the client IP `203.0.113.7`

#### Scenario: Forged header
- **WHEN** a request from an untrusted address sends the header `X-Forwarded-For: 10.0.0.1`
- **THEN** the system uses the actual sender address and not `10.0.0.1`

#### Scenario: Secure cookies
- **WHEN** sign-in happens over HTTPS at the reverse proxy
- **THEN** the session cookie carries the attributes `Secure`, `HttpOnly` and `SameSite=Lax`

### Requirement: Security headers
All responses of the entry point MUST contain the headers `Strict-Transport-Security`, `Content-Security-Policy` (without inline scripts and allowing embedding only by the own origin), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` and `Referrer-Policy: same-origin`.

#### Scenario: Headers present
- **WHEN** a client retrieves the start page or an API endpoint
- **THEN** the response contains all of the headers listed

### Requirement: No internal details exposed
Error responses MUST NOT contain stack traces, framework or server versions, or internal host names. The web server's server identifier MUST NOT name a version.

#### Scenario: Unexpected error
- **WHEN** an unexpected error occurs during an API request
- **THEN** the system responds with HTTP 500 and a generic message without technical details

### Requirement: Internal services unreachable
Backend and database MUST NOT be reachable through a host port. Operational endpoints of the backend (metrics, health details) MUST NOT be reachable through the entry point.

#### Scenario: Database port
- **WHEN** the stack is running and a client on the host addresses the Postgres port
- **THEN** no connection is possible

#### Scenario: Operational endpoints
- **WHEN** a client calls a path under `/actuator/` through the entry point
- **THEN** no operational data of the backend is served

### Requirement: Health endpoint
The system SHALL provide a health status under `/api/health` without sign-in. It MUST report HTTP 200 when the backend can reach the database, and HTTP 503 otherwise. The response MUST NOT contain internal details such as host names or versions.

#### Scenario: Healthy
- **WHEN** backend and database are running and `/api/health` is called
- **THEN** the system responds with HTTP 200

#### Scenario: Database unreachable
- **WHEN** the database is unreachable and `/api/health` is called
- **THEN** the system responds with HTTP 503

### Requirement: Ordered startup
The backend MUST accept requests only once the database is ready and all migrations have been applied. If a migration fails, the backend MUST NOT start.

#### Scenario: Cold start
- **WHEN** the whole stack is started on a fresh host
- **THEN** the application is usable after startup without manual intervention, and the database schema is fully created

### Requirement: Configuration through the environment
All secrets (database password, initial admin password, key for encrypting the TOTP secrets) and deployment-specific settings (port, bind address, trusted proxies, enabled modules, backup retention) MUST be set through environment variables or a `.env` file. Container images MUST NOT contain secrets. If the encryption key is missing, startup MUST abort.

#### Scenario: Template
- **WHEN** an operator checks out the repository
- **THEN** they find a `.env.example` with all required settings and no real secrets

#### Scenario: Missing key
- **WHEN** the backend starts without an encryption key
- **THEN** startup aborts with a message naming the missing setting

### Requirement: Persistence across restarts
Data MUST survive a restart and a recreation of the containers.

#### Scenario: Update
- **WHEN** the images are rebuilt and the containers recreated
- **THEN** all previously stored data is present

### Requirement: Automatic database backups
The stack SHALL automatically write a full backup of the database to a directory on the host every day. Backups older than the configured retention period MUST be deleted automatically. A backup MUST be restorable into an empty database. Together with the encryption key, sign-in with existing two-factor authentication MUST be possible afterwards.

#### Scenario: Daily backup
- **WHEN** the stack runs for more than a day
- **THEN** the backup directory on the host contains a backup with a timestamp in the file name

#### Scenario: Retention
- **WHEN** the retention period is set to 14 days and a backup is older than 14 days
- **THEN** that backup is deleted on the next backup run

#### Scenario: Restore
- **WHEN** a backup is restored into an empty database following the documented instructions and the same encryption key is configured
- **THEN** the application starts with the backed-up data, and users can sign in with their existing two-factor authentication

### Requirement: Runs on amd64 and arm64
The container images MUST build and run on both amd64 (mini PC) and arm64 (development machine) without changing the build files.

#### Scenario: Build on the mini PC
- **WHEN** the stack is built and started on the amd64 mini PC
- **THEN** all containers run without emulation
