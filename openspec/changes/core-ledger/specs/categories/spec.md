# Spec Delta

## Purpose

Kategorien ordnen Transaktionen fachlich ein, z. B. „Lebensmittel“ oder „Grundsteuer“. Ein gemeinsamer Kategorienbaum für alle Benutzer sorgt dafür, dass Auswertungen über Konten und Rechtsträger hinweg vergleichbar sind.

## ADDED Requirements

### Requirement: Gemeinsamer Kategorienbaum
Das System SHALL einen für alle Benutzer gemeinsamen Kategorienbaum mit höchstens zwei Ebenen führen: Hauptkategorien und Unterkategorien. Jede Kategorie MUST eine Art haben, `INCOME` (Einnahme) oder `EXPENSE` (Ausgabe). Eine Unterkategorie hat immer die Art ihrer Hauptkategorie. Namen MUST innerhalb derselben Hauptkategorie bzw. unter den Hauptkategorien einer Art eindeutig sein, unabhängig von Groß- und Kleinschreibung.

#### Scenario: Baum abrufen
- **WHEN** ein angemeldeter Benutzer die Kategorien abruft
- **THEN** erhält er alle aktiven Kategorien als Baum, getrennt nach Einnahmen und Ausgaben

#### Scenario: Dritte Ebene
- **WHEN** ein Admin unter einer Unterkategorie eine weitere Kategorie anlegen will
- **THEN** lehnt das System dies ab

### Requirement: Standardkategorien
Bei der Einrichtung SHALL das System eine deutsche Grundausstattung an Kategorien anlegen. Sie umfasst mindestens:
- Einnahmen: Gehalt, Mieteinnahmen, Kapitalerträge, Sonstige Einnahmen
- Ausgaben: Wohnen (mit Grundsteuer, Nebenkosten, Instandhaltung), Lebensmittel, Mobilität, Versicherungen, Steuern und Abgaben, Gebühren, Sonstige Ausgaben

#### Scenario: Frische Installation
- **WHEN** Logis zum ersten Mal startet
- **THEN** stehen die Standardkategorien zur Auswahl

### Requirement: Kategorien pflegen
Nur Benutzer mit der Rolle `ADMIN` SHALL Kategorien anlegen, umbenennen, verschieben und archivieren können. Andere Benutzer MUST bei diesen Aufrufen HTTP 403 erhalten. Eine Kategorie MUST NOT gelöscht werden, wenn ihr Transaktionen oder Unterkategorien zugeordnet sind. Archivieren MUST dagegen immer möglich sein. Archivierte Kategorien SHALL wiederhergestellt werden können.

#### Scenario: Kategorie anlegen
- **WHEN** ein Admin unter „Ausgaben > Wohnen“ die Unterkategorie „Hausgeld“ anlegt
- **THEN** ist „Hausgeld“ für alle Benutzer beim Erfassen von Transaktionen auswählbar

#### Scenario: Benutzer ohne Adminrechte
- **WHEN** ein `USER` eine Kategorie anlegen will
- **THEN** antwortet das System mit HTTP 403

#### Scenario: Verwendete Kategorie löschen
- **WHEN** ein Admin eine Kategorie löschen will, die Transaktionen zugeordnet ist
- **THEN** lehnt das System dies mit der Meldung „Die Kategorie wird verwendet und kann nur archiviert werden“ ab

### Requirement: Kategorien von Modulen
Kategorien SHALL einem Modul zugeordnet sein können. Solche Kategorien MUST für Admins sichtbar und archivierbar sein, MUST NOT aber umbenannt, verschoben oder gelöscht werden können, weil das Modul sich auf sie verlässt. Ist das Modul deaktiviert, MUST das System seine Kategorien beim Erfassen nicht anbieten. Bestehende Zuordnungen MUST erhalten bleiben.

#### Scenario: Modulkategorie umbenennen
- **WHEN** ein Admin eine vom Modul Ferienvermietung bereitgestellte Kategorie umbenennen will
- **THEN** lehnt das System dies mit der Meldung „Diese Kategorie wird von einem Modul verwaltet“ ab
