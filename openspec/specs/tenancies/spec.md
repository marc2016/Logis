# tenancies Specification

## Purpose

Tenancies record who rents a long-term rental unit, from when to when and for how much. They are the basis for rent payments, rent adjustments and utility statements.

## Requirements

### Requirement: Record a tenancy
A user with at least `EDITOR` as effective role on a unit with the usage „Dauermiete“ SHALL be able to record a tenancy for it. A tenancy has:
- a start date;
- an optional end date, which is the last day of the tenancy;
- at least one tenant with a name;
- a first rent.

The end MUST NOT lie before the start. A unit with another usage MUST be rejected with „Die Einheit ist keine Dauermiete“.

#### Scenario: New tenancy
- **WHEN** Anna records a tenancy for „Whg. 1“ from 01.03.2027 with the tenant „Erika Musterfrau“ and 650,00 € cold rent
- **THEN** the tenancy is listed for „Whg. 1“ with its tenant and start

#### Scenario: Vacation rental unit
- **WHEN** Anna tries to record a tenancy for a unit with the usage „Ferienvermietung“
- **THEN** the system rejects it with „Die Einheit ist keine Dauermiete“

#### Scenario: End before start
- **WHEN** Anna enters an end date before the start date
- **THEN** the system rejects it with „Das Ende liegt vor dem Beginn“

### Requirement: One tenancy per unit at a time
The periods of the tenancies of a unit MUST NOT overlap. A tenancy without an end counts as running indefinitely. An overlapping tenancy MUST be rejected with „Die Einheit ist in diesem Zeitraum bereits vermietet“.

#### Scenario: Overlap
- **WHEN** „Whg. 1“ is rented from 01.03.2027 without an end and Anna records another tenancy from 01.06.2027
- **THEN** the system rejects it with „Die Einheit ist in diesem Zeitraum bereits vermietet“

#### Scenario: Following tenancy
- **WHEN** the first tenancy ends on 31.05.2027 and Anna records the next one from 01.06.2027
- **THEN** both tenancies exist

### Requirement: Tenants
A tenancy SHALL have one or more tenants, each with a name and optionally address, phone and e-mail. Users with at least `EDITOR` SHALL be able to add, change and remove tenants; the last tenant MUST NOT be removed.

After a tenancy has ended, „Mieterdaten löschen“ SHALL delete the names and contact data of its tenants for good, and the tenancy SHALL then show „Mieterdaten gelöscht“. Documents stay and can be deleted one by one.

#### Scenario: Two tenants
- **WHEN** Anna records a tenancy with the tenants „Erika Musterfrau“ and „Max Mustermann“
- **THEN** the tenancy shows both names

#### Scenario: Delete tenant data
- **WHEN** a tenancy ended on 31.05.2027 and Anna chooses „Mieterdaten löschen“ in June 2027
- **THEN** the tenancy keeps its period and rent and shows „Mieterdaten gelöscht“ instead of the names

#### Scenario: Running tenancy
- **WHEN** a tenancy is still running
- **THEN** „Mieterdaten löschen“ is not offered

### Requirement: Rent history
The rent of a tenancy SHALL be a history of entries. Each entry is valid from a date and holds:
- the cold rent;
- the advance payment for utilities;
- optional further items with a label and an amount.

The first entry SHALL be valid from the start of the tenancy. Users with at least `EDITOR` SHALL be able to add an entry valid from a later date, and to change or delete entries; the first entry MUST NOT be deleted. Two entries MUST NOT be valid from the same date. The tenancy SHALL show the entry valid today and its total as the current rent.

#### Scenario: Rent increase
- **WHEN** the rent is 650,00 € from 01.03.2027 and Anna adds an entry with 680,00 € valid from 01.03.2028
- **THEN** the tenancy shows 650,00 € as the current rent until 29.02.2028 and 680,00 € from 01.03.2028, and the history lists both

#### Scenario: Total with further items
- **WHEN** an entry holds 650,00 € cold rent, 150,00 € advance payment and „Stellplatz“ with 40,00 €
- **THEN** its total is 840,00 €

