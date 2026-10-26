# Design

## Context

Das Gerüst aus `project-scaffold` steht. Spring Modulith hat die Module `core`, `shared` und `vacationrental`. `core` darf nur `shared` nutzen und stellt Fachmodulen die Named Interface `modules` bereit. Login und Rollen laufen über den `SessionAuthenticationFilter`. Im Controller steht der angemeldete Benutzer als `LogisPrincipal(userId, username)` zur Verfügung. Fehler werden über `ApiException` als `ProblemDetail` mit deutscher Meldung ausgeliefert. Die Datenbank hat ein Schema pro Bereich, die Migrationen liegen unter `db/migration/<bereich>` mit Zeitstempel-Versionen.

Benutzer entstehen an zwei Stellen: in `UserAdminService.create` und in `InitialAdminRunner`. Im Frontend fügen Kernbereiche ihre Navigation bisher fest in `AppSidebar` ein, Module über die Registry. Formulare nutzen `FormError` und `PageHeader`.

Motivation und Leitentscheidungen stehen in proposal.md, die Anforderungen in `specs/`.

## Goals / Non-Goals

**Goals:**
- Ein Freigabe-Mechanismus, den Fachmodule ohne Kopie wiederverwenden. Das erste Modul, das ihn nutzt, ist die Ferienvermietung mit ihren Ferienwohnungen.
- Jede fachliche Abfrage filtert per Konstruktion nach Freigaben. Ein vergessener Filter soll nicht zu einem Datenleck führen.
- Geldbeträge sind von der Datenbank bis zur Anzeige exakt, ohne Gleitkomma.

**Non-Goals:**
- Import, Duplikaterkennung, Abgleich mit Bankumsätzen. Das kommt mit `core-bank-import`.
- Belege und Anhänge, Berichte, Budgets, Anteile bzw. Splits.
- Abgeschlossene Zeiträume und ein Änderungsprotokoll.
- Andere Währungen als EUR.
- Kategorien, die einzelne Benutzer für sich anlegen.
- Kennzahlen im Dashboard. Die „Übersicht“ bleibt vorerst ein Platzhalter.

## Decisions

### D1: Packages und Modulgrenzen

```
app.logis.core
  sharing/              Freigaben (Named Interface "sharing" fuer Module)
    ObjectShare, ShareRole, SharingService, ShareableType, SharesController
  ledger/
    LegalEntity..., Account..., Category..., Transaction..., Transfer...
  users/
    UserDirectoryController (Benutzersuche), UserCreatedEvent
```

- `core.sharing` wird per `@NamedInterface("sharing")` für Module freigegeben. `core.ledger` bleibt intern. Module bekommen erst dann eine Ledger-API, wenn sie sie brauchen, etwa für die Zuordnung von Zahlungen in der Ferienvermietung.
- *Alternative:* ein eigenes Modulith-Modul `ledger` neben `core`. Das wäre eine sauberere Trennung, aber Rechtsträger und private Rechtsträger hängen eng an Benutzern, und die Roadmap nennt das Thema bewusst „core“. Ein späteres Herauslösen ist möglich, weil `ledger` nur über `users`- und `sharing`-APIs auf den Rest des Kerns zugreift.

### D2: Freigaben als generische Tabelle

```
core.object_shares
  id, object_type VARCHAR(64), object_id BIGINT, user_id -> users,
  role (OWNER|EDITOR|VIEWER), created_at
  UNIQUE (object_type, object_id, user_id)
  INDEX (user_id, object_type)
```

- Der `object_type` ist ein stabiler String wie `"account"` und später `"vacationrental.property"`. Jeder Typ wird als `ShareableType`-Bean registriert. Er liefert Typ-ID, Anzeigename und die Prüfung „existiert das Objekt?“. Der generische `SharesController` (`/api/shares/{type}/{id}`) kann damit für alle Typen arbeiten.
- `SharingService` stellt bereit:
  - `grantOwner`
  - `requireRole(type, id, userId, minRole)`: liefert 404, wenn keine Freigabe besteht, und 403, wenn die Rolle zu niedrig ist
  - `accessibleIds(type, userId, minRole)`
  - `invite`, `changeRole`, `revoke` und `list`
