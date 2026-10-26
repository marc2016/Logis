# Tasks

## 1. Repo-Grundlage

- [x] 1.1 `.gitignore` (Java, Node, `.env`, `backups/`), `.editorconfig` und `.env.example` mit allen Einstellungen aus design.md D5, D6, D9, D12 und D13 anlegen. Prüfen: `git status` zeigt `.env` und `backups/` nicht an, und `.env.example` enthält keine echten Secrets.
- [x] 1.2 `compose.dev.yml` mit Postgres 17 auf `localhost:5432` und benanntem Volume anlegen. Prüfen: `docker compose -f compose.dev.yml up -d` startet, und `pg_isready` meldet „accepting connections“.

## 2. Backend-Gerüst

- [x] 2.1 Spring-Boot-3-Projekt in `backend/` anlegen: Maven Wrapper, Java 21, `groupId app.logis`, Abhängigkeiten Web, Security, Data JPA, Validation, Actuator, Flyway, PostgreSQL, Spring Modulith, `dev.samstevens.totp`, Testcontainers. Prüfen: `./mvnw verify` baut erfolgreich.
- [x] 2.2 Package-Struktur nach D2 (`core.auth`, `core.users`, `core.modules`, `core.web`, `shared`, `modules.vacationrental`) anlegen und einen Modulith-Verify-Test schreiben. Prüfen: Der Test ist grün und schlägt fehl, sobald testweise ein Zugriff von `core` auf `modules` eingebaut wird.
- [x] 2.3 `application.yml` (Datasource aus der Umgebung, `ddl-auto=validate`, Flyway-Locations nach D4, Management-Port 8081 mit nur `health`, `server.error.include-*=never`, `forward-headers-strategy=native`, Session-Timeout 30 min) und `application-dev.yml` nach D14 anlegen. Prüfen: Das Backend startet im Profil `dev` gegen `compose.dev.yml`.
- [x] 2.4 Flyway-Migrationen für die Schemas `core` und `vacationrental` sowie `core.users` und `core.recovery_codes` nach D5 anlegen. Prüfen: Nach dem Start existieren Schemas und Tabellen mit Unique-Index auf `lower(username)`.
- [x] 2.5 Globale Fehlerbehandlung (`@RestControllerAdvice` mit `ProblemDetail` ohne Details) umsetzen. Prüfen: Ein Integrationstest bestätigt, dass ein absichtlich geworfener Fehler 500 liefert, ohne Stacktrace, Exception-Namen oder Versionsangaben.

## 3. Modulsystem (Backend)

- [x] 3.1 `ModuleDescriptor` und `GET /api/modules` in `core.modules` implementieren. Prüfen: Ein Integrationstest liefert die registrierten Descriptoren.
- [x] 3.2 `VacationRentalModuleConfiguration` mit `@ConditionalOnProperty` (D3), Descriptor (`vacationrental`, „Ferienvermietung“) und `GET /api/vacationrental/status` anlegen. Prüfen: Integrationstests decken die Szenarien „aktiv: Liste enthält Modul und Status liefert 200“ sowie „deaktiviert: Modul fehlt in der Liste und Status liefert 404“ ab.
- [x] 3.3 Integrationstest „Daten bleiben erhalten“: einen Datensatz in das Schema `vacationrental` schreiben (Testtabelle aus einer Testmigration), mit deaktiviertem Modul starten, dann mit aktiviertem Modul starten. Prüfen: Der Datensatz ist unverändert vorhanden.

## 4. Benutzer und Grundsicherheit (Backend)

