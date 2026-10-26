# Proposal

## Why

Logis hat ein lauffähiges Gerüst aus Anmeldung, Benutzern und Modulen, aber noch keine fachlichen Daten. Alle späteren Module bauen auf einem gemeinsamen Finanzkern auf: Ferienvermietung, Immobilien, Beteiligungen, Bank-Import und Berichte. Dieses Change schafft ihn, und zwar schlank. Es gibt Eigentümer (Rechtsträger), Konten, Kategorien und von Hand erfasste Transaktionen, außerdem das **Freigabemodell pro Objekt**, das ab jetzt jede fachliche Abfrage schützt.

Leitentscheidungen aus der Erkundung:

- **Reihenfolge der Roadmap:** `core-ledger` (dieses Change), dann `realestate-minimal`, dann `vacation-rental` mit manueller Erfassung, danach `core-bank-import`. Der Import ist für die manuelle Ferienvermietung nicht nötig und wird später besser, wenn es schon Buchungen gibt, denen er Zahlungen zuordnen kann.
- **Rechtsträger:** Es gibt die eigenen privaten Finanzen und daneben Eigentümer, die steuerlich getrennt werden müssen (z. B. eine GbR). Jedes Konto gehört genau einem Rechtsträger. Jeder Benutzer hat automatisch einen eigenen privaten Rechtsträger.
- **Rechtsträger geben keine Rechte:** Wer einen Rechtsträger anlegt, verwaltet ihn. Andere sehen nur seinen Namen, und zwar an den Konten, die für sie freigegeben sind.
- **Freigaben gibt es nur pro Objekt.** Das erste freigebbare Objekt ist das Konto mit den Rollen `OWNER`, `EDITOR` und `VIEWER`. Transaktionen erben die Rechte des Kontos. Eingeladen werden nur bestehende Benutzer. Die Systemrolle `ADMIN` gibt keinen Zugriff auf fachliche Daten.
- **Keine doppelte Buchführung:** Ein Betrag ist positiv (Einnahme) oder negativ (Ausgabe), in EUR.

## What Changes

- **Freigabe-Mechanismus im Kern:** allgemein, für beliebige Objekttypen. Module können ihn später für eigene Objekte nutzen, etwa für Ferienwohnungen.
  - Owner laden bestehende Benutzer als `VIEWER` oder `EDITOR` ein, ändern Rollen und entziehen Freigaben.
  - Es bleibt immer mindestens ein Owner.
  - Eine Benutzersuche zum Einladen liefert nur Benutzername und Anzeigename.
- **Rechtsträger:**
  - Ein privater Rechtsträger pro Benutzer, automatisch angelegt.
  - Weitere Rechtsträger lassen sich anlegen und umbenennen (Name, Art, Steuernummer).
- **Konten:** anlegen, bearbeiten und archivieren.
  - Ein Konto gehört zu einem Rechtsträger und hat Art, IBAN, Anfangssaldo und einen laufenden Saldo.
  - Wer ein Konto anlegt, wird dessen Owner.
- **Transaktionen:**
  - Von Hand erfassen, bearbeiten und löschen, mit Datum, Betrag, Kategorie, Gegenpartei, Verwendungszweck und Notiz.
  - Filtern nach Konto, Zeitraum, Kategorie und Text, mit Summen für Einnahmen und Ausgaben.
- **Umbuchungen zwischen eigenen Konten:** zwei verknüpfte Buchungen, die die Salden ändern, aber nicht als Einnahme oder Ausgabe zählen.
- **Kategorien:**
  - Ein gemeinsamer Kategorienbaum mit zwei Ebenen, getrennt nach Einnahmen und Ausgaben, mit deutschen Standardkategorien.
  - Admins pflegen ihn.
  - Module können später eigene Kategorien beisteuern.
- **Oberfläche:**
  - Neue Sidebar-Gruppe „Finanzen“ mit Konten, Transaktionen und Eigentümern.
  - Unter „Einstellungen“ der neue Eintrag „Kategorien“ für Admins.
  - Ein Freigabe-Dialog am Konto.

## Capabilities

### New Capabilities

- `object-sharing`: Freigabe einzelner Objekte an bestehende Benutzer mit den Rollen `OWNER`, `EDITOR` und `VIEWER`, dazu die Benutzersuche zum Einladen.
- `legal-entities`: Rechtsträger (Eigentümer) für die steuerliche Trennung, mit einem privaten Rechtsträger pro Benutzer.
- `accounts`: Konten mit Zugehörigkeit zu einem Rechtsträger, Saldo und Archivierung.
- `transactions`: Manuelle Transaktionen und Umbuchungen, Filter und Summen.
- `categories`: Gemeinsamer Kategorienbaum mit Pflege durch Admins.

### Modified Capabilities

Keine. Die neuen Menüeinträge erfüllen die bestehenden Anforderungen von `app-shell`, ohne sie zu ändern.

## Impact

- **Backend:** Neue Packages `app.logis.core.sharing` (mit öffentlicher API für Module) und `app.logis.core.ledger` (Rechtsträger, Konten, Kategorien, Transaktionen). Neue Flyway-Migrationen im Schema `core`.
- **Frontend:** Neue Seiten unter `src/core/ledger/` und `src/core/admin/categories/`, ein Freigabe-Dialog als wiederverwendbare Komponente und eine neue Sidebar-Gruppe.
- **Bestehende Benutzer:** Eine Migration legt für sie nachträglich einen privaten Rechtsträger an.
- **Annahmen** (in der Erkundung vorgeschlagen, nicht ausdrücklich bestätigt; leicht änderbar):
  - Ein gemeinsamer Kategorienbaum für alle statt Kategorien pro Rechtsträger.
  - Nur EUR. Ein Konto hat trotzdem ein Währungsfeld.
  - Transaktionen sind frei bearbeitbar und löschbar. Abgeschlossene Zeiträume und ein Änderungsprotokoll kommen später.
  - Belege und Anhänge kommen nicht mit diesem Change.
