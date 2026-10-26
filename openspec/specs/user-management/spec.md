# user-management Specification

## Purpose

Logis wird von mehreren Personen genutzt. Wer Zugang bekommt, entscheidet ausschließlich ein Admin. Diese Capability regelt Rollen sowie das Anlegen, Deaktivieren und Zurücksetzen von Benutzern.

## Requirements

### Requirement: Rollen
Jeder Benutzer MUST genau eine der Rollen `ADMIN` oder `USER` haben. Nur Benutzer mit der Rolle `ADMIN` SHALL Zugriff auf die Benutzerverwaltung haben. Aufrufe der Benutzerverwaltung durch einen `USER` MUST mit HTTP 403 beantwortet werden.

#### Scenario: Benutzer ohne Adminrechte
- **WHEN** ein angemeldeter `USER` die Benutzerliste über die API abruft
- **THEN** antwortet das System mit HTTP 403

#### Scenario: Menüeintrag
- **WHEN** ein angemeldeter `USER` die App-Shell betrachtet
- **THEN** sieht er keinen Eintrag „Benutzer“

### Requirement: Keine Selbstregistrierung
Das System MUST NOT eine Möglichkeit bieten, sich selbst ein Konto anzulegen. Benutzer SHALL ausschließlich durch einen Admin angelegt werden.

#### Scenario: Anmeldeseite
- **WHEN** ein nicht angemeldeter Besucher die Anmeldeseite öffnet
- **THEN** gibt es dort keine Registrierungsmöglichkeit

### Requirement: Benutzer auflisten
Ein Admin SHALL alle Benutzer mit Benutzername, Anzeigename, Rolle, Status (aktiv/deaktiviert), Status der Zwei-Faktor-Anmeldung (eingerichtet/ausstehend) und Zeitpunkt der letzten Anmeldung sehen. Passwort-Hashes, TOTP-Secrets und Wiederherstellungscodes MUST NOT ausgeliefert werden.

#### Scenario: Liste
- **WHEN** ein Admin die Benutzerverwaltung öffnet
- **THEN** sieht er alle Benutzer mit den genannten Angaben

### Requirement: Benutzer anlegen
Ein Admin SHALL einen Benutzer mit Benutzername, Anzeigename, Rolle und einem Startpasswort anlegen können. Benutzernamen MUST eindeutig sein, unabhängig von Groß- und Kleinschreibung. Der neue Benutzer MUST beim ersten Login sein Passwort ändern und die Zwei-Faktor-Anmeldung einrichten.

#### Scenario: Anlegen
- **WHEN** ein Admin einen Benutzer „anna“ mit Rolle `USER` und gültigem Startpasswort anlegt
- **THEN** erscheint „anna“ in der Liste als aktiv mit ausstehender Zwei-Faktor-Anmeldung

#### Scenario: Doppelter Benutzername
- **WHEN** ein Admin einen Benutzer „Anna“ anlegen will und „anna“ bereits existiert
- **THEN** lehnt das System dies mit der Meldung „Benutzername ist bereits vergeben“ ab

#### Scenario: Erster Login des neuen Benutzers
- **WHEN** sich „anna“ zum ersten Mal mit dem Startpasswort anmeldet
- **THEN** muss sie die Zwei-Faktor-Anmeldung einrichten und ein eigenes Passwort setzen, bevor sie die Übersicht sieht

### Requirement: Benutzer bearbeiten
Ein Admin SHALL Anzeigename und Rolle eines Benutzers ändern können.

#### Scenario: Rolle ändern
- **WHEN** ein Admin die Rolle von „anna“ auf `ADMIN` ändert
- **THEN** hat „anna“ bei ihrer nächsten Anfrage Zugriff auf die Benutzerverwaltung

### Requirement: Benutzer deaktivieren und reaktivieren
Ein Admin SHALL Benutzer deaktivieren und reaktivieren können. Beim Deaktivieren MUST jede bestehende Sitzung des Benutzers sofort enden. Benutzer werden nicht gelöscht, damit spätere fachliche Daten ihnen zugeordnet bleiben.

#### Scenario: Deaktivieren
- **WHEN** ein Admin „anna“ deaktiviert, während sie angemeldet ist
- **THEN** wird ihre nächste Anfrage mit HTTP 401 beantwortet, und sie kann sich nicht mehr anmelden

#### Scenario: Reaktivieren
- **WHEN** ein Admin „anna“ wieder aktiviert
- **THEN** kann sie sich mit ihren bisherigen Zugangsdaten und ihrer Zwei-Faktor-Anmeldung wieder anmelden

### Requirement: Passwort und Zwei-Faktor-Anmeldung zurücksetzen
Ein Admin SHALL das Passwort eines Benutzers auf ein neues Startpasswort zurücksetzen können. Er SHALL außerdem dessen Zwei-Faktor-Anmeldung zurücksetzen können. In beiden Fällen MUST jede bestehende Sitzung des Benutzers enden. Nach dem Zurücksetzen der Zwei-Faktor-Anmeldung MUST der Benutzer sie beim nächsten Login neu einrichten, und seine alten Wiederherstellungscodes MUST ungültig sein.

#### Scenario: Passwort zurücksetzen
- **WHEN** ein Admin das Passwort von „anna“ zurücksetzt
- **THEN** kann sie sich nur noch mit dem neuen Startpasswort anmelden und muss danach ein eigenes Passwort setzen

#### Scenario: Zwei-Faktor-Anmeldung zurücksetzen
- **WHEN** ein Admin die Zwei-Faktor-Anmeldung von „anna“ zurücksetzt, weil sie ihr Telefon verloren hat
- **THEN** muss „anna“ nach Eingabe ihres Passworts die Zwei-Faktor-Anmeldung neu einrichten

### Requirement: Mindestens ein aktiver Admin
Das System MUST verhindern, dass der letzte aktive Admin deaktiviert oder auf `USER` herabgestuft wird. Ein Admin MUST NOT sich selbst deaktivieren können.

#### Scenario: Letzter Admin
- **WHEN** der einzige aktive Admin seine eigene Rolle auf `USER` ändern will
- **THEN** lehnt das System dies mit der Meldung „Es muss mindestens ein aktiver Admin bestehen bleiben“ ab

#### Scenario: Sich selbst deaktivieren
- **WHEN** ein Admin versucht, sein eigenes Konto zu deaktivieren
- **THEN** lehnt das System dies ab
