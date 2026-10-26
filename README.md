# Logis

Selbst gehostete Plattform für Finanzen, Immobilien, Ferienvermietung und Beteiligungen. Sie dient der Übersicht und der Steuer und ersetzt keine doppelte Buchführung.

Logis ist ein **modularer Monolith**. Ein Kern mit Anmeldung, Benutzern und Modulregistry nimmt Fachmodule auf, die sich per Konfiguration an- und abschalten lassen. Die Planung liegt unter [`openspec/`](openspec/).

```
Internet --TLS--> Reverse Proxy (vorhanden)
                       |
          LOGIS_BIND:LOGIS_PORT
                       |
              frontend (nginx)      React-App, Security-Header, Rate-Limit
                       | /api/
              backend (Spring Boot) nur intern
                       |
              db (Postgres 17)      nur intern  <--  backup (taeglich pg_dump -> ./backups)
```

| Bereich | Technik |
|---|---|
| Backend | Java 21, Spring Boot 3.5, Spring Modulith, Spring Security, JPA, Flyway |
| Frontend | React 19, TypeScript, Vite, Tailwind CSS 4, shadcn/ui, TanStack Query, React Router |
| Datenbank | PostgreSQL 17, ein Schema pro Modul (`core`, `vacationrental`, …) |
| Betrieb | Docker Compose: `frontend`, `backend`, `db`, `backup` |

## Voraussetzungen

- Docker mit Docker Compose v2. Die Images bauen nativ auf **amd64** (Mini-PC) und **arm64** (Mac).
- Ein vorhandener Reverse Proxy mit TLS, wenn Logis aus dem Internet erreichbar sein soll.
- Für die Entwicklung: JDK 21 oder neuer und Node.js 22 oder neuer.

## Inbetriebnahme (Mini-PC)

```sh
git clone <repo-url> logis && cd logis
cp .env.example .env
openssl rand -base64 32          # Ergebnis als LOGIS_ENCRYPTION_KEY in .env eintragen
$EDITOR .env                     # Passwoerter, Admin, Port, vertrauenswuerdige Proxys
./start.sh prod                  # prueft .env und startet: docker compose up -d --build
docker compose ps                # alle Dienste "healthy"
```

Danach richtest du im Reverse Proxy eine HTTPS-Route auf `LOGIS_BIND:LOGIS_PORT` ein (siehe unten). Öffne dann die Domain und melde dich mit `LOGIS_ADMIN_USERNAME` und `LOGIS_ADMIN_PASSWORD` an. Beim ersten Login richtest du die Zwei-Faktor-Anmeldung ein, z. B. mit Aegis, 2FAS oder Google Authenticator, und sicherst die 10 Wiederherstellungscodes. Der initiale Admin wird nur angelegt, solange noch kein Benutzer existiert.

**Update:** `git pull && docker compose up -d --build`. Die Daten bleiben im Volume `pgdata` erhalten.

### Wichtige Einstellungen (`.env`)

| Variable | Bedeutung |
|---|---|
| `LOGIS_BIND`, `LOGIS_PORT` | Adresse und Port des einzigen veröffentlichten Ports. Standard `127.0.0.1:8080`, also nur für einen Proxy auf demselben Host erreichbar. |
| `LOGIS_TRUSTED_PROXIES` | Adressen oder Netze, deren `X-Forwarded-For` als echte Client-IP gilt, komma-getrennt |
| `LOGIS_ENCRYPTION_KEY` | Schlüssel für die TOTP-Secrets, 32 Byte in Base64. **Getrennt von den Backups sichern!** |
| `LOGIS_ADMIN_USERNAME`, `LOGIS_ADMIN_PASSWORD` | Initialer Admin, nur beim allerersten Start verwendet. Mindestens 12 Zeichen. |
| `LOGIS_MODULES_<MODUL>_ENABLED` | Modul an- oder abschalten, z. B. `LOGIS_MODULES_VACATIONRENTAL_ENABLED=false` |
| `BACKUP_TIME`, `BACKUP_RETENTION_DAYS` | Uhrzeit der täglichen Sicherung und Aufbewahrung in Tagen |

> **Schlüssel verloren?** Ohne `LOGIS_ENCRYPTION_KEY` lassen sich die gespeicherten TOTP-Secrets nicht mehr entschlüsseln. Alle Benutzer müssen ihre 2FA dann neu einrichten (siehe Notfall).

