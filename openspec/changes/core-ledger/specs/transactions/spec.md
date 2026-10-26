# Spec Delta

## Purpose

Transaktionen sind die einzelnen Geldbewegungen auf einem Konto. Sie bilden die Grundlage für Salden, Einnahmen- und Ausgabenübersichten und spätere Steuerauswertungen. In diesem Ausbau werden sie von Hand erfasst.

## ADDED Requirements

### Requirement: Transaktion erfassen
Ein Benutzer mit mindestens `EDITOR`-Rechten auf einem Konto SHALL darin eine Transaktion erfassen können. Pflicht sind Buchungsdatum und Betrag. Optional sind Kategorie, Gegenpartei, Verwendungszweck und Notiz. Der Betrag MUST ungleich 0 sein, höchstens zwei Nachkommastellen haben und einen Betrag von 999.999.999,99 nicht überschreiten. Positive Beträge sind Einnahmen, negative Ausgaben.

#### Scenario: Ausgabe erfassen
- **WHEN** Anna im Konto „Girokonto“ am 03.10.2026 eine Transaktion über -84,90 mit Kategorie „Lebensmittel“ und Gegenpartei „Edeka“ erfasst
- **THEN** erscheint sie in der Liste des Kontos, und der Saldo sinkt um 84,90

#### Scenario: Betrag 0 oder zu genau
- **WHEN** Anna eine Transaktion über 0,00 oder über 12,345 erfassen will
- **THEN** lehnt das System dies mit einer Meldung zum Betrag ab

### Requirement: Transaktion bearbeiten und löschen
Ein Benutzer mit mindestens `EDITOR`-Rechten SHALL Transaktionen des Kontos ändern und löschen können. Das Verschieben einer Transaktion in ein anderes Konto MUST auch dort mindestens `EDITOR`-Rechte erfordern. Gleichzeitige Änderungen derselben Transaktion MUST erkannt werden. Die zweite Änderung MUST dann mit der Meldung „Die Transaktion wurde inzwischen geändert“ abgelehnt werden, statt die erste zu überschreiben.

#### Scenario: Kategorie korrigieren
- **WHEN** Bert als `EDITOR` die Kategorie einer Transaktion ändert
- **THEN** zeigt die Transaktion die neue Kategorie

#### Scenario: Gleichzeitige Bearbeitung
- **WHEN** Anna und Bert dieselbe Transaktion öffnen, Anna speichert und Bert danach mit dem alten Stand speichert
- **THEN** wird Berts Änderung mit „Die Transaktion wurde inzwischen geändert“ abgelehnt

### Requirement: Umbuchung zwischen Konten
Ein Benutzer mit mindestens `EDITOR`-Rechten auf beiden Konten SHALL einen Betrag von einem Konto auf ein anderes umbuchen können. Das System MUST dazu zwei verknüpfte Transaktionen anlegen: den negativen Betrag im Quellkonto und den positiven im Zielkonto, mit gleichem Datum. Umbuchungen MUST die Salden ändern, MUST NOT als Einnahme oder Ausgabe zählen und haben keine Kategorie. Ändern oder Löschen der einen Seite MUST die andere Seite mit ändern bzw. löschen.

#### Scenario: Umbuchung aufs Tagesgeld
- **WHEN** Anna 500,00 vom „Girokonto“ auf „Tagesgeld“ umbucht
- **THEN** sinkt der Saldo des Girokontos um 500,00, der des Tagesgeldkontos steigt um 500,00, und die Summen der Einnahmen und Ausgaben bleiben unverändert

#### Scenario: Umbuchung löschen
- **WHEN** Anna eine Seite der Umbuchung löscht
- **THEN** sind beide Transaktionen gelöscht

#### Scenario: Fehlende Rechte am Zielkonto
- **WHEN** Bert vom eigenen Konto auf ein Konto umbuchen will, für das er nur `VIEWER` ist
- **THEN** antwortet das System mit HTTP 403

### Requirement: Transaktionen auflisten und filtern
Ein Benutzer SHALL die Transaktionen aller ihm freigegebenen Konten oder eines einzelnen Kontos auflisten können. Sie sind nach Datum absteigend sortiert und seitenweise mit je höchstens 100 Einträgen abrufbar. Die Liste SHALL sich filtern lassen nach Konto, Zeitraum, Kategorie (einschließlich ihrer Unterkategorien), „ohne Kategorie“ und einem Suchtext in Gegenpartei, Verwendungszweck und Notiz. Zu jedem Filter MUST das System die Summe der Einnahmen und die Summe der Ausgaben liefern, ohne Umbuchungen und über alle Seiten hinweg.

#### Scenario: Monatsübersicht
- **WHEN** Anna die Transaktionen aller Konten für Oktober 2026 abruft
- **THEN** erhält sie die Transaktionen des Oktobers und die Summen der Einnahmen und Ausgaben dieses Monats

#### Scenario: Fremde Konten bleiben verborgen
- **WHEN** Bert die Transaktionen aller Konten abruft
- **THEN** enthält die Liste nur Transaktionen aus Konten, die ihm freigegeben sind

#### Scenario: Unkategorisierte finden
- **WHEN** Anna nach „ohne Kategorie“ filtert
- **THEN** erhält sie nur Transaktionen ohne Kategorie und ohne Umbuchungen

### Requirement: Nur aktive Kategorien zuweisen
Einer Transaktion MUST nur eine existierende, nicht archivierte Kategorie zugewiesen werden können. Bereits zugewiesene Kategorien MUST nach ihrer Archivierung an den Transaktionen erhalten bleiben.

#### Scenario: Archivierte Kategorie
- **WHEN** Anna einer neuen Transaktion eine archivierte Kategorie zuweisen will
- **THEN** lehnt das System dies mit der Meldung „Die Kategorie ist nicht verfügbar“ ab
