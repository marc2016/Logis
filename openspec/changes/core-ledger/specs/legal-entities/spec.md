# Spec Delta

## Purpose

Rechtsträger sind die Eigentümer von Konten, z. B. eine Privatperson oder eine GbR. Sie trennen Finanzen steuerlich, damit Auswertungen wie Anlage V oder EÜR pro Eigentümer möglich werden. Sie vergeben selbst keine Rechte.

## ADDED Requirements

### Requirement: Privater Rechtsträger je Benutzer
Jeder Benutzer MUST genau einen privaten Rechtsträger der Art `PRIVATE_PERSON` haben, der beim Anlegen des Benutzers automatisch entsteht und den Namen „<Anzeigename> privat“ trägt. Der private Rechtsträger MUST NOT gelöscht oder einem anderen Benutzer übertragen werden können.

#### Scenario: Neuer Benutzer
- **WHEN** ein Admin den Benutzer „anna“ mit Anzeigename „Anna“ anlegt
- **THEN** besitzt Anna einen Rechtsträger „Anna privat“ der Art `PRIVATE_PERSON`

#### Scenario: Bestehende Benutzer
- **WHEN** Logis mit dieser Version erstmals startet und bereits Benutzer existieren
- **THEN** hat danach jeder bestehende Benutzer seinen privaten Rechtsträger

### Requirement: Weitere Rechtsträger anlegen
Jeder angemeldete Benutzer SHALL weitere Rechtsträger mit Name, Art (`PRIVATE_PERSON`, `GBR`, `GMBH`, `OTHER`) und optional einer Steuernummer anlegen können. Wer einen Rechtsträger anlegt, MUST dessen Verwalter sein. Der Name MUST pro Verwalter eindeutig sein, unabhängig von Groß- und Kleinschreibung.

#### Scenario: GbR anlegen
- **WHEN** Anna einen Rechtsträger „GbR Ostsee“ der Art `GBR` anlegt
- **THEN** erscheint er in Annas Liste ihrer Rechtsträger, und Anna kann Konten darin anlegen

#### Scenario: Doppelter Name
- **WHEN** Anna einen zweiten Rechtsträger „gbr ostsee“ anlegen will
- **THEN** lehnt das System dies mit der Meldung „Ein Rechtsträger mit diesem Namen existiert bereits“ ab

### Requirement: Rechtsträger verwalten
Nur der Verwalter eines Rechtsträgers SHALL dessen Name, Art und Steuernummer ändern und darin Konten anlegen können. Ein Rechtsträger SHALL nur gelöscht werden können, wenn er keine Konten mehr hat und nicht privat ist.

#### Scenario: Umbenennen
- **WHEN** Anna „GbR Ostsee“ in „Ostsee GbR“ umbenennt
- **THEN** zeigen alle Konten dieses Rechtsträgers den neuen Namen

#### Scenario: Löschen mit Konten
- **WHEN** Anna einen Rechtsträger löschen will, der noch Konten hat
- **THEN** lehnt das System dies mit der Meldung „Der Rechtsträger hat noch Konten“ ab

### Requirement: Sichtbarkeit von Rechtsträgern
Ein Benutzer SHALL die Rechtsträger sehen, die er verwaltet. Fremde Rechtsträger SHALL er nur als Name und Art an den Konten sehen, die ihm freigegeben sind. Er MUST NOT sie ändern, Konten in ihnen anlegen oder ihre übrigen Konten sehen können.

#### Scenario: Freigegebenes Konto einer fremden GbR
- **WHEN** Bert als `EDITOR` das Konto „GbR-Giro“ von Annas „GbR Ostsee“ sieht
- **THEN** sieht er „GbR Ostsee“ als Eigentümer dieses Kontos, aber weder die GbR in seiner Liste der Rechtsträger noch andere Konten der GbR

#### Scenario: Konto in fremdem Rechtsträger anlegen
- **WHEN** Bert ein Konto im Rechtsträger „GbR Ostsee“ anlegen will
- **THEN** antwortet das System mit HTTP 404