- Die Invariante „mindestens ein Owner“ wird in einer Transaktion mit `SELECT … FOR UPDATE` auf den Owner-Zeilen des Objekts geprüft. Das ist dasselbe Muster wie bei „mindestens ein aktiver Admin“.
- **Abfragen filtern per Konstruktion:** Services für freigebbare Objekte holen zuerst die freigegebenen IDs (`SharingService.rolesFor` bzw. `accessibleIds`) und fragen dann nur diese IDs ab. Einzelzugriffe laufen immer über `requireRole`.
  - *Umgesetzt statt des ursprünglich geplanten JPQL-Subselects:* Die Rolle je Objekt wird für die Anzeige ohnehin gebraucht, und die Mengen sind klein. Ein ArchUnit-Test im `ModularityTest`-Stil stellt sicher, dass Controller in `core.ledger` nicht direkt `JpaRepository.findById` aufrufen, sondern nur die Service-Methoden mit Rechteprüfung.
- Beim Löschen eines Objekts werden seine Freigaben im selben Service-Aufruf gelöscht. Einen Fremdschlüssel gibt es wegen des generischen `object_id` nicht.
- *Alternative:* eine Freigabetabelle pro Objekttyp (`account_shares` usw.). Das bringt echte Fremdschlüssel, aber jedes Modul müsste die Logik zum Einladen, zum Owner-Schutz und für die UI neu bauen. Die generische Tabelle passt zur Entscheidung „ein Freigabemodell für alles“.

### D3: Benutzersuche

`GET /api/users/search?q=` liefert für jeden angemeldeten Benutzer höchstens 10 aktive Benutzer als `{id, username, displayName}`. Gesucht wird per `ILIKE` in Benutzername und Anzeigename. Ist `q` kürzer als 2 Zeichen, kommt eine leere Liste zurück. Das ist bewusst die einzige Stelle, an der Nicht-Admins andere Benutzer sehen.

### D4: Rechtsträger und der private Rechtsträger

```
core.legal_entities
  id, name VARCHAR(128), kind (PRIVATE_PERSON|GBR|GMBH|OTHER), tax_number VARCHAR(32) NULL,
  manager_user_id -> users, personal BOOLEAN, created_at
  UNIQUE (manager_user_id, lower(name))
  UNIQUE (manager_user_id) WHERE personal   -- genau ein privater je Benutzer
```

- Rechtsträger sind **kein** freigebbares Objekt. Sichtbar sind sie über `manager_user_id = me`. Fremde Rechtsträger erscheinen nur als `{id, name, kind}` eingebettet in die Kontodaten.
- **Anlegen des privaten Rechtsträgers:** `UserAdminService.create` und `InitialAdminRunner` veröffentlichen ein `UserCreatedEvent`. Ein `@EventListener` in `core.ledger` legt synchron in derselben Transaktion „<Anzeigename> privat“ an. Bestehende Benutzer bekommen ihn per Migration (`INSERT … SELECT` für alle Benutzer ohne privaten Rechtsträger).
- Der Name des privaten Rechtsträgers ist danach unabhängig vom Anzeigenamen und kann umbenannt werden.

### D5: Konten

```
core.accounts
  id, legal_entity_id -> legal_entities, name VARCHAR(128),
  kind (CHECKING|SAVINGS|CASH|CREDIT_CARD|OTHER), currency CHAR(3) DEFAULT 'EUR' CHECK = 'EUR',
  iban VARCHAR(34) NULL, opening_balance NUMERIC(14,2) DEFAULT 0, archived BOOLEAN, created_at
```

- Ein Konto darf nur im eigenen Rechtsträger angelegt werden (`manager_user_id = me`, sonst 404). Dabei wird `grantOwner("account", id, me)` ausgeführt.
- Der **Saldo** wird nicht gespeichert, sondern per Abfrage berechnet: `opening_balance + COALESCE(SUM(amount), 0)`. In der Kontenliste geschieht das in einer einzigen gruppierten Abfrage. Gespeicherte Salden würden bei Bearbeitung, Löschen und Umbuchungen auseinanderlaufen, und die Mengen sind klein.
- **IBAN:** Leerzeichen werden entfernt, Großbuchstaben erzwungen, dann Länge 15–34 und Mod-97 = 1 geprüft. Eine Länderliste pflegen wir nicht.

### D6: Transaktionen und Umbuchungen

