# Tasks

## 1. Datenbank

- [x] 1.1 Migration für `core.object_shares`, `core.legal_entities`, `core.accounts`, `core.categories` und `core.transactions` nach design.md D2, D4, D5, D6 und D8 anlegen, einschließlich Indizes, Unique-Constraints und Checks. Prüfen: Das Backend startet im Profil `dev`, und `\d` in psql zeigt alle Tabellen mit ihren Constraints.
- [x] 1.2 Seed-Migration für die Standardkategorien laut Spec `categories` anlegen. Prüfen: Ein Integrationstest bestätigt, dass nach dem Start alle geforderten Haupt- und Unterkategorien mit der richtigen Art existieren.
- [x] 1.3 Migration anlegen, die bestehenden Benutzern ihren privaten Rechtsträger gibt. Prüfen: Ein Integrationstest legt vorher Benutzer ohne Rechtsträger an, führt die Migration aus (Flyway `target`) und findet danach je Benutzer genau einen privaten Rechtsträger.

## 2. Freigaben (Backend)

- [x] 2.1 `ObjectShare`, `ShareRole`, `ShareableType` und `SharingService` (`grantOwner`, `requireRole`, `accessibleIds`, `invite`, `changeRole`, `revoke`, `list`) in `core.sharing` mit `@NamedInterface("sharing")` umsetzen. Prüfen: Integrationstests bestätigen 404 ohne Freigabe, 403 bei zu niedriger Rolle und die Rollenhierarchie VIEWER < EDITOR < OWNER. Der `ModularityTest` bleibt grün.
- [x] 2.2 `SharesController` (`GET/POST /api/shares/{type}/{id}`, `PATCH/DELETE /api/shares/{type}/{id}/{userId}`) umsetzen. Prüfen: Integrationstests decken Einladen (sofort wirksam), deaktivierten bzw. unbekannten Benutzer („Benutzer nicht gefunden“), Rollenwechsel statt Duplikat, Entziehen, eigene Freigabe aufgeben und „Es muss mindestens einen Owner geben“ ab, außerdem dass ein `EDITOR` beim Einladen 403 erhält.
- [x] 2.3 Benutzersuche `GET /api/users/search` nach D3 umsetzen. Prüfen: Integrationstests bestätigen höchstens 10 Treffer, keine deaktivierten Benutzer, eine leere Liste bei weniger als 2 Zeichen und genau die Felder `id`, `username` und `displayName`.

## 3. Rechtsträger (Backend)

- [x] 3.1 `UserCreatedEvent` in `UserAdminService.create` und `InitialAdminRunner` veröffentlichen. In `core.ledger` legt ein Listener „<Anzeigename> privat“ an. Prüfen: Ein Integrationstest bestätigt, dass ein neu angelegter Benutzer genau einen privaten Rechtsträger der Art `PRIVATE_PERSON` hat.
- [x] 3.2 Endpunkte `/api/legal-entities` umsetzen: Liste der eigenen Rechtsträger, anlegen, ändern, löschen. Prüfen: Integrationstests decken ab, dass ein doppelter Name abgelehnt wird (ohne Beachtung der Groß- und Kleinschreibung), dass ein Rechtsträger mit Konten nicht gelöscht werden kann, dass der private nicht löschbar ist und dass ein fremder Rechtsträger 404 liefert.

## 4. Konten (Backend)

- [x] 4.1 IBAN-Prüfung (Normalisierung, Länge, Mod-97) und Betragsprüfung (Scale ≤ 2, ≠ 0, Maximalbetrag) umsetzen. Prüfen: Unit-Tests mit gültigen und ungültigen IBANs (DE, AT, falsche Prüfziffer) und Beträgen.
- [x] 4.2 Endpunkte `/api/accounts` umsetzen: Liste mit Rechtsträger, eigener Rolle und Saldo in einer gruppierten Abfrage, anlegen nur im eigenen Rechtsträger mit `grantOwner`, ändern, archivieren, wiederherstellen und löschen nur ohne Transaktionen, inklusive Löschen der Freigaben. Prüfen: Integrationstests decken ab:
  - Saldo 100,00 + 0,10 + 0,20 − 50,00 = 50,30
  - `EDITOR` ändert Konto → 403
  - Fremdes Konto → 404, auch für Admins
  - Konto in fremdem Rechtsträger → 404
  - Gemischte Liste mit fremdem Rechtsträger als `{id, name, kind}`
  - Nach dem Löschen keine verwaisten Freigaben
