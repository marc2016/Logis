# authentication Specification

## Purpose

Logis enthält vertrauliche Finanzdaten und ist aus dem Internet erreichbar. Diese Capability stellt sicher, dass nur berechtigte Benutzer nach einer Zwei-Faktor-Anmeldung auf Oberfläche und API zugreifen, und regelt die Anlage des ersten Admin-Kontos.

## Requirements

### Requirement: Initialer Admin
Existiert beim Start noch kein Benutzer, dann SHALL das System einen Benutzer mit der Rolle `ADMIN` und mit Benutzername und Passwort aus der Betriebskonfiguration anlegen. Fehlt diese Konfiguration oder ist das Passwort kürzer als 12 Zeichen, während noch kein Benutzer existiert, dann MUST der Start mit einer verständlichen Fehlermeldung abbrechen. Existiert bereits ein Benutzer, dann MUST das System die Konfiguration ignorieren und keine Benutzerdaten ändern.

#### Scenario: Erster Start
- **WHEN** die Anwendung mit leerer Datenbank und konfiguriertem Admin-Benutzernamen und gültigem Passwort startet
- **THEN** existiert danach ein Admin mit diesen Zugangsdaten, für den noch keine Zwei-Faktor-Anmeldung eingerichtet ist

#### Scenario: Erster Start ohne Konfiguration
- **WHEN** die Anwendung mit leerer Datenbank und ohne Admin-Konfiguration startet
- **THEN** bricht der Start ab, und das Log nennt die fehlenden Einstellungen

#### Scenario: Zu kurzes Passwort
- **WHEN** die Anwendung mit leerer Datenbank und einem Admin-Passwort mit 8 Zeichen startet
- **THEN** bricht der Start ab, und das Log nennt die Mindestlänge

#### Scenario: Späterer Start mit geändertem Passwort in der Konfiguration
- **WHEN** bereits ein Benutzer existiert und die Anwendung mit einem anderen konfigurierten Admin-Passwort startet
- **THEN** bleibt das gespeicherte Passwort unverändert

### Requirement: Passwortregeln und sichere Speicherung
Passwörter MUST mindestens 12 Zeichen lang sein. Sie MUST ausschließlich als gesalzener Hash mit einem für Passwörter vorgesehenen Verfahren gespeichert werden. Sie MUST NOT im Klartext gespeichert, protokolliert oder von der API zurückgegeben werden.

#### Scenario: Gespeichertes Passwort
- **WHEN** ein Benutzer angelegt wurde
- **THEN** enthält die Datenbank für ihn kein Klartextpasswort

#### Scenario: Zu kurzes Passwort wird abgelehnt
- **WHEN** ein Benutzer ein neues Passwort mit 11 Zeichen setzen will
- **THEN** lehnt das System dies mit einer Meldung zur Mindestlänge ab

### Requirement: Zweistufige Anmeldung
Die Anmeldung SHALL in zwei Schritten erfolgen: zuerst Benutzername und Passwort, danach ein gültiger TOTP-Code oder ein Wiederherstellungscode. Erst nach beiden Schritten MUST eine vollständige Sitzung bestehen, die über ein HttpOnly-Cookie gehalten wird. Bis dahin MUST NOT ein Zugriff auf fachliche Endpunkte möglich sein. Bei Fehlern im ersten Schritt MUST die Meldung offenlassen, ob der Benutzername oder das Passwort falsch war.

#### Scenario: Erfolgreiche Anmeldung
- **WHEN** ein Benutzer mit eingerichteter Zwei-Faktor-Anmeldung gültige Zugangsdaten und danach einen gültigen TOTP-Code eingibt
- **THEN** ist er angemeldet und sieht die Übersicht in der App-Shell

#### Scenario: Nur Passwort
- **WHEN** ein Benutzer gültige Zugangsdaten eingegeben, aber noch keinen TOTP-Code bestätigt hat, und die Modulliste abruft
- **THEN** antwortet das System mit HTTP 401

#### Scenario: Falsches Passwort
- **WHEN** der Benutzer einen gültigen Benutzernamen mit falschem Passwort eingibt
- **THEN** bleibt er abgemeldet und sieht die Meldung „Benutzername oder Passwort ist falsch“

#### Scenario: Unbekannter Benutzer
- **WHEN** der Benutzer einen unbekannten Benutzernamen eingibt
- **THEN** sieht er dieselbe Meldung wie bei einem falschen Passwort

