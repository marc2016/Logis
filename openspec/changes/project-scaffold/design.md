# Design

## Context

Es handelt sich um ein Greenfield-Projekt: Im Repo gibt es bisher nur `openspec/`. Die Motivation und die Leitentscheidungen stehen in proposal.md, die Anforderungen in `specs/`.

Diese Rahmenbedingungen prägen das Design:

- Es gibt einen Entwickler und Betreiber, aber **mehrere Benutzer**.
- Logis ist **aus dem Internet erreichbar** über einen vorhandenen Reverse Proxy auf dem Mini-PC, der TLS übernimmt.
- Entwickelt wird auf einem Mac mit **arm64**, betrieben auf einem Mini-PC mit **amd64** (`x86_64`). Gebaut wird zunächst direkt auf dem Mini-PC.
- Es sind Finanzdaten. Zwei-Faktor-Anmeldung, Schutz vor Brute-Force, Härtung und Datensicherung sind daher Pflicht.
- Folgende Changes (Ledger, Immobilien, Ferienvermietung) sollen sich als Module einfügen, ohne den Rahmen umzubauen.

## Goals / Non-Goals

**Goals:**
- Feste Modulgrenzen, deren Verletzung einen Test scheitern lässt und nicht erst im Code-Review auffällt.
- Ein neues Modul braucht im Backend genau ein Package und im Frontend genau einen Registry-Eintrag.
- Eine Anmeldung, die im Internet ohne vorgeschaltete Authentifizierungsschicht vertretbar ist.
- Der Stack startet mit einem einzigen Befehl auf dem Mini-PC.
- Die lokale Entwicklung bietet Hot Reload für Backend und Frontend, ohne die Images neu zu bauen.

**Non-Goals:**
- TLS-Terminierung und Zertifikate. Das übernimmt der vorhandene Reverse Proxy.
- Zugriffsrechte auf fachliche Daten (z. B. pro Rechtsträger). Siehe Open Questions, das Thema kommt mit `core-ledger`.
- Passwort-vergessen-Funktion per E-Mail. Stattdessen setzt ein Admin das Passwort zurück, ein E-Mail-Versand existiert noch nicht.
- Passkeys/WebAuthn. Das wäre später als zweiter Faktor oder Ersatz nachrüstbar.
- CI, eine Image-Registry und Release-Versionierung.
- Sicherung von Dokumenten bzw. Dateien. Es gibt noch keine, das Thema kommt mit der KI-Inbox.

## Decisions

### D1: Repo-Layout

```
logis/
  backend/            Maven, Spring Boot 3.x, Java 21
  frontend/           Vite, React, TypeScript, Tailwind, shadcn/ui
  ops/backup/         Backup-Skript fuer den Backup-Container
  compose.yml         Produktion: frontend, backend, db, backup
  compose.dev.yml     Entwicklung: nur db
  .env.example
  README.md           Start, Reverse Proxy, Entwicklung, Backup/Restore
```

### D2: Spring Modulith für die Modulgrenzen

```
app.logis
  LogisApplication
  core/                   Kern
    auth/                 Login-Schritte, TOTP, Recovery, Lockout, Sessions
    users/                User, Rollen, Admin-API, "Mein Konto"
    modules/              ModuleDescriptor, GET /api/modules
    web/                  Fehlerbehandlung, Health
  shared/                 Querschnitt ohne Fachlogik (Crypto, spaeter Money)
  modules/
    vacationrental/       Platzhalter: GET /api/vacationrental/status
```

- Ein Test mit `ApplicationModules.of(LogisApplication.class).verify()` prüft, dass Module nur die API-Packages anderer Module nutzen und dass es keine Zyklen gibt.
- `modules.*` darf auf `core` zugreifen, `core` aber nie auf `modules.*`.
- *Alternative:* getrennte Maven-Module pro Fachmodul. Das ergibt härtere Grenzen über Compile-Abhängigkeiten, kostet aber viel Build-Konfiguration für eine einzige Person. Ein Wechsel ist später möglich, weil die Package-Grenzen dieselben sind.

