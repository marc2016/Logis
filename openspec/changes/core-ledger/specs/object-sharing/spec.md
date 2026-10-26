# Spec Delta

## Purpose

Fachliche Daten in Logis gehören einzelnen Benutzern und werden gezielt geteilt. Diese Capability regelt die Freigabe einzelner Objekte (zunächst Konten) an bestehende Benutzer mit abgestuften Rechten und gilt für alle Module.

## ADDED Requirements

### Requirement: Rollen pro Objekt
Jede Freigabe MUST einem Benutzer genau eine der Rollen `OWNER`, `EDITOR` oder `VIEWER` für ein Objekt geben:
- `VIEWER` SHALL das Objekt und seine untergeordneten Daten lesen dürfen.
- `EDITOR` SHALL zusätzlich untergeordnete Daten anlegen, ändern und löschen dürfen, z. B. Transaktionen eines Kontos.
- `OWNER` SHALL zusätzlich das Objekt selbst ändern, archivieren und dessen Freigaben verwalten dürfen.

Ohne Freigabe MUST ein Objekt für einen Benutzer unsichtbar sein. Es MUST dann wie nicht vorhanden behandelt werden, also mit HTTP 404 beantwortet werden statt mit 403.

#### Scenario: Ohne Freigabe
- **WHEN** Benutzer Bert ein Konto abruft, das ihm nicht freigegeben ist
- **THEN** antwortet das System mit HTTP 404, und das Konto erscheint in keiner seiner Listen

#### Scenario: Viewer darf nicht schreiben
- **WHEN** Bert als `VIEWER` eines Kontos eine Transaktion darin anlegen will
- **THEN** antwortet das System mit HTTP 403

#### Scenario: Editor darf keine Freigaben verwalten
- **WHEN** Bert als `EDITOR` eines Kontos einen weiteren Benutzer einladen will
- **THEN** antwortet das System mit HTTP 403

### Requirement: Admin ohne automatischen Datenzugriff
Die Systemrolle `ADMIN` MUST NOT Zugriff auf fachliche Objekte geben. Ein Admin sieht nur Objekte, die ihm selbst gehören oder freigegeben sind.

#### Scenario: Admin und fremdes Konto
- **WHEN** ein Admin ein Konto abruft, das ihm nicht freigegeben ist
- **THEN** antwortet das System mit HTTP 404

### Requirement: Ersteller ist Owner
Wer ein freigebbares Objekt anlegt, MUST automatisch dessen `OWNER` werden.

#### Scenario: Konto anlegen
- **WHEN** Anna ein Konto anlegt
- **THEN** ist Anna `OWNER` dieses Kontos

### Requirement: Benutzer einladen
Ein `OWNER` SHALL bestehende, aktive Benutzer als `VIEWER`, `EDITOR` oder `OWNER` zu einem Objekt einladen können. Die Freigabe MUST sofort wirksam sein. Einladungen an nicht existierende oder deaktivierte Benutzer MUST abgelehnt werden. Hat ein Benutzer bereits eine Freigabe, dann MUST das System sie nicht doppelt anlegen und stattdessen die Rolle ändern.

#### Scenario: Einladen
- **WHEN** Anna als `OWNER` Bert als `EDITOR` zu ihrem Konto einlädt
- **THEN** sieht Bert das Konto bei seiner nächsten Anfrage und darf Transaktionen darin erfassen

#### Scenario: Deaktivierter Benutzer
- **WHEN** Anna einen deaktivierten Benutzer einladen will
- **THEN** lehnt das System dies mit der Meldung „Benutzer nicht gefunden“ ab

### Requirement: Freigaben ändern und entziehen
Ein `OWNER` SHALL die Rolle einer bestehenden Freigabe ändern und Freigaben entziehen können. Jeder Benutzer SHALL seine eigene Freigabe aufgeben können. Ein Objekt MUST immer mindestens einen `OWNER` behalten.

#### Scenario: Freigabe entziehen
- **WHEN** Anna die Freigabe von Bert entzieht
- **THEN** ist das Konto für Bert bei seiner nächsten Anfrage nicht mehr sichtbar

#### Scenario: Letzter Owner
- **WHEN** Anna als einzige `OWNER` ihre eigene Rolle auf `VIEWER` ändern oder ihre Freigabe aufgeben will
- **THEN** lehnt das System dies mit der Meldung „Es muss mindestens einen Owner geben“ ab

### Requirement: Freigaben einsehen
Jeder Benutzer mit Zugriff auf ein Objekt SHALL sehen können, wer welche Rolle hat (Anzeigename, Benutzername, Rolle).

#### Scenario: Liste der Freigaben
- **WHEN** Bert als `VIEWER` die Freigaben eines Kontos aufruft
- **THEN** sieht er alle Berechtigten mit ihrer Rolle, aber keine Schaltflächen zum Ändern

### Requirement: Benutzersuche zum Einladen
Angemeldete Benutzer SHALL aktive Benutzer anhand von Benutzername oder Anzeigename suchen können, um sie einzuladen. Die Suche MUST mindestens 2 Zeichen verlangen, höchstens 10 Treffer liefern und pro Treffer nur ID, Benutzername und Anzeigename ausgeben. Deaktivierte Benutzer MUST NOT erscheinen.

#### Scenario: Suche
- **WHEN** Anna nach „be“ sucht
- **THEN** erhält sie aktive Benutzer, deren Benutzername oder Anzeigename „be“ enthält, ohne Rolle, Status oder andere Angaben

#### Scenario: Zu kurze Suche
- **WHEN** Anna nach „b“ sucht
- **THEN** erhält sie eine leere Liste