#### Scenario: Falscher TOTP-Code
- **WHEN** der Benutzer im zweiten Schritt einen falschen Code eingibt
- **THEN** bleibt er abgemeldet, sieht die Meldung „Code ist ungültig“ und kann es erneut versuchen

#### Scenario: Wiederverwendeter TOTP-Code
- **WHEN** ein TOTP-Code, mit dem sich der Benutzer bereits erfolgreich angemeldet hat, innerhalb seiner Gültigkeit erneut verwendet wird
- **THEN** lehnt das System ihn ab

#### Scenario: Deaktivierter Benutzer
- **WHEN** ein deaktivierter Benutzer korrekte Zugangsdaten eingibt
- **THEN** bleibt er abgemeldet und sieht dieselbe Meldung wie bei falschen Zugangsdaten

### Requirement: Pflicht zur Einrichtung der Zwei-Faktor-Anmeldung
Ein Benutzer ohne eingerichtete Zwei-Faktor-Anmeldung SHALL nach dem ersten Schritt die Einrichtung durchlaufen müssen. Das System MUST dabei ein Secret als QR-Code und als Text anzeigen. Die Einrichtung MUST erst nach Bestätigung mit einem gültigen Code aus der Authenticator-App abgeschlossen sein. Danach MUST das System genau einmal 10 Wiederherstellungscodes anzeigen. Vor Abschluss der Einrichtung MUST NOT ein Zugriff auf fachliche Endpunkte möglich sein.

#### Scenario: Erster Login des Admins
- **WHEN** sich der initiale Admin zum ersten Mal mit Benutzername und Passwort anmeldet
- **THEN** sieht er die Einrichtung mit QR-Code statt der Übersicht

#### Scenario: Einrichtung abschließen
- **WHEN** der Benutzer einen gültigen Code aus der Authenticator-App bestätigt
- **THEN** sieht er 10 Wiederherstellungscodes mit dem Hinweis, sie sicher aufzubewahren, und ist danach vollständig angemeldet

#### Scenario: Einrichtung mit falschem Code
- **WHEN** der Benutzer bei der Einrichtung einen falschen Code eingibt
- **THEN** ist die Zwei-Faktor-Anmeldung nicht eingerichtet, und der Benutzer kann es erneut versuchen

### Requirement: Wiederherstellungscodes
Jeder Wiederherstellungscode SHALL im zweiten Anmeldeschritt anstelle eines TOTP-Codes genau einmal verwendbar sein. Die Codes MUST nur als Hash gespeichert werden. Ein Benutzer SHALL neue Codes erzeugen können, wobei alle bisherigen ungültig werden.

#### Scenario: Anmeldung mit Wiederherstellungscode
- **WHEN** der Benutzer im zweiten Schritt einen unbenutzten Wiederherstellungscode eingibt
- **THEN** ist er angemeldet, und dieser Code ist danach ungültig

#### Scenario: Neue Codes erzeugen
- **WHEN** der angemeldete Benutzer unter „Mein Konto“ neue Wiederherstellungscodes erzeugt
- **THEN** sieht er 10 neue Codes, und keiner der alten Codes funktioniert mehr

### Requirement: Schutz vor Brute-Force
Nach 5 aufeinanderfolgenden fehlgeschlagenen Anmeldeversuchen eines Benutzers, egal ob im Passwort- oder im Code-Schritt, MUST das System weitere Versuche für diesen Benutzer zeitweise sperren. Die Sperre SHALL bei jedem weiteren Fehlschlag wachsen, höchstens bis 15 Minuten. Eine erfolgreiche Anmeldung MUST den Zähler zurücksetzen. Zusätzlich MUST die Anzahl der Anmeldeanfragen pro Client-IP begrenzt sein. Fehlgeschlagene Anmeldungen MUST mit Benutzername, Client-IP und Zeitpunkt protokolliert werden, jedoch ohne Passwort oder Code.

#### Scenario: Sperre nach Fehlversuchen
- **WHEN** für einen Benutzer 5 Anmeldeversuche nacheinander fehlschlagen und danach die korrekten Zugangsdaten eingegeben werden
- **THEN** wird die Anmeldung abgelehnt, und der Benutzer sieht „Zu viele Fehlversuche, bitte später erneut versuchen“