### D3: Module ein- und ausschalten

- Jedes Modul hat eine `@Configuration` mit `@ConditionalOnProperty(prefix = "logis.modules.<id>", name = "enabled", matchIfMissing = true)`. Alle Beans des Moduls einschließlich der Controller hängen an dieser Konfiguration. Ist das Modul deaktiviert, existieren keine Controller, und Spring antwortet mit 404.
- Jedes aktive Modul registriert ein `ModuleDescriptor`-Bean (`id`, `label`, `order`). `GET /api/modules` liefert alle registrierten Descriptoren.
- In Docker werden die Einstellungen über Umgebungsvariablen gesetzt (`LOGIS_MODULES_VACATIONRENTAL_ENABLED=false`, Relaxed Binding).
- Das Schalten gilt global. Module pro Benutzer freizuschalten ist bewusst nicht vorgesehen, solange das Thema Datenzugriff offen ist.
- *Alternative:* Feature-Flags in der Datenbank mit einer Admin-Oberfläche. Das wäre zur Laufzeit umschaltbar, bräuchte aber Bean-Refresh und UI. Für einen einzelnen Betreiber reichen ein Neustart und `.env`.

### D4: Datenbank und Migrationen

- Es gibt eine Postgres-Instanz (Version 17) und **ein Schema pro Bereich**: `core` sowie eines pro Modul (`vacationrental` usw.).
- Es gibt eine Flyway-Instanz mit den Locations `classpath:db/migration/core` und `classpath:db/migration/<modul>`. Versionsnummern sind global eindeutig und werden mit Zeitstempel vergeben (`V20261025_1200__core_users.sql`).
- **Migrationen aller Module laufen immer, auch für deaktivierte Module.** Schema und Daten bleiben dadurch beim Deaktivieren erhalten.
- JPA läuft mit `ddl-auto=validate`, Flyway ist die einzige Quelle für das Schema.
- *Alternative:* eine Flyway-Instanz pro Modul mit eigener History-Tabelle. Das bringt Probleme mit der Reihenfolge, wenn Module Fremdschlüssel auf `core` setzen.

### D5: Benutzer, Rollen und Sitzungen

- Tabelle `core.users`:
  - Stammdaten: `id`, `username` (unique über `lower(username)`), `display_name`, `role` (`ADMIN`/`USER`), `enabled`.
  - Passwort: `password_hash`, `password_change_required`.
  - Zwei-Faktor: `totp_secret_enc` (nullable), `totp_last_step`.
  - Sperre: `failed_attempts`, `locked_until`.
  - Zeitstempel: `last_login_at`, `created_at`.
- Tabelle `core.recovery_codes` (`user_id`, `code_hash`, `used_at`).
- Passwörter werden über `DelegatingPasswordEncoder` gespeichert, Standard ist bcrypt. Die Mindestlänge von 12 Zeichen wird zentral validiert.
- **Sitzung:** Spring-Session-Cookie (`HttpOnly`, `SameSite=Lax`, `Secure` laut `X-Forwarded-Proto`). Inaktivitäts-Timeout 30 min über `server.servlet.session.timeout`. Das absolute Limit von 12 h prüft ein Filter anhand eines Login-Zeitstempels in der Session.
- Die Sessions liegen im Speicher. **Sitzungen werden über eine Sitzungsversion beendet:** `core.users.session_version` wird beim Deaktivieren, beim Zurücksetzen und bei einer Passwortänderung erhöht. Ein Filter (`SessionAuthenticationFilter`) vergleicht sie bei jeder Anfrage mit der Version in der Session, prüft dabei auch Inaktivität (30 min) und absolute Dauer (12 h) und baut den SecurityContext mit der **aktuell gespeicherten** Rolle auf. Rollenänderungen greifen dadurch bei der nächsten Anfrage.
  - *Ursprünglich geplant:* ein `SessionRegistry`. Dagegen sprach bei der Umsetzung, dass dort gespeicherte Rollen erst nach einem neuen Login aktualisiert würden. Außerdem kostet der neue Weg nur eine Abfrage per Primärschlüssel pro Anfrage.