- [x] 4.1 `User`-Entity mit Repository, `PasswordEncoder` (delegating, bcrypt) und zentraler Passwortvalidierung (mindestens 12 Zeichen) anlegen. Prüfen: Unit-Tests zeigen, dass der Hash nicht dem Klartext entspricht, dass `matches` erfolgreich ist und dass 11 Zeichen abgelehnt werden.
- [x] 4.2 AES-GCM-Verschlüsselung in `shared` mit dem Schlüssel aus `LOGIS_ENCRYPTION_KEY` umsetzen. Fehlt der Schlüssel oder ist er ungültig, scheitert der Start. Prüfen: Ein Unit-Test bestätigt den Roundtrip und dass manipulierter Ciphertext einen Fehler auslöst. Ein Integrationstest bestätigt, dass der Start ohne Schlüssel mit einer Meldung abbricht.
- [x] 4.3 Initial-Admin-Runner nach D5 umsetzen. Prüfen: Integrationstests decken vier Fälle ab: leere DB mit gültiger Konfiguration legt den Admin mit Rolle `ADMIN` und ohne TOTP an, fehlende Konfiguration lässt den Start scheitern, ein Passwort mit 8 Zeichen lässt den Start scheitern, und ein bestehender Benutzer bleibt unverändert.
- [x] 4.4 Security-Grundkonfiguration umsetzen: 401-EntryPoint, `CookieCsrfTokenRepository`, öffentliche Pfade (`/api/auth/**`, `/api/health`), fachliche Pfade verlangen `ROLE_USER`/`ROLE_ADMIN`, `/api/admin/**` verlangt `ROLE_ADMIN`, `SessionRegistry`. Prüfen: Integrationstests bestätigen, dass `GET /api/modules` ohne Sitzung 401 liefert und ein POST ohne CSRF-Token 403.

## 5. Mehrstufige Anmeldung (Backend)

- [x] 5.1 Login-Automat nach D6 umsetzen: `POST /api/auth/login`, `GET /api/auth/me` mit `stage`, Stage-Authorities, neue Session-ID bei jedem Übergang. Prüfen: Integrationstests decken ab, dass nach dem Passwort-Schritt `stage=TOTP_REQUIRED` bzw. `TOTP_ENROLLMENT_REQUIRED` gilt und `GET /api/modules` 401 liefert. Falsches Passwort, unbekannter und deaktivierter Benutzer führen zur gleichen Fehlermeldung.
- [x] 5.2 TOTP-Einrichtung umsetzen: `POST /api/auth/totp/setup` (Secret und otpauth-URI, verschlüsselt in der Session bzw. vorläufig gespeichert) und `POST /api/auth/totp/confirm` (Code prüfen, Secret speichern, 10 Wiederherstellungscodes erzeugen und einmalig zurückgeben). Prüfen: Integrationstests bestätigen, dass ein gültiger Code die Einrichtung abschließt und 10 Codes liefert, dass ein falscher Code nichts speichert und dass `totp_secret_enc` in der DB nicht dem Klartext-Secret entspricht.
- [x] 5.3 TOTP-Prüfung umsetzen: `POST /api/auth/totp` mit TOTP-Code (±1 Schritt, Schutz vor Wiederverwendung über `totp_last_step`) oder Wiederherstellungscode (einmalig). Prüfen: Integrationstests mit fester `Clock` zeigen, dass ein gültiger Code anmeldet, ein wiederverwendeter Code abgelehnt wird, ein Wiederherstellungscode einmal funktioniert und beim zweiten Mal abgelehnt wird.
- [x] 5.4 Stage `PASSWORD_CHANGE_REQUIRED` und `POST /api/auth/password` umsetzen. Prüfen: Ein Integrationstest bestätigt, dass ein Benutzer mit `password_change_required` nach dem TOTP-Schritt erst nach der Passwortänderung `AUTHENTICATED` ist.
- [x] 5.5 Lockout nach D7 umsetzen: Zähler über Passwort- und Code-Schritt, wachsende Sperre bis 15 min, Dummy-Hash für unbekannte Benutzer, Logzeile `auth.failed`. Prüfen: Integrationstests mit fester `Clock` zeigen, dass nach 5 Fehlschlägen auch korrekte Daten mit „Zu viele Fehlversuche“ abgelehnt werden, dass nach Ablauf der Sperre die Anmeldung weiterläuft, dass ein Erfolg den Zähler zurücksetzt und dass das Log weder Passwort noch Code enthält.
- [x] 5.6 Sitzungsdauer umsetzen: Inaktivität 30 min und Filter für das absolute Limit von 12 h. Prüfen: Integrationstests mit manipulierter Zeit bzw. fester `Clock` bestätigen 401 nach Inaktivität und 401 nach 12 h trotz Aktivität.
- [x] 5.7 `POST /api/auth/logout` umsetzen. Prüfen: Ein Integrationstest bestätigt, dass das alte Cookie danach 401 erhält.