```
core.transactions
  id, account_id -> accounts, booking_date DATE, amount NUMERIC(14,2) CHECK <> 0,
  category_id -> categories NULL, counterparty VARCHAR(255), purpose VARCHAR(500), note TEXT,
  transfer_id UUID NULL, version INTEGER,
  created_by -> users, created_at, updated_by -> users, updated_at
  INDEX (account_id, booking_date DESC), INDEX (transfer_id), INDEX (category_id)
  CHECK (transfer_id IS NULL OR category_id IS NULL)
```

- **Optimistisches Sperren** über `@Version`: Der Client schickt `version` mit, und bei einer Abweichung antwortet das System mit 409 und der Meldung „Die Transaktion wurde inzwischen geändert“.
- **Umbuchungen** sind zwei Zeilen mit gleicher `transfer_id`. Eigene Endpunkte `POST/PUT/DELETE /api/transfers/{transferId}` ändern immer beide Seiten in einer Transaktion. Die normalen Transaktions-Endpunkte lehnen Änderungen an Umbuchungszeilen mit dem Hinweis auf die Umbuchung ab, Löschen ist die einzige Ausnahme und löscht dann beide Seiten.
- **Liste:** `GET /api/transactions?accountId&from&to&categoryId&uncategorized&q&page&size` liefert `{items, page, totalItems, income, expense}`.
  - Gefiltert wird über eine JPA Specification, die immer mit `account_id IN (accessibleIds)` beginnt.
  - `categoryId` schließt die Unterkategorien ein.
  - Die Summen kommen aus einer zweiten Abfrage mit derselben Specification: `SUM(CASE WHEN amount > 0 …)`, nur über Zeilen mit `transfer_id IS NULL`.
- Bearbeiten erfordert `EDITOR` auf dem bisherigen und auf dem neuen Konto, ebenso das Umbuchen auf Quell- und Zielkonto.

### D7: Geldbeträge in der API

- Im Backend sind Beträge durchgehend `BigDecimal` mit Scale 2. Eingaben mit mehr als 2 Nachkommastellen werden abgelehnt (400), nicht gerundet.
- **In JSON sind Beträge Strings** (`"-84.90"`), damit JavaScript nie Gleitkomma rechnet. Umgesetzt ist das über `@JsonFormat(shape = STRING)` an den DTO-Feldern.
- Das Frontend hat `lib/money.ts`:
  - `formatEUR("-84.90")` liefert „-84,90 €“ (über `Intl.NumberFormat("de-DE")`).
  - `parseAmount("1.234,56")` liefert `"1234.56"` und arbeitet rein auf Strings.
  - Gerechnet wird im Frontend nicht. Salden und Summen kommen vom Server.

### D8: Kategorien

```
core.categories
  id, parent_id -> categories NULL, kind (INCOME|EXPENSE), name VARCHAR(64),
  module_key VARCHAR(128) NULL UNIQUE,   -- z. B. "vacationrental.cleaning"
  module_id VARCHAR(64) NULL, archived BOOLEAN, sort_order INTEGER
  UNIQUE (kind, COALESCE(parent_id, 0), lower(name))
```

- Die zweite Ebene wird im Service geprüft: Der `parent` darf selbst keinen `parent` haben, und die Art wird von der Hauptkategorie übernommen.
- Die **Standardkategorien** kommen per Flyway-Seed (Spec `categories`).
- **Modulkategorien:** Module registrieren `CategoryContribution`-Beans mit `moduleKey`, Art, Name und optional dem `moduleKey` der Hauptkategorie. Ein Startlauf (`ApplicationRunner`) legt fehlende Kategorien anhand von `module_key` an und benennt nichts um. Admins können diese Kategorien nur archivieren. Die API zur Auswahl blendet Kategorien deaktivierter Module aus, denn deren `module_id` fehlt in den `ModuleDescriptor`-Beans. Die Schnittstelle liegt in der Named Interface `modules`. In diesem Change nutzt sie nur ein Test-Bean.
- Die Admin-Endpunkte liegen unter `/api/admin/categories`. Für die Auswahl beim Erfassen gibt es `GET /api/categories`.

### D9: Frontend