- Ohne vollständige Sitzung gibt es 401 über einen `HttpStatusEntryPoint` und keine Weiterleitung.
- **CSRF:** `CookieCsrfTokenRepository` mit einem lesbaren `XSRF-TOKEN`-Cookie. Das Frontend sendet den Header `X-XSRF-TOKEN` mit.
- **Initialer Admin:** Ein `ApplicationRunner` legt ihn an, wenn `core.users` leer ist. Er nutzt `LOGIS_ADMIN_USERNAME` und `LOGIS_ADMIN_PASSWORD`, vergibt die Rolle `ADMIN`, setzt `password_change_required=false` und lässt TOTP leer, sodass die Einrichtung beim ersten Login erzwungen wird. Fehlende oder zu kurze Werte lassen den Start scheitern.
- *Alternative:* JWT im Local Storage. Das ist anfälliger für XSS, und ein sofortiges Beenden der Sitzung beim Deaktivieren ist damit nicht möglich.

### D6: Mehrstufige Anmeldung als Zustandsautomat

Spring Security 6 bringt keine eingebaute MFA mit. Die Anmeldung wird deshalb als Zustand in der Session modelliert:

```
                POST /api/auth/login (user+pw ok)
  [anonym] -----------------------------------------> [PASSWORD_OK]
                                                         |
                       totp_secret_enc == null           |  totp vorhanden
                  +--------------------------------------+------------------+
                  v                                                         v
       [TOTP_ENROLLMENT_REQUIRED]                                    [TOTP_REQUIRED]
         POST /api/auth/totp/setup  -> Secret + otpauth-URI            POST /api/auth/totp
         POST /api/auth/totp/confirm (code ok) -> 10 Recovery-Codes      (TOTP- oder Recovery-Code ok)
                  |                                                         |
                  +-------------------------+-------------------------------+
                                            v
                         password_change_required ?
                          ja |                          | nein
                             v                          v
               [PASSWORD_CHANGE_REQUIRED]         [AUTHENTICATED]  -> ROLE_ADMIN / ROLE_USER
                 POST /api/auth/password ---------------^
```

- Jeder Zwischenzustand ist eine eigene Authority (`AUTH_STAGE_*`) ohne fachliche Rechte. Nur `AUTHENTICATED` erhält `ROLE_USER` bzw. `ROLE_ADMIN`. Fachliche Endpunkte verlangen eine dieser Rollen, wodurch die Spec-Anforderung „erst nach beiden Schritten“ automatisch erfüllt ist.
- Beim Zustandswechsel wird die Session-ID neu vergeben (Schutz vor Session Fixation).
- `GET /api/auth/me` liefert `{ stage, user? }`. Das Frontend leitet daraus ab, welcher Schritt angezeigt wird.
- **TOTP:** RFC 6238 mit SHA-1, 6 Stellen, 30 s und ±1 Zeitschritt Toleranz. Ein wiederverwendeter Code wird über `totp_last_step` abgewiesen, denn ein Code ist nur gültig, wenn sein Schritt größer als der zuletzt verwendete ist. Bibliothek: `dev.samstevens.totp`.
- **TOTP-Secret verschlüsselt:** AES-256-GCM mit dem Schlüssel aus `LOGIS_ENCRYPTION_KEY` (Base64, 32 Byte). So sind Secrets in Backups ohne den Schlüssel wertlos. Fehlt der Schlüssel, scheitert der Start.
- **Wiederherstellungscodes:** 10 Stück zu je 10 Zeichen in Base32, gruppiert (`xxxxx-xxxxx`). Sie werden als bcrypt-Hash gespeichert, und `used_at` macht jeden Code einmalig.
- *Alternative:* Spring Security 7 mit eingebauter MFA. Es ist mit der Vorgabe Spring Boot 3.x nicht verfügbar. Beim späteren Upgrade lässt sich prüfen, ob der eigene Automat ersetzt werden kann.

