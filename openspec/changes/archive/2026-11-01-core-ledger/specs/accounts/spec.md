# Spec Delta

## Purpose

Accounts represent the places where money is held (checking account, savings account, cash, etc.). They belong to a legal entity, carry the transactions and are the first shareable object in Logis.

## ADDED Requirements

### Requirement: Create an account
A user SHALL be able to create an account in a legal entity they manage, with name, type (`CHECKING`, `SAVINGS`, `CASH`, `CREDIT_CARD`, `OTHER`), optionally an IBAN, and an opening balance (default 0.00). The currency MUST be `EUR`. A given IBAN MUST be formally valid (length and check digits).

#### Scenario: Create a checking account
- **WHEN** Anna creates the account "Girokonto" of type `CHECKING` with an opening balance of 1,250.00 in the legal entity "Anna privat"
- **THEN** the account appears in their account list with a balance of 1,250.00 EUR

#### Scenario: Invalid IBAN
- **WHEN** Anna enters an IBAN with wrong check digits
- **THEN** the system rejects this with the message „Die IBAN ist ungültig“

### Requirement: Account list
A user SHALL see all non-archived accounts shared with them, grouped by legal entity. Each entry shows name, type, legal entity, the user's own role and the current balance. Archived accounts SHALL be shown on request.

#### Scenario: Mixed list
- **WHEN** Bert has an account of their own and sees Anna's "GbR-Giro" as `VIEWER`
- **THEN** their list shows both accounts, grouped under "Bert privat" and "GbR Ostsee", each with their role

### Requirement: Balance
The balance of an account MUST be the opening balance plus the sum of all transaction amounts of the account, including transfers. It MUST be calculated exactly to two decimal places without rounding errors.

#### Scenario: Balance after entries
- **WHEN** an account with an opening balance of 100.00 has the transactions +0.10, +0.20 and -50.00
- **THEN** its balance is 50.30

### Requirement: Edit and archive an account
An `OWNER` SHALL be able to change name, type, IBAN and opening balance, and to archive or restore the account. The legal entity of an account MUST NOT be changed afterwards. An archived account MUST NOT accept new transactions, but its existing ones stay visible. An account SHALL only be deletable if it has no transactions.

#### Scenario: Archive
- **WHEN** Anna archives their old savings account and then tries to create a transaction in it
- **THEN** the system rejects this with the message „Das Konto ist archiviert“, and the existing transactions remain retrievable

#### Scenario: Editor edits an account
- **WHEN** Bert, as `EDITOR`, tries to change the account's name
- **THEN** the system responds with HTTP 403
