# Spec Delta

## Purpose

Logis besteht aus einem Kern und fachlichen Modulen (z. B. Ferienvermietung). Diese Capability legt fest, wie Module aktiviert und deaktiviert werden und wie das System die aktiven Module nach außen bekannt macht.

## ADDED Requirements

### Requirement: Module per Konfiguration aktivieren
Jedes fachliche Modul MUST über eine Konfigurationseinstellung aktivierbar und deaktivierbar sein, ohne dass der Code geändert wird. Ein Modul ohne explizite Einstellung SHALL als aktiviert gelten.

#### Scenario: Modul ist standardmäßig aktiv
- **WHEN** die Anwendung ohne Einstellung für das Modul `vacationrental` startet
- **THEN** ist das Modul `vacationrental` aktiv

#### Scenario: Modul wird deaktiviert
- **WHEN** die Anwendung mit der Einstellung startet, dass `vacationrental` deaktiviert ist
- **THEN** ist das Modul `vacationrental` nicht aktiv

### Requirement: Aktive Module abrufen
Das System SHALL angemeldeten Benutzern eine Liste der aktiven Module bereitstellen. Jeder Eintrag MUST mindestens eine stabile Modul-ID enthalten. Deaktivierte Module MUST NOT in der Liste erscheinen.

#### Scenario: Liste enthält aktive Module
- **WHEN** ein angemeldeter Benutzer die Modulliste abruft und `vacationrental` aktiv ist
- **THEN** enthält die Antwort einen Eintrag mit der ID `vacationrental`

#### Scenario: Deaktiviertes Modul fehlt in der Liste
- **WHEN** ein angemeldeter Benutzer die Modulliste abruft und `vacationrental` deaktiviert ist
- **THEN** enthält die Antwort keinen Eintrag mit der ID `vacationrental`

#### Scenario: Liste ohne Anmeldung
- **WHEN** ein nicht angemeldeter Client die Modulliste abruft
- **THEN** antwortet das System mit HTTP 401

### Requirement: Endpunkte deaktivierter Module sind nicht erreichbar
Die API-Endpunkte eines deaktivierten Moduls MUST nicht erreichbar sein. Aufrufe SHALL mit HTTP 404 beantwortet werden.

#### Scenario: Aufruf eines deaktivierten Moduls
- **WHEN** ein angemeldeter Benutzer einen Endpunkt unter `/api/vacationrental/` aufruft und `vacationrental` deaktiviert ist
- **THEN** antwortet das System mit HTTP 404

#### Scenario: Aufruf eines aktiven Moduls
- **WHEN** ein angemeldeter Benutzer den Status-Endpunkt von `vacationrental` aufruft und das Modul aktiv ist
- **THEN** antwortet das System mit HTTP 200

### Requirement: Daten deaktivierter Module bleiben erhalten
Wird ein Modul deaktiviert, dann MUST das System dessen gespeicherte Daten unverändert lassen. Nach einer erneuten Aktivierung SHALL der vorherige Datenbestand wieder verfügbar sein.

#### Scenario: Deaktivieren und wieder aktivieren
- **WHEN** ein Modul mit gespeicherten Daten deaktiviert, die Anwendung neu gestartet und das Modul danach wieder aktiviert wird
- **THEN** sind die zuvor gespeicherten Daten des Moduls unverändert vorhanden