### D7: Schutz vor Brute-Force

Er besteht aus zwei Schichten:

1. **nginx:** `limit_req` auf `/api/auth/` mit dem Schlüssel aus der echten Client-IP (siehe D9), etwa 10 Anfragen pro Minute und Burst 5. Bei Überschreitung gibt es 429.
2. **Backend pro Benutzer:** `failed_attempts` zählt Fehlschläge im Passwort- und im Code-Schritt. Ab 5 Fehlschlägen gilt `locked_until = now + min(2^(n-5), 15) min`. Ein Erfolg setzt den Zähler zurück. Eine Sperre wird geprüft, bevor das Passwort geprüft wird.

- **Unbekannter Benutzer:** Das Backend führt trotzdem einen bcrypt-Abgleich gegen einen Dummy-Hash aus. So lässt sich an der Antwortzeit nicht erkennen, ob ein Benutzer existiert.
- Fehlschläge werden als strukturierte Logzeile `auth.failed user=<name> ip=<ip> stage=<stage>` geschrieben, ohne Passwort und ohne Code.

### D8: Benutzerverwaltung

- Endpunkte für Admins unter `/api/admin/users`: `GET` (Liste), `POST` (Anlegen mit Startpasswort, dabei `password_change_required=true`), `PATCH /{id}` (Anzeigename, Rolle, `enabled`), `POST /{id}/reset-password` und `POST /{id}/reset-totp`.
- „Mein Konto“ unter `/api/account`: `POST /password` (aktuelles und neues Passwort) und `POST /recovery-codes` (neu erzeugen).
- Die Invariante „mindestens ein aktiver Admin“ und die Regel „nicht sich selbst deaktivieren“ prüft ein Service in derselben Transaktion. Die Zählung aktiver Admins läuft mit `SELECT … FOR UPDATE`, damit zwei gleichzeitige Herabstufungen sich nicht gegenseitig durchlassen.
- Benutzer werden nie gelöscht, nur deaktiviert. Fachliche Daten werden ihnen später als Ersteller oder Bearbeiter zugeordnet.

### D9: Reverse Proxy, Header und Härtung

```
 Internet --TLS--> Reverse Proxy (vorhanden, z. B. Traefik/Caddy/NPM)
                        |  HTTP, X-Forwarded-For / -Proto
                        v
            ${LOGIS_BIND:-127.0.0.1}:${LOGIS_PORT:-8080}
                        |
                 frontend (nginx)  -- set_real_ip_from ${LOGIS_TRUSTED_PROXIES}
                        |              real_ip_header X-Forwarded-For
                        v
                 backend :8080     -- server.forward-headers-strategy=native
                                      (Tomcat RemoteIpValve, internalProxies = frontend-Netz)
```

- **Bind-Adresse:** Standard `127.0.0.1`, passend für einen Proxy auf demselben Host. Läuft der Proxy als Container im selben Docker-Netz, wird `LOGIS_BIND`/`LOGIS_PORT` entsprechend gesetzt oder das externe Netz eingebunden, was im README beschrieben wird.
- **nginx übernimmt die Client-IP** nur von `LOGIS_TRUSTED_PROXIES` und reicht `X-Forwarded-For` und `X-Forwarded-Proto` an das Backend weiter. Das Backend vertraut ausschließlich nginx im internen Netz.
- **Security-Header setzt nginx** für alle Antworten (`add_header … always`):
  - `Strict-Transport-Security: max-age=31536000`
  - `Content-Security-Policy: default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; frame-ancestors 'none'`. `unsafe-inline` ist nur für Styles nötig, weil Radix/shadcn Inline-Styles setzen. Skripte bleiben streng.
  - `X-Content-Type-Options`, `X-Frame-Options: DENY`, `Referrer-Policy: same-origin`
  - Dazu `server_tokens off`.
