# transactions Specification

## Purpose

Transactions are the individual movements of money on an account. They are the basis for balances, income and expense overviews and later tax reports. At this stage they are entered manually.

## Requirements

### Requirement: Enter a transaction
A user with at least `EDITOR` rights on an account SHALL be able to enter a transaction in it. Booking date and amount are required. Category, counterparty, reference and note are optional. The amount MUST be non-zero, have at most two decimal places and not exceed 999,999,999.99. Positive amounts are income, negative amounts are expenses.

#### Scenario: Enter an expense
- **WHEN** Anna enters a transaction of -84.90 on 2026-10-03 in the account "Girokonto" with category "Lebensmittel" and counterparty "Edeka"
- **THEN** it appears in the account's list, and the balance drops by 84.90

#### Scenario: Amount 0 or too precise
- **WHEN** Anna tries to enter a transaction of 0.00 or of 12.345
- **THEN** the system rejects this with a message about the amount

### Requirement: Edit and delete a transaction
A user with at least `EDITOR` rights SHALL be able to change and delete the account's transactions. Moving a transaction to another account MUST require at least `EDITOR` rights there as well. Concurrent changes to the same transaction MUST be detected. The second change MUST then be rejected with the message „Die Transaktion wurde inzwischen geändert“ instead of overwriting the first.

#### Scenario: Correct the category
- **WHEN** Bert, as `EDITOR`, changes the category of a transaction
- **THEN** the transaction shows the new category

#### Scenario: Concurrent editing
- **WHEN** Anna and Bert open the same transaction, Anna saves, and Bert then saves with the old state
- **THEN** Bert's change is rejected with „Die Transaktion wurde inzwischen geändert“

### Requirement: Transfer between accounts
A user with at least `EDITOR` rights on both accounts SHALL be able to transfer an amount from one account to another. The system MUST create two linked transactions for this: the negative amount in the source account and the positive amount in the target account, with the same date. Transfers MUST change the balances, MUST NOT count as income or expense, and have no category. Changing or deleting one side MUST change or delete the other side as well.

#### Scenario: Transfer to savings
- **WHEN** Anna transfers 500.00 from "Girokonto" to "Tagesgeld"
- **THEN** the balance of the checking account drops by 500.00, that of the savings account rises by 500.00, and the totals of income and expenses stay unchanged

#### Scenario: Delete a transfer
- **WHEN** Anna deletes one side of the transfer
- **THEN** both transactions are deleted

#### Scenario: Missing rights on the target account
- **WHEN** Bert tries to transfer from their own account to an account on which they are only `VIEWER`
- **THEN** the system responds with HTTP 403

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

### Requirement: Assign active categories only
Only an existing, non-archived category MUST be assignable to a transaction. Categories already assigned MUST stay on the transactions after they are archived.

#### Scenario: Archived category
- **WHEN** Anna tries to assign an archived category to a new transaction
- **THEN** the system rejects this with the message „Die Kategorie ist nicht verfügbar“

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