## 6. Benutzerverwaltung und Mein Konto (Backend)

- [x] 6.1 `GET/POST /api/admin/users` und `PATCH /api/admin/users/{id}` nach D8 umsetzen. Prüfen: Integrationstests bestätigen: Ein `USER` erhält 403. Das Anlegen ergibt einen aktiven Benutzer mit `password_change_required` und ausstehendem TOTP. „Anna“ wird bei vorhandener „anna“ abgelehnt. Die Antwort enthält keine Hashes oder Secrets.
- [x] 6.2 Deaktivieren und Reaktivieren mit sofortigem Beenden der Sitzungen über `SessionRegistry` umsetzen. Prüfen: Ein Integrationstest bestätigt, dass die laufende Sitzung des Benutzers nach dem Deaktivieren 401 erhält, dass er sich nicht anmelden kann und dass er sich nach dem Reaktivieren wieder anmelden kann.
- [x] 6.3 `reset-password` und `reset-totp` umsetzen. Prüfen: Integrationstests bestätigen, dass die Sitzungen enden, dass nach einem Passwort-Reset ein Passwortwechsel erzwungen wird und dass nach einem TOTP-Reset die Neueinrichtung erzwungen wird und alte Wiederherstellungscodes ungültig sind.
- [x] 6.4 Admin-Invarianten umsetzen: letzten aktiven Admin nicht herabstufen oder deaktivieren, sich nicht selbst deaktivieren, mit `FOR UPDATE`. Prüfen: Integrationstests decken beide Ablehnungen samt Meldung ab.
- [x] 6.5 `POST /api/account/password` (andere Sitzungen beenden) und `POST /api/account/recovery-codes` umsetzen. Prüfen: Integrationstests bestätigen, dass ein falsches aktuelles Passwort abgelehnt wird, dass nach einer Änderung die zweite Sitzung 401 erhält und dass nach dem Neuerzeugen alte Codes ungültig sind.

## 7. Health (Backend)

- [x] 7.1 `GET /api/health` mit DB-Prüfung nach D10 umsetzen. Prüfen: Ein Integrationstest liefert bei laufender DB 200 mit `{"status":"UP"}` ohne weitere Felder und bei gestopptem Testcontainer 503.

## 8. Frontend-Gerüst

- [x] 8.1 Vite-, React- und TypeScript-Projekt in `frontend/` mit Tailwind v4, shadcn/ui (`new-york`, lucide), React Router, TanStack Query, `qrcode.react`, Vitest und Testing Library anlegen. Den Vite-Proxy `/api` auf `localhost:8080` setzen. Prüfen: `npm run build` und `npm test` laufen erfolgreich.
- [x] 8.2 shadcn-Komponenten `sidebar`, `button`, `input`, `input-otp`, `label`, `card`, `dialog`, `table`, `badge`, `select`, `sonner`, `separator`, `tooltip`, `sheet`, `dropdown-menu` und `alert` hinzufügen. Prüfen: Die Dateien liegen unter `src/components/ui/`, und der Build ist grün.
- [x] 8.3 `lib/api.ts` umsetzen: `credentials: "include"`, `X-XSRF-TOKEN` aus dem Cookie, bei 401 Umleitung auf `/login?next=…`, Fehler im Format `ProblemDetail` als deutsche Meldung. Prüfen: Ein Unit-Test bestätigt, dass der Header gesetzt wird und bei 401 umgeleitet wird.