## Anbindung an den Reverse Proxy

Der Proxy terminiert TLS und leitet **alles** unter der Domain an Logis weiter. Er muss `X-Forwarded-For` und `X-Forwarded-Proto` setzen. Nur dann bekommt das Sitzungs-Cookie das Attribut `Secure`, und Rate-Limit und Protokoll sehen die echte Client-IP. Das tun die gängigen Proxys standardmäßig.

`LOGIS_TRUSTED_PROXIES` muss die Adresse enthalten, mit der der Proxy beim Container ankommt:

- **Proxy direkt auf dem Host** (Caddy oder nginx als Dienst) mit `LOGIS_BIND=127.0.0.1`: Docker leitet die Verbindung über das Bridge-Gateway weiter. Der Standardwert `172.16.0.0/12,127.0.0.1` deckt das ab.
- **Proxy auf einem anderen Rechner:** `LOGIS_BIND` auf die LAN-Adresse des Mini-PCs setzen und `LOGIS_TRUSTED_PROXIES` auf die IP des Proxys.

Beispiele:

```caddyfile
# Caddy (auf dem Mini-PC)
logis.example.com {
    reverse_proxy 127.0.0.1:8080
}
```

```yaml
# Traefik (File Provider, Traefik laeuft auf dem Host oder im Host-Netz)
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

**Nginx Proxy Manager:** Proxy Host für die Domain anlegen, Ziel `http`, `<IP des Mini-PCs>`, Port `LOGIS_PORT`. SSL über Let's Encrypt erzwingen. `LOGIS_BIND` und `LOGIS_TRUSTED_PROXIES` passend setzen.

**Prüfen:** Nach der Anmeldung über die Domain muss das Cookie `LOGIS_SESSION` in den Browser-DevTools die Attribute `Secure; HttpOnly; SameSite=Lax` tragen. Bei einem absichtlich falschen Passwort muss `docker compose logs backend | grep auth.failed` deine echte IP zeigen und nicht die des Proxys.

## Benutzer

- Nur Admins legen Benutzer an, unter *Einstellungen > Benutzer*. Eine Selbstregistrierung gibt es nicht.
- Neue Benutzer setzen beim ersten Login ein eigenes Passwort und richten die 2FA ein.
- Admins können Benutzer deaktivieren und Passwort oder 2FA zurücksetzen. Die Sitzungen der betroffenen Person enden dabei sofort.
- Unter *Mein Konto* ändert jeder sein Passwort und erzeugt neue Wiederherstellungscodes.

## Backup und Restore

Der Dienst `backup` schreibt täglich zur Uhrzeit `BACKUP_TIME` eine Sicherung nach `./backups/logis_<YYYYMMDD_HHMMSS>.dump` und löscht Sicherungen, die älter als `BACKUP_RETENTION_DAYS` sind.

```sh
docker compose exec backup backup.sh --now      # sofort sichern
ls -l backups/
```

> Die Backups liegen auf demselben Rechner wie die Datenbank. Kopiere `./backups` zusätzlich regelmäßig auf einen anderen Rechner, ein NAS oder einen Cloud-Speicher, und sichere `.env` mit dem Schlüssel getrennt davon.

**Wiederherstellung** in eine leere Datenbank:

```sh
docker compose down -v                           # ACHTUNG: loescht die aktuelle Datenbank
docker compose up -d --wait db
docker compose exec -T db sh -c 'pg_restore --clean --if-exists --no-owner -U "$POSTGRES_USER" -d "$POSTGRES_DB"' \
  < backups/logis_YYYYMMDD_HHMMSS.dump
docker compose up -d
```

Die `.env` muss dabei denselben `LOGIS_ENCRYPTION_KEY` enthalten wie beim Erstellen der Sicherung.

## Notfall: Admin ausgesperrt

Führe die folgenden Befehle auf dem Mini-PC im Logis-Verzeichnis aus. Danach endet jede Sitzung des Benutzers.

**2FA verloren** (Telefon weg und keine Wiederherstellungscodes mehr): Die 2FA wird zurückgesetzt und beim nächsten Login neu eingerichtet.

```sh
docker compose exec db sh -c 'psql -U "$POSTGRES_USER" -d "$POSTGRES_DB" -c "
  DELETE FROM core.recovery_codes WHERE user_id = (SELECT id FROM core.users WHERE username = '"'"'admin'"'"');
  UPDATE core.users SET totp_secret_enc = NULL, totp_last_step = NULL, failed_attempts = 0,
         locked_until = NULL, session_version = session_version + 1 WHERE username = '"'"'admin'"'"';"'