- [x] 4.3 ArchUnit-Regel hinzufügen: Controller in `core.ledger` greifen nicht direkt auf Repositories zu. Prüfen: Der Test ist grün und schlägt fehl, sobald testweise ein Controller ein Repository injiziert.

## 5. Kategorien (Backend)

- [x] 5.1 `GET /api/categories` (aktiver Baum, ohne Kategorien deaktivierter Module) und Admin-Endpunkte `/api/admin/categories` umsetzen: anlegen, umbenennen, verschieben, archivieren, wiederherstellen, löschen. Prüfen: Integrationstests decken ab:
  - `USER` → 403
  - Dritte Ebene abgelehnt
  - Unterkategorie übernimmt die Art der Hauptkategorie
  - Doppelter Name abgelehnt
  - Löschen einer verwendeten Kategorie mit der Meldung „Die Kategorie wird verwendet und kann nur archiviert werden“
  - Archivieren und Wiederherstellen
- [x] 5.2 `CategoryContribution` in der Named Interface `modules` sowie den Startlauf nach D8 umsetzen. Prüfen: Ein Integrationstest mit einem Test-Bean legt eine Modulkategorie an. Ein erneuter Start dupliziert nichts. Umbenennen bzw. Löschen ergibt „Diese Kategorie wird von einem Modul verwaltet“, Archivieren ist erlaubt. Bei deaktiviertem Modul fehlt die Kategorie in `GET /api/categories`.

## 6. Transaktionen (Backend)

- [x] 6.1 `POST/PUT/DELETE /api/transactions` mit Rechteprüfung (EDITOR auf altem und neuem Konto), Ablehnung bei archiviertem Konto, nur aktiven Kategorien und `@Version` umsetzen. Prüfen: Integrationstests decken ab:
  - Erfassen ändert den Saldo
  - 0,00 und 12,345 abgelehnt
  - `VIEWER` → 403
  - Fremdes Konto → 404
  - Archiviertes Konto: „Das Konto ist archiviert“
  - Archivierte Kategorie: „Die Kategorie ist nicht verfügbar“
  - Veraltete `version` → 409 „Die Transaktion wurde inzwischen geändert“
  - Bestehende Zuordnung bleibt nach dem Archivieren der Kategorie erhalten
- [x] 6.2 `GET /api/transactions` mit Specification-Filtern (Konto, Zeitraum, Kategorie inklusive Unterkategorien, ohne Kategorie, Suchtext), Paginierung (max. 100) und Summen ohne Umbuchungen umsetzen. Prüfen: Integrationstests decken ab, dass die Summen über alle Seiten exakt sind, dass nur freigegebene Konten erscheinen, dass der Filter auf eine Hauptkategorie die Unterkategorien einschließt, dass „ohne Kategorie“ Umbuchungen ausschließt und dass die Sortierung nach Datum absteigend ist.
- [x] 6.3 `POST/PUT/DELETE /api/transfers` nach D6 umsetzen. Normale Endpunkte lehnen Änderungen an Umbuchungszeilen ab, Löschen entfernt beide Seiten. Prüfen: Integrationstests decken ab, dass sich beide Salden ändern und die Summen gleich bleiben, dass eine Änderung beide Seiten betrifft, dass das Löschen einer Seite beide löscht und dass ein Zielkonto mit nur `VIEWER` 403 ergibt.

## 7. Frontend-Grundlagen

- [x] 7.1 `lib/money.ts` (`formatEUR`, `parseAmount`) umsetzen. Prüfen: Vitest mit „1.234,56“, „-84,9“, „0,005“ (abgelehnt), „abc“ (abgelehnt) und großen Beträgen, ohne Gleitkomma-Abweichungen.
- [x] 7.2 Die shadcn-Komponenten `popover`, `command`, `textarea`, `tabs`, `toggle-group` und `checkbox` hinzufügen. Prüfen: `npm run build` ist grün.
- [x] 7.3 `core/ledger/api.ts` mit Typen (Beträge als String) und TanStack-Query-Hooks für Konten, Rechtsträger, Kategorien, Transaktionen, Umbuchungen und Freigaben anlegen. Prüfen: `npm run build` ist grün (Typprüfung).
- [x] 7.4 Die Sidebar um die Kerngruppe „Finanzen“ (Konten, Transaktionen, Eigentümer) und den Admin-Eintrag „Kategorien“ erweitern. Die Routen kommen nach D9 dazu. Prüfen: Komponententest „Finanzen“ ist sichtbar und „Kategorien“ nur für Admins.