## 9. App-Shell und Modul-Registry (Frontend)

- [x] 9.1 Den Typ `LogisModule` und `app/registry.ts` nach D11 anlegen. Ein Hook `useActiveModules` filtert die Registry anhand von `GET /api/modules`. Prüfen: Ein Unit-Test zeigt, dass nur Module aus der API-Antwort zurückgegeben werden.
- [x] 9.2 `AppShell` mit `AppSidebar` bauen: Kerngruppe, eine Gruppe pro aktivem Modul, „Einstellungen > Benutzer“ nur für `ADMIN`, Benutzermenü mit „Mein Konto“ und „Abmelden“, `collapsible="icon"`, Hervorhebung des aktiven Eintrags. Prüfen: Komponententests bestätigen, dass ein aktives Modul als Gruppe erscheint, ein deaktiviertes fehlt und ein `USER` keinen Eintrag „Benutzer“ sieht.
- [x] 9.3 Router aufbauen: Kern-Routen, Modul-Routen aus aktiven Modulen unter `/<basePath>`, Admin-Routen geschützt, `NotFound` innerhalb der Shell. Prüfen: Komponententests bestätigen, dass die URL eines deaktivierten Moduls `NotFound` anzeigt und dass `/einstellungen/benutzer` für einen `USER` ebenfalls `NotFound` anzeigt.
- [x] 9.4 Platzhalterseiten anlegen: „Übersicht“ (`/`) und das Modul `vacationrental` mit `basePath` „ferienvermietung“ und Eintrag „Belegung“. Prüfen: Manuell im Dev-Modus öffnen. Ein Klick wechselt die Seite ohne Neuladen, die URL ändert sich, und der Eintrag ist hervorgehoben.
- [x] 9.5 Helles Theme festlegen: shadcn-Farb-Tokens in `index.css` nur für das helle Theme, keine `dark`-Varianten, `color-scheme: light`. Prüfen: Manuell mit Dunkelmodus in macOS bzw. emuliertem `prefers-color-scheme: dark` in den DevTools. Die Oberfläche bleibt hell, auch Formularfelder und Scrollbars.
- [x] 9.6 Responsives Verhalten prüfen: Bei Smartphone-Breite öffnet sich die Sidebar als Overlay, und der Zustand des Einklappens bleibt nach einem Reload erhalten. Prüfen: Manuell in den DevTools mit 390 px Breite und mit Reload.

## 10. Anmeldung, Konto und Benutzerverwaltung (Frontend)

- [x] 10.1 `AuthGate` und `LoginFlow` umsetzen: Schritt Passwort, dann je nach `stage` TOTP-Eingabe (`input-otp`, Link „Wiederherstellungscode verwenden“) oder Einrichtung (QR-Code lokal, Secret als Text, Bestätigungscode), Anzeige der Wiederherstellungscodes mit „Ich habe die Codes gesichert“ und gegebenenfalls Passwortwechsel. `next` bleibt über alle Schritte erhalten. Prüfen: Komponententests mit gemockter API decken die Abläufe ab: Deep Link, Passwort, TOTP, Zielseite. Bei einem Erst-Login gelten Passwort, Einrichtung, Codes, Übersicht. Die Fehlermeldungen „Benutzername oder Passwort ist falsch“, „Code ist ungültig“ und „Zu viele Fehlversuche…“ erscheinen.
- [x] 10.2 Seite „Mein Konto“ (`/konto`) umsetzen: Passwort ändern und Wiederherstellungscodes neu erzeugen, mit Bestätigungsdialog und Anzeige der neuen Codes. Prüfen: Komponententests decken Erfolg und die Meldung bei falschem aktuellem Passwort ab.
- [x] 10.3 Benutzerverwaltung (`/einstellungen/benutzer`) umsetzen: eine Tabelle mit Status-Badges, die Dialoge „Benutzer anlegen“ und „Bearbeiten“ sowie die Aktionen Deaktivieren/Reaktivieren, „Passwort zurücksetzen“ und „2FA zurücksetzen“ mit Bestätigung. Fehlermeldungen der API werden angezeigt. Prüfen: Komponententests decken Anlegen, die Meldung bei doppeltem Namen und die Meldung „Es muss mindestens ein aktiver Admin bestehen bleiben“ ab.
- [x] 10.4 Abmelden umsetzen: `POST /api/auth/logout`, Query-Cache leeren, zum Login weiterleiten. Prüfen: Manuell im Dev-Modus. Nach dem Abmelden zeigt ein Reload die Anmeldung.

