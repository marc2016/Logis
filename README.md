# Logis

Self-hosted platform for finances, real estate, vacation rental and investments. It is meant for overview and taxes and does not replace double-entry bookkeeping.

Logis is a **modular monolith**. A core with sign-in, users and a module registry hosts domain modules that can be switched on and off via configuration. Planning lives under [`openspec/`](openspec/).

```
Internet --TLS--> Reverse proxy (existing)
                       |
          LOGIS_BIND:LOGIS_PORT
                       |
              frontend (nginx)      React app, security headers, rate limit
                       | /api/
              backend (Spring Boot) internal only
                       |
              db (Postgres 17)      internal only  <--  backup (daily pg_dump -> ./backups)
```

| Area | Technology |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Modulith, Spring Security, JPA, Flyway |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4, shadcn/ui, TanStack Query, React Router |
| Database | PostgreSQL 17, one schema per module (`core`, `vacationrental`, …) |
| Operations | Docker Compose: `frontend`, `backend`, `db`, `backup` |

## Prerequisites

- Docker with Docker Compose v2. The images build natively on **amd64** (mini PC) and **arm64** (Mac).
- An existing reverse proxy with TLS, if Logis should be reachable from the internet.
- For development: JDK 21 or newer and Node.js 22 or newer.

## Setup (mini PC)

```sh
git clone <repo-url> logis && cd logis
cp .env.example .env
openssl rand -base64 32          # put the result into .env as LOGIS_ENCRYPTION_KEY
$EDITOR .env                     # passwords, admin, port, trusted proxies
./start.sh prod                  # checks .env and starts: docker compose up -d --build
docker compose ps                # all services "healthy"
```

Then set up an HTTPS route to `LOGIS_BIND:LOGIS_PORT` in the reverse proxy (see below). Open the domain and sign in with `LOGIS_ADMIN_USERNAME` and `LOGIS_ADMIN_PASSWORD`. At the first sign-in you set up two-factor authentication, e.g. with Aegis, 2FAS or Google Authenticator, and store the 10 recovery codes safely. The initial admin is only created as long as no user exists.

**Update:** `git pull && docker compose up -d --build`. The data stays in the volume `pgdata`.

### Important settings (`.env`)

| Variable | Meaning |
|---|---|
| `LOGIS_BIND`, `LOGIS_PORT` | Address and port of the only published port. Default `127.0.0.1:8080`, i.e. reachable only for a proxy on the same host. |
| `LOGIS_TRUSTED_PROXIES` | Addresses or networks whose `X-Forwarded-For` counts as the real client IP, comma-separated |
| `LOGIS_ENCRYPTION_KEY` | Key for the TOTP secrets, 32 bytes in Base64. **Back it up separately from the backups!** |
| `LOGIS_ADMIN_USERNAME`, `LOGIS_ADMIN_PASSWORD` | Initial admin, used only at the very first start. At least 12 characters. |
| `LOGIS_MODULES_<MODULE>_ENABLED` | Switch a module on or off, e.g. `LOGIS_MODULES_VACATIONRENTAL_ENABLED=false` |
| `LOGIS_AI_ENABLED`, `LOGIS_ANTHROPIC_API_KEY`, `LOGIS_AI_MODEL` | Optional AI fallback of the mail import (see Vacation rental > Mail import). Off by default; model default `claude-opus-5-5`. |
| `BACKUP_TIME`, `BACKUP_RETENTION_DAYS` | Time of the daily backup and retention in days |

> **Key lost?** Without `LOGIS_ENCRYPTION_KEY` the stored TOTP secrets can no longer be decrypted. All users then have to set up 2FA again (see Emergency).

## Connecting the reverse proxy

The proxy terminates TLS and forwards **everything** under the domain to Logis. It must set `X-Forwarded-For` and `X-Forwarded-Proto`. Only then does the session cookie get the `Secure` attribute, and only then do the rate limit and the log see the real client IP. Common proxies do this by default.

`LOGIS_TRUSTED_PROXIES` must contain the address with which the proxy arrives at the container:

- **Proxy directly on the host** (Caddy or nginx as a service) with `LOGIS_BIND=127.0.0.1`: Docker forwards the connection through the bridge gateway. The default `172.16.0.0/12,127.0.0.1` covers this.
- **Proxy on another machine:** set `LOGIS_BIND` to the mini PC's LAN address and `LOGIS_TRUSTED_PROXIES` to the proxy's IP.

