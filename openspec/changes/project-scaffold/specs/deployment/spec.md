# Spec Delta

## Purpose

Logis läuft selbst gehostet als Container-Stack auf einem Mini-PC und ist über einen vorhandenen Reverse Proxy mit TLS aus dem Internet erreichbar. Diese Capability legt das beobachtbare Betriebsverhalten fest: Erreichbarkeit, Isolation interner Dienste, Härtung, Gesundheitsprüfung, Konfiguration und Datensicherung.

## ADDED Requirements

### Requirement: Einziger Einstiegspunkt für den Reverse Proxy
Der Stack MUST genau einen HTTP-Port auf dem Host veröffentlichen. Über ihn SHALL sowohl die Weboberfläche als auch die API unter `/api/` erreichbar sein. Portnummer und Bind-Adresse MUST konfigurierbar sein, damit der Port nur für den Reverse Proxy erreichbar gemacht werden kann.

#### Scenario: Oberfläche und API über denselben Port
- **WHEN** der Stack gestartet ist und der Reverse Proxy den konfigurierten Port aufruft
- **THEN** wird die Weboberfläche ausgeliefert, und Aufrufe unter `/api/` erreichen das Backend

#### Scenario: Deep Link nach Neuladen
- **WHEN** der Browser eine Unterseite der Oberfläche (z. B. `/ferienvermietung/belegung`) direkt aufruft
- **THEN** wird die Oberfläche ausgeliefert und kein HTTP 404 vom Webserver

#### Scenario: Bind-Adresse
- **WHEN** die Bind-Adresse auf `127.0.0.1` gesetzt ist
- **THEN** ist der Port von anderen Rechnern im Netz aus nicht erreichbar

### Requirement: Betrieb hinter einem Reverse Proxy
Das System MUST die Angaben `X-Forwarded-For` und `X-Forwarded-Proto` nur von konfigurierten vertrauenswürdigen Proxy-Adressen übernehmen. Die übernommene Client-IP MUST für das Rate-Limit und die Protokollierung von Anmeldungen verwendet werden. Sitzungs- und CSRF-Cookies MUST mit dem Attribut `Secure` gesetzt werden, sofern die Anfrage laut Proxy über HTTPS kam.

#### Scenario: Client-IP hinter dem Proxy
- **WHEN** eine fehlgeschlagene Anmeldung über den vertrauenswürdigen Proxy mit `X-Forwarded-For: 203.0.113.7` eintrifft
- **THEN** protokolliert das System die Client-IP `203.0.113.7`

#### Scenario: Gefälschter Header
- **WHEN** eine Anfrage von einer nicht vertrauenswürdigen Adresse den Header `X-Forwarded-For: 10.0.0.1` mitsendet
- **THEN** verwendet das System die tatsächliche Absenderadresse und nicht `10.0.0.1`

#### Scenario: Sichere Cookies
- **WHEN** die Anmeldung über HTTPS am Reverse Proxy erfolgt
- **THEN** trägt das Sitzungs-Cookie die Attribute `Secure`, `HttpOnly` und `SameSite=Lax`

### Requirement: Security-Header
Alle Antworten des Einstiegspunkts MUST die Header `Strict-Transport-Security`, `Content-Security-Policy` (ohne Inline-Skripte und mit Einbettung nur durch die eigene Origin), `X-Content-Type-Options: nosniff`, `X-Frame-Options: DENY` und `Referrer-Policy: same-origin` enthalten.

#### Scenario: Header vorhanden
- **WHEN** ein Client die Startseite oder einen API-Endpunkt abruft
- **THEN** enthält die Antwort alle genannten Header

### Requirement: Keine internen Details nach außen
Fehlerantworten MUST NOT Stacktraces, Framework- oder Serverversionen oder interne Hostnamen enthalten. Die Server-Kennung des Webservers MUST NOT eine Version nennen.

#### Scenario: Unerwarteter Fehler
- **WHEN** bei einer API-Anfrage ein unerwarteter Fehler auftritt
- **THEN** antwortet das System mit HTTP 500 und einer allgemeinen Meldung ohne technische Details

### Requirement: Interne Dienste nicht erreichbar
Backend und Datenbank MUST NOT über einen Host-Port erreichbar sein. Betriebsendpunkte des Backends (Metriken, Health-Details) MUST NOT über den Einstiegspunkt erreichbar sein.

#### Scenario: Datenbankport
- **WHEN** der Stack läuft und ein Client vom Host aus den Postgres-Port anspricht
- **THEN** ist keine Verbindung möglich