#### Scenario: Ende der Sperre
- **WHEN** die Sperrzeit abgelaufen ist und der Benutzer korrekte Zugangsdaten eingibt
- **THEN** wird die Anmeldung fortgesetzt

#### Scenario: Viele Anfragen von einer IP
- **WHEN** eine Client-IP in kurzer Zeit sehr viele Anmeldeanfragen sendet
- **THEN** werden weitere Anfragen dieser IP mit HTTP 429 abgelehnt

### Requirement: Sitzungsdauer
Eine Sitzung MUST nach 30 Minuten Inaktivität und spätestens 12 Stunden nach der Anmeldung enden.

#### Scenario: Inaktivität
- **WHEN** ein angemeldeter Benutzer 30 Minuten lang keine Anfrage stellt und dann die Modulliste abruft
- **THEN** antwortet das System mit HTTP 401

#### Scenario: Absolute Dauer
- **WHEN** seit der Anmeldung mehr als 12 Stunden vergangen sind, auch bei durchgehender Aktivität
- **THEN** antwortet das System auf die nächste Anfrage mit HTTP 401

### Requirement: Abmelden
Das System SHALL eine Abmeldung anbieten, die die Sitzung serverseitig beendet.

#### Scenario: Abmelden
- **WHEN** der angemeldete Benutzer sich abmeldet
- **THEN** sieht er die Anmeldeseite, und weitere API-Aufrufe mit dem alten Sitzungs-Cookie werden mit HTTP 401 beantwortet

### Requirement: Eigenes Passwort ändern
Ein angemeldeter Benutzer SHALL sein Passwort unter Angabe des aktuellen Passworts ändern können. Andere Sitzungen desselben Benutzers MUST danach enden. Wurde das Passwort durch einen Admin zurückgesetzt, dann MUST der Benutzer es nach dem nächsten Login ändern, bevor er fachliche Endpunkte nutzen kann.

#### Scenario: Passwort ändern
- **WHEN** der Benutzer unter „Mein Konto“ das aktuelle und ein neues gültiges Passwort eingibt
- **THEN** gilt das neue Passwort, und seine Sitzungen auf anderen Geräten sind beendet

#### Scenario: Falsches aktuelles Passwort
- **WHEN** der Benutzer ein falsches aktuelles Passwort angibt
- **THEN** bleibt das Passwort unverändert

#### Scenario: Erzwungene Änderung
- **WHEN** sich ein Benutzer anmeldet, dessen Passwort ein Admin zurückgesetzt hat
- **THEN** muss er nach der Zwei-Faktor-Anmeldung ein neues Passwort setzen, bevor er die Übersicht sieht

### Requirement: Geschützte API
Alle Endpunkte unter `/api/` außer denen der Anmeldeschritte, der Sitzungsabfrage und Health MUST eine vollständige Sitzung erfordern. Fehlt sie, dann SHALL das System mit HTTP 401 antworten und MUST NOT auf eine HTML-Anmeldeseite umleiten.

#### Scenario: Aufruf ohne Sitzung
- **WHEN** ein Client ohne Sitzung die Modulliste abruft
- **THEN** antwortet das System mit HTTP 401

### Requirement: Schutz vor Cross-Site-Request-Forgery
Zustandsändernde API-Aufrufe (POST, PUT, PATCH, DELETE) MUST ein gültiges CSRF-Token mitsenden. Andernfalls SHALL das System den Aufruf mit HTTP 403 ablehnen.

#### Scenario: Aufruf ohne Token
- **WHEN** ein Client mit gültiger Sitzung, aber ohne CSRF-Token einen POST-Aufruf an die API sendet
- **THEN** antwortet das System mit HTTP 403

### Requirement: Weiterleitung zur Anmeldung in der Oberfläche
Öffnet ein nicht angemeldeter Benutzer eine Seite der Anwendung, dann SHALL die Oberfläche die Anmeldung zeigen. Nach erfolgreicher Anmeldung MUST sie zur ursprünglich aufgerufenen Seite zurückkehren.

#### Scenario: Deep Link ohne Sitzung
- **WHEN** ein nicht angemeldeter Benutzer die URL der Belegungsseite öffnet und sich danach vollständig anmeldet
- **THEN** sieht er nach der Anmeldung die Belegungsseite

#### Scenario: Sitzung läuft ab
- **WHEN** die Sitzung abgelaufen ist und die Oberfläche eine Antwort mit HTTP 401 erhält
- **THEN** zeigt die Oberfläche die Anmeldung