```
src/core/ledger/
  money.ts (unter lib/), api.ts (Typen + Query-Hooks)
  accounts/AccountsPage.tsx          /finanzen/konten       Liste nach Rechtstraeger, Saldo, Rolle
  accounts/AccountDetailPage.tsx     /finanzen/konten/:id   Kopf mit Saldo + Aktionen, Transaktionsliste
  accounts/AccountDialog.tsx         anlegen/bearbeiten
  transactions/TransactionsPage.tsx  /finanzen/transaktionen   alle Konten, Filterleiste, Summen
  transactions/TransactionTable.tsx  (wiederverwendet in Kontodetail)
  transactions/TransactionDialog.tsx Einnahme/Ausgabe-Umschalter, Betrag, Datum, Kategorie, ...
  transactions/TransferDialog.tsx
  legal-entities/LegalEntitiesPage.tsx /finanzen/eigentuemer
src/core/admin/categories/CategoriesPage.tsx  /einstellungen/kategorien
src/components/ShareDialog.tsx       generisch: (objectType, objectId, canManage)
src/components/CategorySelect.tsx    gruppiert nach Haupt-/Unterkategorie
```

- **Sidebar:** Unter „Allgemein“ kommt die neue Kerngruppe „Finanzen“ mit Konten, Transaktionen und Eigentümern. „Einstellungen“ bekommt für Admins den Eintrag „Kategorien“.
- **Datum:** natives `<input type="date">`. Das ist ohne zusätzliche Abhängigkeit barrierearm und auf Mobilgeräten ausreichend.
- **Benutzerwahl im ShareDialog:** shadcn `Command` in einem `Popover` als Combobox, gespeist aus `/api/users/search`.
- Neue shadcn-Komponenten: `popover`, `command`, `textarea`, `tabs`, `toggle-group`, `checkbox`.
- Nach Änderungen invalidiert TanStack Query gezielt `['accounts']`, `['transactions', filter]` und `['shares', type, id]`.

### D10: Tests

- **Backend:** Integrationstests pro Spec-Datei.
  - Schwerpunkte: Rechte und Datenlecks. Dazu gehört je Endpunkt ein Test „fremder Benutzer → 404“ und „VIEWER → 403“.
  - Weitere Schwerpunkte: Saldo- und Summen-Exaktheit, Umbuchungen, optimistisches Sperren, Owner-Invariante, Migration der privaten Rechtsträger.
  - Unit-Tests für IBAN und Betragsprüfung. Der Modulith-Test prüft zusätzlich die Named Interface `sharing`.
- **Frontend:**
  - Vitest für `money.ts` (Formatieren und Parsen, Randfälle).
  - Komponententests für TransactionDialog (Vorzeichen, Fehlermeldungen), ShareDialog (Einladen, letzter Owner) und Kontenliste (Gruppierung).

## Risks / Trade-offs

- [Generische Freigabetabelle ohne Fremdschlüssel auf das Objekt] → Verwaiste Freigaben sind möglich, wenn ein Objekt am Service vorbei gelöscht wird. Dagegen helfen Löschen ausschließlich über Services und ein Test, der nach jedem Löschfall keine Freigaben ohne Objekt mehr findet.
- [Saldo per Abfrage statt gespeichert] → Das ist bei vielen Tausend Transaktionen pro Konto langsamer. Der Index `(account_id, booking_date)` und kleine Datenmengen machen das unkritisch. Ein materialisierter Saldo kann später ohne API-Änderung kommen.
- [Deaktivierter einziger Owner] → Das Konto ist dann für niemanden mehr verwaltbar. Die Lösung ist, den Benutzer zu reaktivieren. Ein Admin-Werkzeug zum Übertragen gibt es noch nicht, das README beschreibt den SQL-Weg.
- [Ein gemeinsamer Kategorienbaum] → Benutzer können keine eigenen Unterkategorien anlegen und müssen einen Admin fragen. Das ist eine bewusste Annahme (siehe proposal.md). Später ist das als Erweiterung möglich.
- [Beträge als JSON-Strings] → Das ist ungewohnt für API-Nutzer, verhindert aber Rundungsfehler. Es ist dokumentiert und gilt für alle Geld-Felder.

## Migration Plan

Neue Flyway-Migrationen im Schema `core`. Sie legen die Tabellen an, säen die Standardkategorien und fügen private Rechtsträger für bestehende Benutzer ein. Sie laufen beim nächsten Start automatisch. Zurück geht es über ein Backup aus der Zeit vor dem Update (README: Restore), weil Flyway-Migrationen nicht automatisch zurückgerollt werden.

## Open Questions

- **Transaktionen, die einem Konto und einem fremden Objekt zugeordnet sind.** Vorschlag: Lesen darf auch, wer nur Rechte am Objekt hat. Das betrifft erst `realestate-minimal`, weil es dann Objekte gibt, und ändert dieses Change nicht.
