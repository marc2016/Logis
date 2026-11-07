# Spec Delta

## MODIFIED Requirements

### Requirement: List and filter transactions
A user SHALL be able to list the transactions of all accounts shared with them or of a single account, together with the transactions attributed to properties and units they can see. Transactions attributed to a property are visible through it only to users with a role on the property; transactions attributed to a unit are visible through it to users with an effective role on the unit. They are sorted by date descending and retrievable page by page with at most 100 entries each. The list SHALL be filterable by account, property, unit, period, category (including its subcategories), "uncategorized", and a search text in counterparty, reference and note; the note MUST only be searched in transactions whose account the user can see. Filtering by property SHALL include the transactions attributed to the property itself and to those of its units the user can see. For each filter, the system MUST return the total of income and the total of expenses, excluding transfers and across all pages.

#### Scenario: Monthly overview
- **WHEN** Anna retrieves the transactions of all accounts for October 2026
- **THEN** they receive October's transactions and the totals of income and expenses for that month

#### Scenario: Other people's accounts stay hidden
- **WHEN** Bert retrieves the transactions of all accounts
- **THEN** the list contains only transactions from accounts shared with them and transactions attributed to properties or units they can see

#### Scenario: Find uncategorized
- **WHEN** Anna filters by "uncategorized"
- **THEN** they receive only transactions without a category and without transfers

#### Scenario: Filter by unit
- **WHEN** Carla, as `EDITOR` of the unit "Whg. 2" without access to the account "GbR-Giro", filters by "Whg. 2"
- **THEN** they receive the transactions from "GbR-Giro" attributed to "Whg. 2" and the totals of their income and expenses

#### Scenario: Property-wide transactions stay hidden from unit-only users
- **WHEN** a transaction "Grundsteuer" is attributed to the property itself and Carla only has a share on the unit "Whg. 2"
- **THEN** the transaction does not appear in Carla's list

## ADDED Requirements

### Requirement: Attribute a transaction to a property or unit
A transaction SHALL be attributable to at most one target: either a property or one of its units. Setting, changing or removing the target MUST require at least `EDITOR` rights on the account and at least `EDITOR` as effective role on the new target. Transfers MUST NOT be attributable. Only active properties and units MUST be selectable as a new target; existing attributions MUST stay when a target is archived.

#### Scenario: Attribute a booking payment
- **WHEN** Anna, as `EDITOR` of "GbR-Giro" and `OWNER` of the property, attributes the incoming payment "Gast Müller" to the unit "Whg. 2"
- **THEN** the transaction shows "Whg. 2" as its target

#### Scenario: Viewer on the target
- **WHEN** Dieter, as `EDITOR` of the account but only `VIEWER` of the property, tries to attribute a transaction to one of its units
- **THEN** the system responds with HTTP 403

#### Scenario: Transfer
- **WHEN** Anna tries to attribute one side of a transfer to a property
- **THEN** the system rejects this with the message „Umbuchungen können keinem Objekt zugeordnet werden“

### Requirement: Read-only access through the target
A user who sees a transaction only through its property or unit, and not through its account, SHALL see its date, amount, category, counterparty, reference and target. They MUST NOT see the account name, the account balance or the note, and MUST NOT be able to change or delete the transaction.

#### Scenario: Carla reads an attributed payment
- **WHEN** Carla opens the transactions of the unit "Whg. 2", to which a payment from "GbR-Giro" is attributed
- **THEN** they see date, amount, category, counterparty and reference, but neither "GbR-Giro" nor its note nor any button to edit it

#### Scenario: Carla tries to edit
- **WHEN** Carla tries to change that transaction via the API
- **THEN** the system responds with HTTP 404