- **Fehlerantworten:** Ein globaler `@RestControllerAdvice` liefert ein einheitliches `ProblemDetail` ohne Details. `server.error.include-stacktrace=never` und `include-message=never`.
- Der QR-Code für TOTP wird im Frontend aus der `otpauth://`-URI gerendert. Das Secret geht dadurch nicht an einen fremden QR-Dienst, und die CSP bleibt bei `'self'`.

### D10: Health

- Das Actuator-`health` wird **nur intern** auf dem Management-Port `8081` bereitgestellt. Den nutzt der Docker-Healthcheck des Backends. Die Security-Konfiguration erlaubt dort ausschließlich den Health-Endpunkt ohne Anmeldung.
- Ein eigener `GET /api/health` (ohne Anmeldung) prüft die Datenbank per `SELECT 1` und liefert nur `{"status":"UP"}` bzw. 503.
- nginx proxyt ausschließlich `/api/`, `/actuator` ist dadurch von außen nicht erreichbar.

### D11: Frontend-Architektur

```
frontend/src/
  main.tsx, App.tsx         Router, QueryClient, AuthGate
  app/
    layout/AppShell.tsx     SidebarProvider + AppSidebar + <Outlet/>
    registry.ts             Liste aller Frontend-Module
    NotFound.tsx
  core/
    auth/                   LoginFlow (Passwort -> TOTP | Einrichtung -> Recovery-Codes
                            -> ggf. Passwortwechsel), useSession, api
    account/                "Mein Konto": Passwort aendern, Recovery-Codes neu
    admin/users/            Benutzerliste, Anlegen-/Bearbeiten-Dialoge, Zuruecksetzen
    dashboard/              Uebersicht (Platzhalter)
  modules/
    vacationrental/
      index.ts              export const module: LogisModule
      pages/                BelegungPage (Platzhalter)
  lib/api.ts                fetch-Wrapper: credentials, XSRF-Header, 401 -> Login
  components/ui/            shadcn-Komponenten (generiert, im Repo)
```

- **Der Modulvertrag:**
  ```ts
  interface LogisModule {
    id: string;                 // == Backend-Modul-ID
    label: string;              // "Ferienvermietung"
    icon: LucideIcon;
    order: number;
    basePath: string;           // "ferienvermietung"
    navItems: { label: string; path: string; icon?: LucideIcon }[];
    routes: RouteObject[];      // relativ zu /<basePath>
  }
  ```
- Alle Frontend-Module werden statisch gebündelt. Zur Laufzeit lädt die Shell `GET /api/modules` und blendet nur aktive Module ein, sowohl in der Sidebar als auch bei den Routen.
- **Sidebar:** shadcn `Sidebar` mit `collapsible="icon"`. Oben steht die Kerngruppe („Übersicht“), dann eine Gruppe pro Modul. Unten liegt der Bereich „Einstellungen > Benutzer“, der nur für `ADMIN` sichtbar ist, und das Benutzermenü im Footer („Mein Konto“, „Abmelden“). Den Zustand des Einklappens speichert die Sidebar in einem Cookie. Auf Mobilgeräten öffnet sie sich als Sheet-Overlay.
- **AuthGate:** Es liest `stage` aus `GET /api/auth/me` und zeigt den passenden Schritt des Login-Ablaufs oder die Shell. Den Parameter `next` behält es über alle Schritte hinweg.
- **QR-Code:** Eine kleine Bibliothek (`qrcode.react`) rendert lokal ein SVG.
- **Routing:** React Router (Data Router) mit deutschen URLs (`/ferienvermietung/belegung`, `/konto`, `/einstellungen/benutzer`).
- **Server-State:** TanStack Query. Der API-Wrapper leitet bei 401 auf `/login?next=<pfad>` um.
- **Styling:** Tailwind v4 und shadcn/ui mit dem Preset `radix-nova` (Radix, lucide, Schrift Geist). Die aktuelle shadcn-CLI bietet den Stil `new-york` nicht mehr an. **Nur ein helles Theme.** Es gibt keine `dark`-Klasse, und die Systemeinstellung wird ignoriert. Die Farb-Tokens liegen in `index.css` als CSS-Variablen (shadcn-Konvention), sodass ein Dunkelmodus später nachgerüstet werden könnte, ohne die Komponenten anzufassen.

