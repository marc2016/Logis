# Spec Delta

## Purpose

Legal entities are the owners of accounts, e.g. a private individual or a GbR. They separate finances for tax purposes, so that reports such as Anlage V or EÜR become possible per owner. They do not grant any rights themselves.

## ADDED Requirements

### Requirement: Private legal entity per user
Every user MUST have exactly one private legal entity of type `PRIVATE_PERSON`, which is created automatically when the user is created and is named `<display name> privat`. The private legal entity MUST NOT be deletable or transferable to another user.

#### Scenario: New user
- **WHEN** an admin creates the user "anna" with display name "Anna"
- **THEN** Anna owns a legal entity „Anna privat“ of type `PRIVATE_PERSON`

#### Scenario: Existing users
- **WHEN** Logis starts with this version for the first time and users already exist
- **THEN** every existing user has their private legal entity afterwards

### Requirement: Create further legal entities
Every signed-in user SHALL be able to create further legal entities with name, type (`PRIVATE_PERSON`, `GBR`, `GMBH`, `OTHER`) and optionally a tax number. Whoever creates a legal entity MUST be its manager. The name MUST be unique per manager, regardless of case.

#### Scenario: Create a GbR
- **WHEN** Anna creates a legal entity "GbR Ostsee" of type `GBR`
- **THEN** it appears in Anna's list of legal entities, and Anna can create accounts in it

#### Scenario: Duplicate name
- **WHEN** Anna tries to create a second legal entity "gbr ostsee"
- **THEN** the system rejects this with the message „Ein Rechtsträger mit diesem Namen existiert bereits“

### Requirement: Manage legal entities
Only the manager of a legal entity SHALL be able to change its name, type and tax number and to create accounts in it. A legal entity SHALL only be deletable if it has no accounts left and is not private.

#### Scenario: Rename
- **WHEN** Anna renames "GbR Ostsee" to "Ostsee GbR"
- **THEN** all accounts of this legal entity show the new name

#### Scenario: Delete with accounts
- **WHEN** Anna tries to delete a legal entity that still has accounts
- **THEN** the system rejects this with the message „Der Rechtsträger hat noch Konten“

### Requirement: Visibility of legal entities
A user SHALL see the legal entities they manage. They SHALL see other people's legal entities only as name and type on the accounts shared with them. They MUST NOT be able to change them, create accounts in them or see their other accounts.

#### Scenario: Shared account of someone else's GbR
- **WHEN** Bert, as `EDITOR`, sees the account "GbR-Giro" of Anna's "GbR Ostsee"
- **THEN** they see "GbR Ostsee" as the owner of this account, but neither the GbR in their list of legal entities nor other accounts of the GbR

#### Scenario: Create an account in someone else's legal entity
- **WHEN** Bert tries to create an account in the legal entity "GbR Ostsee"
- **THEN** the system responds with HTTP 404
