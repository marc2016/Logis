# properties Specification

## Purpose

Properties and their units are the real estate that Logis manages, e.g. a holiday home or a house with several apartments. They belong to a legal entity, are shared per object, and are the foundation for modules such as vacation rental.

## Requirements

### Requirement: Create a property
A user SHALL be able to create a property in a legal entity they manage, with a name and optionally an address (street, postal code, city). When creating it, the user MUST choose whether it is a single-unit property or a property with several units. Whoever creates a property MUST become its `OWNER`. Creating a property in someone else's legal entity MUST be answered with HTTP 404. The name MUST NOT be empty and MUST be at most 128 characters long.

#### Scenario: Create a holiday home
- **WHEN** Anna creates the single-unit property "Ferienhaus Ostsee" in the legal entity "Anna privat"
- **THEN** the property appears in their property list, and Anna is its `OWNER`

#### Scenario: Someone else's legal entity
- **WHEN** Bert tries to create a property in Anna's legal entity "GbR Ostsee"
- **THEN** the system responds with HTTP 404

### Requirement: Every property has at least one unit
Every property MUST have at least one active unit. For a single-unit property the system SHALL create exactly one unit automatically, named like the property. For a property with several units, the user MUST enter the first unit when creating the property. The last active unit of a property MUST NOT be archived.

#### Scenario: Automatic unit
- **WHEN** Anna creates the single-unit property "Ferienhaus Ostsee"
- **THEN** the property has exactly one unit "Ferienhaus Ostsee"

#### Scenario: Archive the last unit
- **WHEN** Anna tries to archive the only active unit of a property
- **THEN** the system rejects this with the message „Ein Objekt braucht mindestens eine aktive Einheit“

### Requirement: Manage units
A user with at least `EDITOR` on the property SHALL be able to add units, change their name and usage, and archive or restore them. The usage MUST be one of `VACATION_RENTAL`, `LONG_TERM_RENTAL`, `OWN_USE` or `OTHER`. Unit names MUST be unique within a property, regardless of case. Adding a second unit to a single-unit property SHALL turn it into a property with several units.

#### Scenario: Add an apartment
- **WHEN** Anna adds the unit "Einliegerwohnung" with usage `VACATION_RENTAL` to "Ferienhaus Ostsee"
- **THEN** the property has two units, and existing data of the first unit stays assigned to it

#### Scenario: Duplicate unit name
- **WHEN** Anna tries to add a second unit "einliegerwohnung" to the same property
- **THEN** the system rejects this with the message „Eine Einheit mit diesem Namen existiert bereits“

### Requirement: Edit and archive a property
An `OWNER` of a property SHALL be able to change its name and address and to archive or restore it. The legal entity of a property MUST NOT be changed afterwards. Archived properties SHALL be hidden from lists by default and shown on request. A property SHALL only be deletable if no transactions are attributed to it or its units.

#### Scenario: Editor edits a property
- **WHEN** Bert, as `EDITOR` of a property, tries to change its name
- **THEN** the system responds with HTTP 403

#### Scenario: Delete with attributed transactions
- **WHEN** Anna tries to delete a property to whose unit transactions are attributed
- **THEN** the system rejects this with the message „Dem Objekt sind noch Transaktionen zugeordnet und es kann nur archiviert werden“

### Requirement: Property list and visibility
A user SHALL see every property on which they have a share, and every property with at least one unit on which they have a share. Each entry shows name, address, legal entity, the user's role on the property (if any) and the units visible to them. Without any share on the property or one of its units, a property MUST be treated as nonexistent (HTTP 404).

#### Scenario: Owner sees all units
- **WHEN** Anna, as `OWNER` of a property with the units "Whg. 1" and "Whg. 2", opens the property
- **THEN** they see both units

#### Scenario: Unit-only access
- **WHEN** Carla has a share only on "Whg. 2" and opens the property
- **THEN** they see the property's name, address and legal entity and the unit "Whg. 2", but not "Whg. 1", the property's shares or transactions attributed to the property itself

#### Scenario: No share
- **WHEN** Bert has no share on a property or any of its units and retrieves it
- **THEN** the system responds with HTTP 404

### Requirement: Single-unit properties in the interface
For a single-unit property the interface SHALL NOT show a separate unit level: the property page shows the property, its shares and its transactions directly. Once a property has more than one unit, the interface SHALL list the units.

#### Scenario: Holiday home page
- **WHEN** Anna opens "Ferienhaus Ostsee", which has one unit
- **THEN** the page shows the property without a unit list