#### Scenario: Same date twice
- **WHEN** Anna adds a second entry valid from 01.03.2027
- **THEN** the system rejects it with „Für dieses Datum gibt es bereits eine Miete“

### Requirement: Deposit
A tenancy SHALL optionally hold the agreed deposit and the date it was received. A deposit without a received date SHALL show as „offen“.

#### Scenario: Deposit received
- **WHEN** Anna enters a deposit of 1.950,00 € and later the received date 05.03.2027
- **THEN** the tenancy shows the deposit first as „offen“ and then as received on 05.03.2027

### Requirement: Change, end and delete a tenancy
Users with at least `EDITOR` on the unit SHALL be able to change a tenancy's dates, deposit and tenants, and to end it by entering an end date. Deleting a tenancy SHALL remove it with its tenants, rent history and documents, after a confirmation.

#### Scenario: End a tenancy
- **WHEN** Anna enters the end date 31.05.2027 for a running tenancy
- **THEN** the tenancy shows as ended from 01.06.2027, and the unit can be rented again from that day

### Requirement: Documents of a tenancy
Users with at least `EDITOR` SHALL be able to attach documents such as the rental contract to a tenancy (PDF or image), to download them and to delete them. `VIEWER`s MUST NOT see the documents.

#### Scenario: Contract attached
- **WHEN** Anna attaches „Mietvertrag.pdf“ to a tenancy
- **THEN** the tenancy lists the file and Anna can download it

### Requirement: Visibility of tenancies
Tenancies MUST be visible exactly to users with an effective role on their unit. `VIEWER`s SHALL see period, tenant names, rent and deposit, but MUST NOT see the tenants' contact data and MUST NOT change anything. A tenancy that does not exist or is not visible MUST show „Mietverhältnis nicht gefunden“.

#### Scenario: Viewer
- **WHEN** Carla has `VIEWER` on „Whg. 1“ and opens its tenancy
- **THEN** she sees the tenant's name and the rent, but neither phone nor e-mail, and no way to edit

#### Scenario: No role
- **WHEN** Bert has no role on „Whg. 1“ and opens the address of its tenancy
- **THEN** he sees „Mietverhältnis nicht gefunden“

### Requirement: List of tenancies and vacancy
The page „Mietverhältnisse“ SHALL list the tenancies of the visible units with unit, tenants, period, current rent and state. The state is one of:
- „künftig“;
- „laufend“;
- „beendet“.

The list SHALL be filterable by property, unit and state, with „laufend“ and „künftig“ shown by default. Every tenancy SHALL open as a page of its own.

The page „Einheiten“ of the module SHALL list every visible unit with the usage „Dauermiete“ with its current tenants and rent, or „Leerstand“ with the day since when it has been vacant.

#### Scenario: Vacancy
- **WHEN** the last tenancy of „Whg. 2“ ended on 31.05.2027 and no other follows
- **THEN** in June 2027 „Whg. 2“ shows „Leerstand seit 01.06.2027“

#### Scenario: Never rented
- **WHEN** „Whg. 3“ never had a tenancy
- **THEN** it shows „Leerstand“ without a date

#### Scenario: Ended tenancies
- **WHEN** Anna chooses the state „beendet“
- **THEN** the list shows the ended tenancies

### Requirement: Module and property deletion
The module „Dauervermietung“ SHALL be a domain module that can be enabled and disabled; while it is disabled, its pages and API MUST NOT be reachable and its data MUST stay unchanged. A property MUST NOT be deleted while a tenancy refers to one of its units; the system SHALL reject this with „Das Objekt hat noch Mietverhältnisse“.

#### Scenario: Module disabled
- **WHEN** the module is disabled and a user calls the tenancies API
- **THEN** the system answers with HTTP 404, and after enabling the module the tenancies are there again

#### Scenario: Property with tenancies
- **WHEN** Anna tries to delete a property whose unit has an ended tenancy
- **THEN** the system rejects it with „Das Objekt hat noch Mietverhältnisse“