```

**Passwort vergessen:** Erzeuge einen bcrypt-Hash, setze ihn als Startpasswort und erzwinge damit einen Passwortwechsel beim nächsten Login.

```sh
HASH=$(docker run --rm httpd:2.4-alpine htpasswd -nbBC 10 "" 'neues-start-passwort' | cut -d: -f2)
docker compose exec db psql -U logis -d logis -c \
  "UPDATE core.users SET password_hash = '{bcrypt}$HASH', password_change_required = true,
          failed_attempts = 0, locked_until = NULL, session_version = session_version + 1
   WHERE username = 'admin';"
```

Ersetze dabei `admin` durch den Benutzernamen. Wenn `POSTGRES_USER` oder `POSTGRES_DB` in der `.env` anders lauten, ersetze im zweiten Befehl auch `logis`.

## Entwicklung

Am schnellsten mit dem Startskript:

```sh
./start.sh          # Postgres + Backend (Profil dev) + Frontend; Strg+C beendet Backend und Frontend
./start.sh stop     # Entwicklungs-Datenbank anhalten
./start.sh prod     # Produktions-Stack (prueft .env auf Platzhalter, dann docker compose up -d --build)
```

Oder einzeln:

```sh
docker compose -f compose.dev.yml up -d                                # nur Postgres auf localhost:5432
cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev    # http://localhost:8080
cd frontend && npm install && npm run dev                              # http://localhost:5173
```

Im Profil `dev` gibt es den Admin `admin` mit dem Passwort `admin-dev-password` und einen festen Entwicklungsschlüssel. Vite leitet `/api` an das Backend weiter.

**Tests:**

```sh
cd backend && ./mvnw verify        # Unit-, Integrations- (Testcontainers) und Modulgrenzen-Tests
cd frontend && npm test            # Vitest + Testing Library
cd frontend && npm run build       # Typpruefung + Produktions-Build
```

Testcontainers braucht einen erreichbaren Docker-Daemon. Bei **Colima** sind zwei Variablen nötig:

```sh
export DOCKER_HOST=unix://$HOME/.colima/default/docker.sock
export TESTCONTAINERS_DOCKER_SOCKET_OVERRIDE=/var/run/docker.sock
```

### Ein neues Modul anlegen

1. Backend: Package `app.logis.modules.<id>` mit einer `package-info.java` (`@ApplicationModule(id = "<id>")`) anlegen. Jede Komponente bekommt `@ConditionalOnLogisModule("<id>")`. Das Modul liefert ein `ModuleDescriptor`-Bean, seine Migrationen liegen unter `db/migration/<id>`, und das Schema kommt zu `spring.flyway.schemas` hinzu.
2. Frontend: `src/modules/<id>/index.ts` exportiert ein `LogisModule` und wird in `src/app/registry.ts` eingetragen.
3. `ModularityTest` stellt sicher, dass das Modul nur die öffentliche API des Kerns nutzt.

## Sicherheit im Überblick

- Anmeldung mit Passwort und verpflichtendem TOTP, dazu 10 einmalige Wiederherstellungscodes. Die TOTP-Secrets sind mit AES-256-GCM verschlüsselt.
- Nach 5 Fehlversuchen wird der Benutzer gesperrt, die Sperrdauer wächst bis 15 Minuten. Zusätzlich gilt ein Rate-Limit pro IP auf die Anmeldeschritte.
- Sitzungen enden nach 30 Minuten Inaktivität und spätestens nach 12 Stunden. Das Cookie ist `HttpOnly`, `SameSite=Lax` und hinter HTTPS `Secure`. Es gibt CSRF-Schutz.
- Die Antworten tragen die Header HSTS, CSP, `X-Frame-Options`, `nosniff` und `Referrer-Policy`. Fehlerantworten enthalten keine internen Details.
- Backend und Datenbank sind nur im internen Docker-Netz erreichbar. `/actuator` ist von außen nicht erreichbar.
