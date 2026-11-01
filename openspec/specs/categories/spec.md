# categories Specification

## Purpose

Categories classify transactions by subject, e.g. "Lebensmittel" (groceries) or "Grundsteuer" (property tax). A category tree shared by all users keeps reports comparable across accounts and legal entities.

## Requirements

### Requirement: Shared category tree
The system SHALL maintain a category tree shared by all users with at most two levels: main categories and subcategories. Every category MUST have a kind, `INCOME` or `EXPENSE`. A subcategory always has the kind of its main category. Names MUST be unique within the same main category, and among the main categories of one kind, regardless of case.

#### Scenario: Retrieve the tree
- **WHEN** a signed-in user retrieves the categories
- **THEN** they receive all active categories as a tree, split into income and expenses

#### Scenario: Third level
- **WHEN** an admin tries to create another category below a subcategory
- **THEN** the system rejects this

### Requirement: Default categories
During setup the system SHALL create a German base set of categories. It includes at least:
- Income: Gehalt, Mieteinnahmen, Kapitalerträge, Sonstige Einnahmen
- Expenses: Wohnen (with Grundsteuer, Nebenkosten, Instandhaltung), Lebensmittel, Mobilität, Versicherungen, Steuern und Abgaben, Gebühren, Sonstige Ausgaben

#### Scenario: Fresh installation
- **WHEN** Logis starts for the first time
- **THEN** the default categories are available for selection

### Requirement: Maintain categories
Only users with the role `ADMIN` SHALL be able to create, rename, move and archive categories. Other users MUST receive HTTP 403 for these calls. A category MUST NOT be deleted if transactions or subcategories are assigned to it. Archiving, on the other hand, MUST always be possible. Archived categories SHALL be restorable.

#### Scenario: Create a category
- **WHEN** an admin creates the subcategory "Hausgeld" under "Ausgaben > Wohnen"
- **THEN** "Hausgeld" can be selected by all users when entering transactions

#### Scenario: User without admin rights
- **WHEN** a `USER` tries to create a category
- **THEN** the system responds with HTTP 403

#### Scenario: Delete a category in use
- **WHEN** an admin tries to delete a category that has transactions assigned
- **THEN** the system rejects this with the message „Die Kategorie wird verwendet und kann nur archiviert werden“

### Requirement: Categories from modules
Categories SHALL be assignable to a module. Such categories MUST be visible to and archivable by admins, but MUST NOT be renamed, moved or deleted, because the module relies on them. If the module is disabled, the system MUST NOT offer its categories when entering transactions. Existing assignments MUST be kept.

#### Scenario: Rename a module category
- **WHEN** an admin tries to rename a category provided by the vacation rental module
- **THEN** the system rejects this with the message „Diese Kategorie wird von einem Modul verwaltet“