Examples:

```caddyfile
# Caddy (on the mini PC)
logis.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

```yaml
# Traefik (file provider, Traefik runs on the host or in the host network)
http:
  routers:
    logis:
      rule: Host(`logis.example.com`)
      entryPoints: [websecure]
      tls: { certResolver: letsencrypt }
      service: logis
  services:
    logis:
      loadBalancer:
        servers:
          - url: http://127.0.0.1:8080
```

**Nginx Proxy Manager:** Create a proxy host for the domain, target `http`, `<IP of the mini PC>`, port `LOGIS_PORT`. Force SSL via Let's Encrypt. Set `LOGIS_BIND` and `LOGIS_TRUSTED_PROXIES` accordingly.

**Uploads:** Documents and receipts may be up to 10 MB, so the proxy must accept request bodies of at least 11 MB. Caddy and Traefik have no limit by default. nginx and Nginx Proxy Manager allow only 1 MB by default: add `client_max_body_size 11m;` (in Nginx Proxy Manager under *Advanced > Custom Nginx Configuration*).

**Check:** After signing in through the domain, the cookie `LOGIS_SESSION` must carry the attributes `Secure; HttpOnly; SameSite=Lax` in the browser DevTools. After a deliberately wrong password, `docker compose logs backend | grep auth.failed` must show your real IP and not the proxy's.

## Users

- Only admins create users, under *Einstellungen > Benutzer*. There is no self-registration.
- New users set their own password and set up 2FA at their first sign-in.
- Admins can disable users and reset their password or 2FA. The affected person's sessions end immediately.
- Under *Mein Konto* everyone changes their password and generates new recovery codes.

## Finances and sharing

- **Owners (legal entities)** separate accounts for tax purposes, e.g. „Anna privat“ and „GbR Ostsee“. Every user automatically has their private owner. Further ones are created under *Finanzen > Eigentümer*. Only whoever created an owner manages it and creates accounts in it.
- **Accounts** belong to exactly one owner. Whoever creates an account is its **owner** in terms of sharing.
- **Shares** apply per account. The owner invites existing users via *Freigeben*:

  | Role | May |
  |---|---|
  | Lesen (`VIEWER`) | view the account and its transactions |
  | Bearbeiten (`EDITOR`) | additionally enter, change and delete transactions and transfers |
  | Owner (`OWNER`) | additionally edit and archive the account and manage shares |

  Without a share an account is invisible, even to admins. There is always at least one owner.
- **Categories** are one shared tree for all users and are maintained by admins under *Einstellungen > Kategorien*. Categories in use can only be archived.
- **Transfers** between own accounts change the balances but do not count as income or expense.

## Real estate

- **Properties** (*Immobilien > Objekte*) belong to exactly one owner (legal entity), like accounts. Whoever creates a property is its **owner**.
- **Units:** Every property has at least one unit. A holiday home is created as a property with one unit; Logis creates that unit automatically and does not show a separate unit level. A house with several apartments gets one unit per apartment.
- **Shares** work on two levels:

  | Shared | Roles | Who manages it |
  |---|---|---|
  | Property | Lesen, Bearbeiten, Owner | owners of the property |
  | Single unit (only for properties with several units) | Lesen, Bearbeiten | owners of the property |

  The effective role on a unit is the higher of the property role and the unit role. Whoever only has a share on a unit sees the property's name and address and that unit, but no other units and nothing attributed to the property as a whole.
- **Attributed transactions:** A transaction can be attributed to a property or a unit (field *Objekt* in the transaction dialog). This needs *Bearbeiten* on the account and on the property or unit. People who see the property or unit but not the account can read these transactions without account name, balance or note; changing them still requires rights on the account. Transfers cannot be attributed.
- **Finances on the property:** The property page shows *Finanzen*: income, expenses and result per month and year, and every item of the year. *+ Kosten* records a cost (e.g. a repair) directly on the property or one of its units, with category, recipient, note and receipts (PDF or image, at most 10 MB each). No account is needed. Settled vacation rental statements appear automatically: the gross amount as income, commission and cleaning as expenses, all on the statement date. Transactions attributed to the property are listed separately and do not count in the result; linking them to costs comes with the bank import. Recording costs needs *Bearbeiten* on the property or unit. Costs on the property as a whole are visible only to people with a role on the property.
- **Deleting** a property is only possible while no transactions, costs or bookings refer to it; otherwise archive it.

## Vacation rental

The module *Ferienvermietung* records stays in units with the usage *Ferienvermietung*. Rights come from the unit: *Lesen* on the property or unit shows bookings and statements, *Bearbeiten* records and changes them.

- **Bookings** (*Buchungen*, *Kalender*): arrival, departure, booking number of the provider, channel, guests, expected rent and the guest. *+ Buchung* at the top right opens the same dialog in the grid and the calendar. Stays of a unit must not overlap; arrival on another stay's departure day is fine. A cancelled booking keeps its data and frees the calendar.
- **Documents** such as the provider's booking confirmation can be attached to a booking (PDF or image, at most 10 MB each).
- **Statements** (*Abrechnungen*): one per unit and statement of the service provider. Assign the bookings with gross, commission and cleaning; net and payout are computed. A booking on a statement counts as settled; a cancelled booking can carry a cancellation payout.
- **Auswertung:** booked nights and occupancy per month (stays across a month boundary are split by night), settled net income on the statement date and planned rent in the month of arrival.
- **Guest data:** Name, address, phone and email are stored. Only people with *Bearbeiten* see the contact data and the documents; *Lesen* shows the guest name only. Guest data is never deleted automatically: *Gastdaten löschen* in the booking removes name and contact data for good and keeps the booking and its amounts. Documents stay and have to be deleted one by one, because they usually contain guest data too.

### Mail import

Logis can read the booking mails of the rental agencies (confirmations, short-notice notices, changes, cancellations) from an IMAP mailbox and prepare bookings for you to confirm.

- **Set up** under *Posteingang* (group *Allgemein*); the inbox is part of the core, vacation rental handles the booking mails in it: IMAP server, port, user, password, folder and a cut-off date. Best use a separate folder or address that only receives the agency mails (a filter rule or forwarding in your mail account). Mails you forward yourself work too, inline ("WG:", "Fwd:") or as an attachment. The password is stored encrypted with `LOGIS_ENCRYPTION_KEY`. Logis polls every 5 minutes, only mails from the cut-off date on, and never changes or deletes anything in the mailbox. Older mails can be fetched once from a chosen date.
- **Recognised by rules:** Fewo & Meer (confirmation, Last-Minute, cancellation) and Holidu/DS Destination Solutions (confirmation). For the first mail of a property, assign its unit once; Logis remembers it per agency.
- **Result:** A new booking number becomes a booking marked *zu prüfen* (it already blocks the calendar); a change or cancellation is proposed on the existing booking. Confirm, apply or discard it in the booking dialog. The original mail is attached to the booking as `.eml`.
- **AI fallback (optional):** With `LOGIS_AI_ENABLED=true` and `LOGIS_ANTHROPIC_API_KEY`, mails no rule recognises are read by Claude (`LOGIS_AI_MODEL`). **The mail text, including guest data, is then sent to Anthropic.** Without AI, such mails stay in the inbox as *Nicht erkannt*.
- **Privacy:** The inbox is visible only to its owner. Deleting a mail there removes its content and guest data; the copy attached to a booking stays until you delete that document.
- **Network:** The backend reaches the IMAP server (and the Claude API) through the compose network `egress`, which only allows outbound connections; database and backup have no access to the outside.

## Backup and restore

The `backup` service writes a backup to `./backups/logis_<YYYYMMDD_HHMMSS>.dump` every day at `BACKUP_TIME` and deletes backups older than `BACKUP_RETENTION_DAYS`.

```sh
docker compose exec backup backup.sh --now      # back up now
ls -l backups/
```

> The backups live on the same machine as the database. Also copy `./backups` regularly to another machine, a NAS or cloud storage, and back up `.env` with the key separately from that.

**Restore** into an empty database:

```sh
docker compose down -v                           # CAUTION: deletes the current database
docker compose up -d --wait db
docker compose exec -T db sh -c 'pg_restore --clean --if-exists --no-owner -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < backups/logis_YYYYMMDD_HHMMSS.dump
docker compose up -d
```

The `.env` must contain the same `LOGIS_ENCRYPTION_KEY` as when the backup was created.

## Emergency: admin locked out

Run the following commands on the mini PC in the Logis directory. Afterwards every session of the user ends.

**2FA lost** (phone gone and no recovery codes left): 2FA is reset and set up again at the next sign-in.

```sh
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
  DELETE FROM core.recovery_codes WHERE user_id = (SELECT id FROM core.users WHERE username = '"'"'admin'"'"');
  UPDATE core.users SET totp_secret_enc = NULL, totp_last_step = NULL, failed_attempts = 0,
         locked_until = NULL, session_version = session_version + 1 WHERE username = '"'"'admin'"'"';"'