## 8. Frontend-Seiten

- [x] 8.1 `ShareDialog` (generisch) und `CategorySelect` umsetzen. Prüfen: Komponententests decken ab, dass das Einladen über die Suche die Freigabe anlegt, dass beim letzten Owner die Meldung erscheint und dass ein `VIEWER` nur die Liste ohne Aktionen sieht.
- [x] 8.2 Eigentümer-Seite (`/finanzen/eigentuemer`) umsetzen: Liste, anlegen, umbenennen, löschen mit Fehlermeldungen. Prüfen: Komponententests decken Anlegen und die Meldung bei doppeltem Namen ab.
- [x] 8.3 Kontenliste (`/finanzen/konten`) mit Gruppierung nach Rechtsträger, Rolle, Saldo, „Archivierte anzeigen“ und `AccountDialog` umsetzen. Prüfen: Komponententests decken Gruppierung, Saldoformat „1.250,00 €“ und die Fehlermeldung bei ungültiger IBAN ab.
- [x] 8.4 `TransactionTable`, `TransactionDialog` (Umschalter Einnahme/Ausgabe, Betrag, Datum, Kategorie, Gegenpartei, Zweck, Notiz) und `TransferDialog` umsetzen. Prüfen: Komponententests decken ab, dass „Ausgabe“ mit 84,90 den Wert „-84.90“ sendet, dass die 409-Meldung angezeigt wird und dass eine Umbuchung in der Tabelle als solche markiert ist.
- [x] 8.5 Kontodetail (`/finanzen/konten/:id`) umsetzen: Kopf mit Saldo, Rolle und Aktionen (Bearbeiten, Freigeben, Archivieren je nach Rolle) sowie die Transaktionsliste des Kontos. Prüfen: Komponententests bestätigen, dass ein `VIEWER` keine Schaltflächen zum Erfassen oder Bearbeiten sieht und ein unbekanntes Konto „Seite nicht gefunden“ zeigt.
- [x] 8.6 Transaktionsseite (`/finanzen/transaktionen`) umsetzen: Filterleiste (Konto, Zeitraum, Kategorie, ohne Kategorie, Suche), Summen der Einnahmen und Ausgaben, Paginierung. Prüfen: Ein Komponententest bestätigt, dass die Filter die richtigen Query-Parameter senden und die Summen angezeigt werden.
- [x] 8.7 Kategorien-Verwaltung (`/einstellungen/kategorien`) umsetzen: Baum nach Art, anlegen, umbenennen, verschieben, archivieren und wiederherstellen, löschen mit Meldung. Modulkategorien sind gekennzeichnet und nur archivierbar. Prüfen: Komponententests decken Anlegen und beide Fehlermeldungen ab.

## 9. Abnahme

- [x] 9.1 Gesamte Testsuiten ausführen: `./mvnw verify` und `npm test && npm run build`. Prüfen: Alles grün.
- [x] 9.2 Manueller Durchlauf mit `./start.sh` und zwei Benutzern (Admin und Bert):
  1. Rechtsträger „GbR Ostsee“ anlegen, darin ein Konto anlegen und Bert als `VIEWER` einladen.
  2. Prüfen, dass Bert nur dieses eine GbR-Konto sieht und nichts erfassen kann.
  3. Bert zum `EDITOR` machen und prüfen, dass er jetzt erfassen kann.
  4. Eine Umbuchung zwischen eigenen Konten durchführen.
  5. Den Monatsfilter mit Summen prüfen.

  Prüfen: Alle Schritte verhalten sich wie in den Specs beschrieben.
- [x] 9.3 README ergänzen: Abschnitt „Finanzen und Freigaben“ (Rechtsträger, Konten, Rollen) und einen SQL-Notfallweg zum Übertragen eines Kontos, dessen einziger Owner deaktiviert ist. Prüfen: Den SQL-Weg einmal am Dev-Stack ausführen.