### D12: Container

```
          Reverse Proxy (vorhanden)
                    |
   ${LOGIS_BIND}:${LOGIS_PORT}
           +--------v--------+
           | frontend (nginx)|   /     -> SPA (try_files ... /index.html)
           |  Header, limit  |   /api/ -> http://backend:8080
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

- **Frontend-Image:** `node:24-alpine` baut, `nginx:1.28-alpine` liefert aus. Gegenüber der Planung sind das die jeweils aktuelle LTS- bzw. Stable-Linie. Ein Skript in `/docker-entrypoint.d/` erzeugt beim Start die `set_real_ip_from`-Zeilen aus `LOGIS_TRUSTED_PROXIES`. nginx blendet die Security-Header des Backends aus und setzt sie selbst, damit kein Header doppelt vorkommt.
- Die Dockerfiles kommen ohne BuildKit-Funktionen wie Cache-Mounts aus, damit sie auch mit einem klassischen Docker-Builder bauen.
- **Backend-Image:** `eclipse-temurin:21-jdk` baut, `eclipse-temurin:21-jre` startet die Anwendung als Nicht-Root-Benutzer.
- Alle Basis-Images sind Multi-Arch und ohne `platform:`-Angaben, der Build läuft nativ auf amd64 und arm64.
- `depends_on` mit `condition: service_healthy`. Nur `frontend` hat `ports:`.
- Healthchecks: `db` mit `pg_isready`, `backend` mit `:8081/actuator/health`, `frontend` mit `/api/health`.

### D13: Backups

- Ein Container `backup` basiert auf `postgres:17` mit passender `pg_dump`-Version. Er führt `ops/backup/backup.sh` als Schleife aus: `pg_dump -Fc` nach `/backups/logis_<YYYYMMDD_HHMMSS>.dump`, Löschen von Dateien älter als `BACKUP_RETENTION_DAYS` (Standard 14), täglich zur Uhrzeit `BACKUP_TIME` (Standard `03:00`).
- Bind-Mount `./backups` auf dem Host.
- Das README weist ausdrücklich darauf hin, dass **`LOGIS_ENCRYPTION_KEY` getrennt von den Backups** gesichert werden muss. Ohne ihn lassen sich die TOTP-Secrets nach einem Restore nicht entschlüsseln, und alle Benutzer müssten 2FA neu einrichten.
- Die Wiederherstellung (`pg_restore --clean --if-exists`) wird im README dokumentiert und von Hand getestet.

### D14: Entwicklungsumgebung

- `compose.dev.yml` startet nur `db` auf `localhost:5432`.
- Das Backend läuft im Profil `dev` mit Devtools. `application-dev.yml` enthält einen Dev-Admin, einen festen Dev-Verschlüsselungsschlüssel, `Secure`-Cookies sind ausgeschaltet (HTTP auf localhost), und das Lockout ist mit kurzen Zeiten aktiv.
- Das Frontend läuft mit `npm run dev`. Der Vite-Proxy leitet `/api` an `http://localhost:8080` weiter.

### D15: Tests

- **Backend:** JUnit 5. Dazu gehören der Modulith-Verify-Test und Integrationstests mit Testcontainers (Postgres 17) für den gesamten Login-Automaten, TOTP-Replay, Recovery-Codes, Lockout, Session-Timeouts (mit `Clock`-Injection), Benutzerverwaltung samt Admin-Invariante, Modulschaltung, Health, Fehlerantworten und den Initial-Admin-Runner.
- **Frontend:** Vitest und Testing Library für Registry-Filterung, die Schritte des AuthGate, die Sichtbarkeit von Admin-Einträgen und das Sidebar-Rendering.
- **Stack:** Eine Smoke-Checkliste im README prüft Port und Bind-Adresse, Header, 429 bei vielen Logins, `/api/health`, den geschlossenen DB-Port, `/actuator` und das Backup.