#### Scenario: Betriebsendpunkte
- **WHEN** ein Client über den Einstiegspunkt einen Pfad unter `/actuator/` aufruft
- **THEN** werden keine Betriebsdaten des Backends ausgeliefert

### Requirement: Health-Endpunkt
Das System SHALL unter `/api/health` ohne Anmeldung einen Health-Status liefern. Er MUST HTTP 200 melden, wenn das Backend die Datenbank erreicht, und andernfalls HTTP 503. Die Antwort MUST NOT interne Details wie Hostnamen oder Versionen enthalten.

#### Scenario: Gesund
- **WHEN** Backend und Datenbank laufen und `/api/health` aufgerufen wird
- **THEN** antwortet das System mit HTTP 200

#### Scenario: Datenbank nicht erreichbar
- **WHEN** die Datenbank nicht erreichbar ist und `/api/health` aufgerufen wird
- **THEN** antwortet das System mit HTTP 503

### Requirement: Geordneter Start
Das Backend MUST erst Anfragen annehmen, wenn die Datenbank bereit ist und alle Migrationen angewendet wurden. Schlägt eine Migration fehl, dann MUST das Backend nicht starten.

#### Scenario: Kaltstart
- **WHEN** der gesamte Stack auf einem frischen Host gestartet wird
- **THEN** ist die Anwendung nach dem Start ohne manuelle Eingriffe nutzbar und das Datenbankschema vollständig angelegt

### Requirement: Konfiguration über Umgebung
Alle Secrets (Datenbankpasswort, initiales Admin-Passwort, Schlüssel zur Verschlüsselung der TOTP-Secrets) und betriebsspezifischen Einstellungen (Port, Bind-Adresse, vertrauenswürdige Proxys, aktivierte Module, Backup-Aufbewahrung) MUST über Umgebungsvariablen bzw. eine `.env`-Datei gesetzt werden. Container-Images MUST NOT Secrets enthalten. Fehlt der Verschlüsselungsschlüssel, dann MUST der Start abbrechen.

#### Scenario: Vorlage
- **WHEN** ein Betreiber das Repository auscheckt
- **THEN** findet er eine `.env.example` mit allen benötigten Einstellungen und ohne echte Secrets

#### Scenario: Fehlender Schlüssel
- **WHEN** das Backend ohne Verschlüsselungsschlüssel startet
- **THEN** bricht der Start mit einer Meldung ab, die die fehlende Einstellung nennt

### Requirement: Persistenz über Neustarts
Daten MUST einen Neustart und ein Neuerstellen der Container überstehen.

#### Scenario: Update
- **WHEN** die Images neu gebaut und die Container neu erstellt werden
- **THEN** sind alle zuvor gespeicherten Daten vorhanden

### Requirement: Automatische Datenbank-Backups
Der Stack SHALL täglich automatisch eine vollständige Sicherung der Datenbank in ein Verzeichnis auf dem Host schreiben. Sicherungen, die älter als die konfigurierte Aufbewahrungsdauer sind, MUST automatisch gelöscht werden. Eine Sicherung MUST sich in eine leere Datenbank zurückspielen lassen. Zusammen mit dem Verschlüsselungsschlüssel MUST danach eine Anmeldung mit bestehender Zwei-Faktor-Anmeldung möglich sein.

#### Scenario: Tägliche Sicherung
- **WHEN** der Stack länger als einen Tag läuft
- **THEN** liegt im Backup-Verzeichnis auf dem Host eine Sicherung mit Zeitstempel im Dateinamen

#### Scenario: Aufbewahrung
- **WHEN** die Aufbewahrungsdauer auf 14 Tage gesetzt ist und eine Sicherung älter als 14 Tage ist
- **THEN** wird diese Sicherung beim nächsten Backup-Lauf gelöscht

#### Scenario: Wiederherstellung
- **WHEN** eine Sicherung nach der dokumentierten Anleitung in eine leere Datenbank eingespielt wird und derselbe Verschlüsselungsschlüssel konfiguriert ist
- **THEN** startet die Anwendung mit dem gesicherten Datenbestand, und Benutzer können sich mit ihrer bestehenden Zwei-Faktor-Anmeldung anmelden

### Requirement: Lauffähig auf amd64 und arm64
Die Container-Images MUST sich sowohl auf amd64 (Mini-PC) als auch auf arm64 (Entwicklungsrechner) bauen und ausführen lassen, ohne dass die Build-Dateien geändert werden.

#### Scenario: Build auf dem Mini-PC
- **WHEN** auf dem amd64-Mini-PC der Stack gebaut und gestartet wird
- **THEN** laufen alle Container ohne Emulation
