# Spec Delta

## Purpose

Transactions are the individual movements of money on an account. They are the basis for balances, income and expense overviews and later tax reports. At this stage they are entered manually.

## ADDED Requirements

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
A user SHALL be able to list the transactions of all accounts shared with them or of a single account. They are sorted by date descending and retrievable page by page with at most 100 entries each. The list SHALL be filterable by account, period, category (including its subcategories), "uncategorized", and a search text in counterparty, reference and note. For each filter, the system MUST return the total of income and the total of expenses, excluding transfers and across all pages.

#### Scenario: Monthly overview
- **WHEN** Anna retrieves the transactions of all accounts for October 2026
- **THEN** they receive October's transactions and the totals of income and expenses for that month

#### Scenario: Other people's accounts stay hidden
- **WHEN** Bert retrieves the transactions of all accounts
- **THEN** the list contains only transactions from accounts shared with them

#### Scenario: Find uncategorized
- **WHEN** Anna filters by "uncategorized"
- **THEN** they receive only transactions without a category and without transfers

### Requirement: Assign active categories only
Only an existing, non-archived category MUST be assignable to a transaction. Categories already assigned MUST stay on the transactions after they are archived.

#### Scenario: Archived category
- **WHEN** Anna tries to assign an archived category to a new transaction
- **THEN** the system rejects this with the message „Die Kategorie ist nicht verfügbar“