## 11. Container und Betrieb

- [x] 11.1 `backend/Dockerfile` als Multi-Stage-Build (temurin 21 jdk zu jre, Nicht-Root-Benutzer, ohne `platform`) anlegen. Prüfen: `docker build` gelingt auf dem Mac (arm64).
- [x] 11.2 `frontend/Dockerfile` und `frontend/nginx.conf` nach D9 und D12 anlegen: SPA-Fallback, `/api/` auf `backend:8080` mit `X-Forwarded-*`, `set_real_ip_from` aus der Umgebung (per `envsubst`-Template), `limit_req` auf `/api/auth/`, Security-Header `always`, `server_tokens off`, kein `/actuator`. Prüfen: `docker build` gelingt, und `nginx -t` im Container ist erfolgreich.
- [x] 11.3 `compose.yml` nach D12 anlegen: Dienste `frontend`, `backend`, `db` und `backup`, internes Netz, nur `frontend` mit Port `${LOGIS_BIND:-127.0.0.1}:${LOGIS_PORT:-8080}`, Healthchecks und `depends_on: service_healthy`, Volume `pgdata`, alle Einstellungen aus `.env`. Prüfen: `docker compose up -d --build` startet alle Dienste mit dem Status `healthy`.
- [x] 11.4 `ops/backup/backup.sh` nach D13 anlegen und einbinden. Prüfen: Mit testweise `BACKUP_TIME` in einer Minute entsteht eine Datei `backups/logis_<zeitstempel>.dump`, und eine Testdatei mit `touch -d` älter als die Aufbewahrungsdauer wird beim nächsten Lauf gelöscht.

## 12. Dokumentation und Abnahme

- [x] 12.1 `README.md` schreiben: Überblick, Voraussetzungen, Inbetriebnahme, Anbindung an den Reverse Proxy (Bind-Adresse, `LOGIS_TRUSTED_PROXIES`, erforderliche Header, Beispiele für Traefik/Caddy/NPM), Schlüssel erzeugen und **getrennt sichern**, lokale Entwicklung, Module schalten, Backup und Restore, SQL-Notfallweg für den letzten Admin, Hinweis auf eine externe Backup-Kopie. Prüfen: Jeder Befehl im README wurde mindestens einmal ausgeführt.
- [x] 12.2 Smoke-Test des Stacks lokal: Oberfläche nur auf der Bind-Adresse erreichbar, Deep Link nach Reload ohne nginx-404, alle Security-Header vorhanden (`curl -I`), keine Serverversion im Header, `/api/health` liefert 200, `/actuator/health` liefert keine Betriebsdaten, `nc -z localhost 5432` schlägt fehl, mehr als 15 schnelle Login-Anfragen ergeben 429, kompletter Erst-Login mit 2FA-Einrichtung, und `LOGIS_MODULES_VACATIONRENTAL_ENABLED=false` lässt die Gruppe nach einem Neustart verschwinden. Prüfen: Alle Punkte sind abgehakt.
- [x] 12.3 Restore-Test: Backup in eine leere Datenbank einspielen, Stack mit demselben `LOGIS_ENCRYPTION_KEY` starten. Prüfen: Die Anmeldung mit bestehendem Admin und bestehender 2FA funktioniert.
