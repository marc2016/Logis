# Proposal

## Why

Logis soll eine selbst gehostete Plattform für Übersicht und Steuer werden. Sie deckt die eigenen Finanzen, Immobilien, Ferienvermietung und Beteiligungen ab. Mehrere Benutzer greifen über das Internet darauf zu. Das Repo enthält bisher keinen Code. Bevor das erste Fachmodul (Ferienvermietung) entstehen kann, braucht es einen tragfähigen und abgesicherten Rahmen: einen modularen Monolithen mit festen Modulgrenzen, eine App-Shell, in die sich Module einklinken, eine Anmeldung mit mehreren Benutzern und Zwei-Faktor-Schutz sowie ein Docker-Setup, das auf dem Mini-PC (amd64) hinter einem vorhandenen Reverse Proxy läuft.

Leitentscheidungen aus der Erkundung, die über dieses Change hinaus gelten:

- Logis dient Übersicht und Steuer (Anlage V, EÜR, Auszüge für Gesellschafter) und ist **keine** doppelte Buchführung.
- Architektur: **modularer Monolith**, eine Anwendung mit hart getrennten Modulen, die per Konfiguration an- und abschaltbar sind. Ein Plugin-System zur Laufzeit gibt es nicht.
- Basis-Package `app.logis`.
- Frontend: React, TypeScript, Tailwind und shadcn/ui im **hellen Design**, mit einer linken, einklappbaren Sidebar, die eine Gruppe pro Modul enthält.
- Betrieb: drei Container (nginx mit React-Build, Spring Boot, Postgres) plus ein Backup-Container. Gebaut wird zunächst direkt auf dem Mini-PC.
- **Erreichbar aus dem Internet** über einen vorhandenen Reverse Proxy, der TLS übernimmt.
- **Mehrere Benutzer** mit Rollen. Zwei-Faktor-Anmeldung (TOTP) ist für alle Pflicht.
- **Freigaben pro Objekt:** Wer ein freigebbares Objekt anlegt (z. B. ein Konto oder eine Immobilie), ist dessen Owner. Er kann bestehende Benutzer lesend (`VIEWER`) oder schreibend (`EDITOR`) einladen. Untergeordnete Daten wie Transaktionen oder Buchungen erben die Rechte. Die Systemrolle `ADMIN` gibt keinen automatischen Zugriff auf fachliche Daten. Einladungen per Link oder E-Mail sind vorerst nicht vorgesehen. Das Rechtemodell entsteht mit `core-ledger`.

Roadmap, in der dieses Change der erste Schritt ist:

1. `project-scaffold` (dieses Change)
2. `core-ledger`: Rechtsträger, Konten, Kategorien, Transaktionen
3. `core-bank-import`: CSV/CAMT-Import, Duplikaterkennung
4. `realestate-minimal`: Objekte und Einheiten
5. `vacation-rental`: Buchungen (manuelle Erfassung, mehrere Kanäle), Belegungskalender

Später folgen KI-Inbox, Berichte, Beteiligungen, Privatfinanzen, Dauermiete und NK-Abrechnung.

## What Changes

- Neues Monorepo-Layout mit `backend/` (Spring Boot 3, Java 21, Maven), `frontend/` (Vite, React, TypeScript, Tailwind, shadcn/ui) sowie Compose-Dateien für Produktion und Entwicklung.
- Ein Backend-Gerüst als modularer Monolith: ein Kernbereich `app.logis.core` und Module unter `app.logis.modules.*`. Die Modulgrenzen werden durch einen automatisierten Test erzwungen.
- Mechanismus, um Module an- und abzuschalten. Das Backend liefert die Liste der aktiven Module an das Frontend.
- Ein leeres Platzhaltermodul `vacationrental`, das den Mechanismus von Anfang bis Ende nachweist und Ausgangspunkt für Change 5 ist.
- Datenbankmigrationen mit einem eigenen Postgres-Schema pro Modul.
- App-Shell im Frontend: eine Modul-Registry, eine linke Sidebar mit einer Gruppe pro aktivem Modul, im Icon-Modus einklappbar, dazu ein Übersicht-Platzhalter, „Mein Konto“ und eine Benutzerverwaltung für Admins.
- Anmeldung für mehrere Benutzer:
  - Login mit Passwort und **verpflichtendem TOTP**, das beim ersten Login eingerichtet wird, mit Wiederherstellungscodes.
  - Schutz vor Brute-Force (Rate-Limit und Sperrzeit nach Fehlversuchen).
  - Begrenzte Sitzungsdauer, CSRF-Schutz und sichere Cookies.
  - Der erste Admin wird beim ersten Start aus der Umgebung angelegt.
- Benutzerverwaltung durch Admins: Benutzer anlegen, deaktivieren, Passwort und 2FA zurücksetzen. Rollen `ADMIN` und `USER`. Eine Selbstregistrierung gibt es nicht.
- Docker-Setup für Produktion hinter einem Reverse Proxy:
  - nginx ist der einzige Einstiegspunkt und leitet `/api` an das Backend weiter. Backend und Datenbank sind nur intern erreichbar.
  - Security-Header, Healthchecks und ein täglicher Datenbank-Backup-Container.
- Docker-Setup für die Entwicklung, das nur Postgres startet. Backend und Vite laufen lokal mit Hot Reload.

## Capabilities

### New Capabilities

- `module-system`: Module lassen sich per Konfiguration aktivieren und deaktivieren. Das System macht die aktiven Module abrufbar, und deaktivierte Module sind nicht erreichbar.
- `app-shell`: Der Rahmen der Weboberfläche mit Sidebar-Navigation, gruppiert nach aktiven Modulen. Die Sidebar ist einklappbar und hat Routing für Modulseiten.
- `authentication`: Anmeldung mit Passwort und TOTP, Wiederherstellungscodes, Schutz vor Brute-Force, Sitzungsdauer, Abmeldung, Schutz aller fachlichen Endpunkte, eigene Passwortänderung und Anlage des initialen Admins.
- `user-management`: Admins verwalten Benutzer und Rollen, deaktivieren Benutzer und setzen Passwort oder 2FA zurück.
- `deployment`: Betriebsverhalten des Container-Stacks hinter einem Reverse Proxy. Dazu gehören der einzige Einstiegspunkt, interne Dienste, Security-Header, Healthchecks und automatische Datenbank-Backups.

### Modified Capabilities

Keine. Es existieren noch keine Specs.

## Impact

- **Code:** Neu sind `backend/`, `frontend/`, `ops/`, `compose.yml`, `compose.dev.yml`, `.env.example` und `README.md`.
- **Abhängigkeiten:**
  - Backend: Spring Boot 3 (Web, Security, Data JPA, Validation, Actuator), Spring Modulith, Flyway, PostgreSQL, eine TOTP-Bibliothek, Testcontainers.
  - Frontend: Vite, React, React Router, TanStack Query, Tailwind CSS, shadcn/ui und ein QR-Code-Renderer.
- **Betrieb:**
  - Auf dem Mini-PC werden Docker und Docker Compose sowie ein vorhandener Reverse Proxy mit TLS vorausgesetzt.
  - Secrets wie DB-Passwort, Admin-Passwort und der Schlüssel zur Verschlüsselung der TOTP-Secrets kommen aus `.env` und nicht aus den Images.
- **Vorgabe für `core-ledger`:** Freigaben pro Objekt (siehe oben). Das Scaffold legt nur die Systemrollen `ADMIN`/`USER` fest. Die Rechte auf fachliche Daten kommen mit dem ersten freigebbaren Objekt hinzu.