```

**Password forgotten:** Generate a bcrypt hash, set it as the initial password and thereby force a password change at the next sign-in.

```sh
HASH=$(docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" 'new-initial-password' | cut -d: -f2)
docker compose exec db psql -U logis -d logis -c \
  "UPDATE core.users SET password_hash = '{bcrypt}$HASH', password_change_required = true,
          failed_attempts = 0, locked_until = NULL, session_version = session_version + 1
   WHERE username = 'admin';"
```

Replace `admin` with the username. If `POSTGRES_USER` or `POSTGRES_DB` differ in `.env`, also replace `logis` in the second command.

**Only owner disabled:** If the only owner of accounts has been disabled, nobody can manage those accounts anymore. The simplest fix is to re-enable the user. If another user should take over instead, the following command gives them the owner role wherever the old user was owner. Replace `old` and `new` with the usernames.

```sh
docker compose exec -T db psql -U logis -d logis -v old=carla -v new=admin <<'SQL'
INSERT INTO core.object_shares (object_type, object_id, user_id, role)
SELECT s.object_type, s.object_id, (SELECT id FROM core.users WHERE username = :'new'), 'OWNER'
FROM core.object_shares s
WHERE s.user_id = (SELECT id FROM core.users WHERE username = :'old') AND s.role = 'OWNER'
ON CONFLICT (object_type, object_id, user_id) DO UPDATE SET role = 'OWNER';
SQL
```

The old user's owners (legal entities) stay with them. The accounts in them can be managed through the new owner role afterwards, though.

## Development

Quickest with the start script:

```sh
./start.sh          # Postgres + backend (dev profile) + frontend; Ctrl+C stops backend and frontend
./start.sh stop     # stop the development database
./start.sh prod     # production stack (checks .env for placeholders, then docker compose up -d --build)
```

Or individually:

```sh
docker compose -f compose.dev.yml up -d                                # Postgres only, on localhost:5432
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev    # http://localhost:8080
cd frontend && npm install && npm run dev                              # http://localhost:5173
```

The `dev` profile has the admin `admin` with the password `admin-dev-password` and a fixed development key. Vite forwards `/api` to the backend.

**Tests:**

```sh
cd backend && ./mvnw verify        # unit, integration (Testcontainers) and module boundary tests
cd frontend && npm test            # Vitest + Testing Library
cd frontend && npm run build       # type check + production build
```

Testcontainers needs a reachable Docker daemon. With **Colima** two variables are required:

```sh
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

### Creating a new module

1. Backend: Create the package `app.logis.modules.<id>` with a `package-info.java` (`@ApplicationModule(id = "<id>")`). Every component gets `@ConditionalOnLogisModule("<id>")`. The module provides a `ModuleDescriptor` bean, its migrations live under `db/migration/<id>`, and the schema is added to `spring.flyway.schemas`.
2. Frontend: `src/modules/<id>/index.ts` exports a `LogisModule` and is registered in `src/app/registry.ts`.
3. `ModularityTest` ensures that the module only uses the public API of the core.

## Security at a glance

- Sign-in with password and mandatory TOTP, plus 10 single-use recovery codes. The TOTP secrets are encrypted with AES-256-GCM.
- After 5 failed attempts the user is locked; the lockout grows up to 15 minutes. In addition, a per-IP rate limit applies to the sign-in steps.
- Sessions end after 30 minutes of inactivity and after 12 hours at the latest. The cookie is `HttpOnly`, `SameSite=Lax` and `Secure` behind HTTPS. There is CSRF protection.
- Responses carry the headers HSTS, CSP, `X-Frame-Options`, `nosniff` and `Referrer-Policy`. Error responses contain no internal details.
- Backend and database are reachable only in the internal Docker network. `/actuator` is not reachable from outside.