## Risks / Trade-offs

- [Eigener MFA-Automat statt einer Lösung des Frameworks, das ist sicherheitskritischer Eigenbau] → Er ist klein und zustandsbasiert, jeder Übergang hat einen Integrationstest, und es wird eine etablierte TOTP-Bibliothek genutzt. Beim Upgrade auf Spring Security 7 wird geprüft, ob er ersetzt werden kann.
- [Die Sessions liegen im Speicher, ein Neustart meldet alle ab] → Das ist akzeptabel. Bei Bedarf lässt sich Spring Session JDBC nachrüsten, das `SessionRegistry` muss dann mitwandern.
- [Ohne eine Passwort-vergessen-Funktion braucht man einen Admin] → Bei einem Betreiber und wenigen Benutzern ist das vertretbar. Für den Notfall des einzigen Admins beschreibt das README einen CLI-Weg (ein SQL-Statement setzt `password_change_required` und einen neuen Hash bzw. leert `totp_secret_enc`).
- [Geht `LOGIS_ENCRYPTION_KEY` verloren, ist 2FA für alle ungültig] → Das README fordert eine getrennte Sicherung. Die Wiederherstellung über den Admin-Reset bzw. den SQL-Notfallweg bleibt möglich.
- [Der Reverse Proxy ist falsch konfiguriert, z. B. kein `X-Forwarded-Proto` oder eine falsche IP] → `Secure`-Cookies oder das Rate-Limit greifen dann nicht korrekt. Die Smoke-Checkliste prüft Cookie-Attribute und die protokollierte Client-IP ausdrücklich.
- [`style-src 'unsafe-inline'` in der CSP] → Das ist für Radix-Inline-Styles nötig. Das Risiko ist gering, da `script-src` streng bleibt.
- [Ein gemeinsamer Flyway-Verlauf für alle Module] → Zeitstempel-Versionen mit Modulpräfix verhindern Kollisionen.
- [Spring Boot 3.x statt der aktuellen Hauptversion] → Das ist eine bewusste Vorgabe. Das Upgrade wird ein eigenes Change.
- [Backups liegen auf demselben Host] → Das schützt nicht gegen einen Hardwareausfall. Eine Kopie außerhalb des Hosts sollte als zeitnaher Folgeschritt kommen und steht als Hinweis im README.

## Migration Plan

Als Erstinstallation gibt es keine Migration von Bestandsdaten. Die Inbetriebnahme läuft so ab: `git clone`, `.env` aus `.env.example` anlegen (Schlüssel mit `openssl rand -base64 32` erzeugen), `docker compose up -d --build`, dann im vorhandenen Reverse Proxy eine Route auf `LOGIS_BIND:LOGIS_PORT` mit TLS einrichten. Danach beim ersten Login als Admin 2FA einrichten. Für ein Rollback checkt man einen älteren Git-Stand aus und baut neu. Gehen Schemaänderungen schief, wird das letzte Backup eingespielt.

## Open Questions

- **Details des Freigabemodells (für `core-ledger`):** Die Grundsatzentscheidung ist gefallen: Freigaben pro Objekt mit `OWNER`/`EDITOR`/`VIEWER`, untergeordnete Daten erben, Einladung nur bestehender Benutzer (siehe proposal.md). Offen ist, wie Transaktionen auf einem Konto behandelt werden, die zugleich einem Objekt zugeordnet sind. Vorschlag: Schreiben nur mit Rechten auf dem Konto, Lesen mit Rechten auf dem Konto oder dem Objekt. Ebenfalls offen ist, welche minimalen Benutzerdaten (Anzeigename, Benutzername) Nicht-Admins für die Auswahl beim Einladen sehen dürfen. Beides ändert das Scaffold nicht.
