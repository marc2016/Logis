# Spec Delta

## Purpose

Konten bilden die Orte ab, an denen Geld liegt (Girokonto, Tagesgeld, Bargeld usw.). Sie gehören einem Rechtsträger, tragen die Transaktionen und sind das erste freigebbare Objekt in Logis.

## ADDED Requirements

### Requirement: Konto anlegen
Ein Benutzer SHALL in einem Rechtsträger, den er verwaltet, ein Konto mit Name, Art (`CHECKING`, `SAVINGS`, `CASH`, `CREDIT_CARD`, `OTHER`), optional einer IBAN und einem Anfangssaldo (Standard 0,00) anlegen können. Die Währung MUST `EUR` sein. Eine angegebene IBAN MUST formal gültig sein (Länge und Prüfziffer).

#### Scenario: Girokonto anlegen
- **WHEN** Anna im Rechtsträger „Anna privat“ das Konto „Girokonto“ der Art `CHECKING` mit Anfangssaldo 1.250,00 anlegt
- **THEN** erscheint das Konto mit Saldo 1.250,00 EUR in ihrer Kontenliste

#### Scenario: Ungültige IBAN
- **WHEN** Anna eine IBAN mit falscher Prüfziffer angibt
- **THEN** lehnt das System dies mit der Meldung „Die IBAN ist ungültig“ ab

### Requirement: Kontenliste
Ein Benutzer SHALL alle ihm freigegebenen, nicht archivierten Konten sehen, gruppiert nach Rechtsträger. Jeder Eintrag zeigt Name, Art, Rechtsträger, seine eigene Rolle und den aktuellen Saldo. Archivierte Konten SHALL auf Wunsch eingeblendet werden können.

#### Scenario: Gemischte Liste
- **WHEN** Bert ein eigenes Konto hat und als `VIEWER` Annas „GbR-Giro“ sieht
- **THEN** zeigt seine Liste beide Konten, gruppiert unter „Bert privat“ und „GbR Ostsee“, jeweils mit seiner Rolle

### Requirement: Saldo
Der Saldo eines Kontos MUST der Anfangssaldo plus die Summe aller Transaktionsbeträge des Kontos sein, einschließlich Umbuchungen. Er MUST exakt mit zwei Nachkommastellen ohne Rundungsfehler berechnet werden.

#### Scenario: Saldo nach Buchungen
- **WHEN** ein Konto mit Anfangssaldo 100,00 die Transaktionen +0,10, +0,20 und -50,00 hat
- **THEN** beträgt sein Saldo 50,30

### Requirement: Konto bearbeiten und archivieren
Ein `OWNER` SHALL Name, Art, IBAN und Anfangssaldo ändern und das Konto archivieren oder wiederherstellen können. Der Rechtsträger eines Kontos MUST NOT nachträglich geändert werden. Ein archiviertes Konto MUST keine neuen Transaktionen annehmen, seine bestehenden bleiben aber sichtbar. Ein Konto SHALL nur gelöscht werden können, wenn es keine Transaktionen hat.

#### Scenario: Archivieren
- **WHEN** Anna ihr altes Tagesgeldkonto archiviert und danach eine Transaktion darin anlegen will
- **THEN** lehnt das System dies mit der Meldung „Das Konto ist archiviert“ ab, und die bisherigen Transaktionen bleiben abrufbar

#### Scenario: Editor bearbeitet Konto
- **WHEN** Bert als `EDITOR` den Namen des Kontos ändern will
- **THEN** antwortet das System mit HTTP 403
