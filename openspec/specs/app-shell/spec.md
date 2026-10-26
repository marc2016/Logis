# app-shell Specification

## Purpose

Die App-Shell ist der gemeinsame Rahmen der Weboberfläche. Sie stellt die Navigation über eine linke Sidebar bereit, in der jedes aktive Modul eine eigene Gruppe hat, und zeigt die Seiten der Module im Inhaltsbereich an.

## Requirements

### Requirement: Sidebar mit Gruppen pro Modul
Die Oberfläche SHALL links eine Sidebar anzeigen. Sie MUST einen Bereich für den Kern (mindestens „Übersicht“) und für jedes aktive Modul eine eigene Gruppe mit dessen Navigationseinträgen enthalten. Deaktivierte Module MUST NOT in der Sidebar erscheinen.

#### Scenario: Aktives Modul erscheint als Gruppe
- **WHEN** ein angemeldeter Benutzer die Anwendung öffnet und `vacationrental` aktiv ist
- **THEN** zeigt die Sidebar eine Gruppe „Ferienvermietung“ mit deren Navigationseinträgen

#### Scenario: Deaktiviertes Modul fehlt
- **WHEN** ein angemeldeter Benutzer die Anwendung öffnet und `vacationrental` deaktiviert ist
- **THEN** zeigt die Sidebar keine Gruppe „Ferienvermietung“

### Requirement: Sidebar einklappbar
Die Sidebar SHALL sich auf eine reine Icon-Leiste einklappen und wieder ausklappen lassen. Der gewählte Zustand MUST beim erneuten Laden der Seite im selben Browser erhalten bleiben.

#### Scenario: Einklappen
- **WHEN** der Benutzer die Sidebar einklappt
- **THEN** zeigt die Sidebar nur noch Icons, und der Inhaltsbereich nimmt die frei gewordene Breite ein

#### Scenario: Zustand bleibt erhalten
- **WHEN** der Benutzer die Sidebar eingeklappt hat und die Seite neu lädt
- **THEN** ist die Sidebar weiterhin eingeklappt

### Requirement: Navigation zu Modulseiten
Ein Klick auf einen Navigationseintrag SHALL die zugehörige Seite im Inhaltsbereich anzeigen, ohne die gesamte Seite neu zu laden. Der aktive Eintrag MUST in der Sidebar hervorgehoben sein. Jede Seite MUST eine eigene URL haben, die direkt aufgerufen werden kann.

#### Scenario: Eintrag anklicken
- **WHEN** der Benutzer in der Gruppe „Ferienvermietung“ auf „Belegung“ klickt
- **THEN** zeigt der Inhaltsbereich die Belegungsseite, die URL ändert sich entsprechend, und „Belegung“ ist hervorgehoben

#### Scenario: Direkter Aufruf einer URL
- **WHEN** der angemeldete Benutzer die URL einer Modulseite direkt im Browser öffnet
- **THEN** zeigt die Anwendung diese Seite innerhalb der Shell an

#### Scenario: URL eines deaktivierten Moduls
- **WHEN** der Benutzer die URL einer Seite eines deaktivierten Moduls öffnet
- **THEN** zeigt die Anwendung eine „Seite nicht gefunden“-Ansicht innerhalb der Shell

### Requirement: Nutzbarkeit auf schmalen Bildschirmen
Auf schmalen Bildschirmen (Smartphone-Breite) SHALL die Sidebar standardmäßig ausgeblendet sein und sich über eine Schaltfläche als Overlay öffnen lassen.

#### Scenario: Smartphone
- **WHEN** die Anwendung in einem Fenster mit Smartphone-Breite geöffnet wird
- **THEN** ist die Sidebar ausgeblendet, und eine Menü-Schaltfläche öffnet sie als Overlay

### Requirement: Helles Erscheinungsbild
Die Oberfläche SHALL ausschließlich in einem hellen Design dargestellt werden: helle Hintergründe und dunkle Schrift. Das gilt unabhängig davon, ob das Betriebssystem oder der Browser einen Dunkelmodus eingestellt hat.

#### Scenario: Dunkelmodus im System
- **WHEN** der Benutzer die Anwendung auf einem Gerät mit aktiviertem Dunkelmodus öffnet
- **THEN** werden Shell, Sidebar und Inhalte im hellen Design angezeigt

### Requirement: Deutsche Oberfläche
Alle Texte der Oberfläche SHALL auf Deutsch sein.

#### Scenario: Beschriftungen
- **WHEN** der Benutzer die Shell betrachtet
- **THEN** sind Navigation, Schaltflächen und Meldungen deutsch beschriftet
