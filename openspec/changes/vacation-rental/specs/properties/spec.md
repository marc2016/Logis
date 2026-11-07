# Spec Delta

## MODIFIED Requirements

### Requirement: Edit and archive a property
An `OWNER` of a property SHALL be able to change its name and address and to archive or restore it. The legal entity of a property MUST NOT be changed afterwards. Archived properties SHALL be hidden from lists by default and shown on request. A property SHALL only be deletable if no transactions are attributed to it or its units and no module still references one of its units, for example through bookings.

#### Scenario: Editor edits a property
- **WHEN** Bert, as `EDITOR` of a property, tries to change its name
- **THEN** the system responds with HTTP 403

#### Scenario: Delete with attributed transactions
- **WHEN** Anna tries to delete a property to whose unit transactions are attributed
- **THEN** the system rejects this with the message „Dem Objekt sind noch Transaktionen zugeordnet und es kann nur archiviert werden“

#### Scenario: Delete with bookings
- **WHEN** Anna tries to delete a property whose unit has bookings
- **THEN** the system rejects this with the message „Zum Objekt gibt es noch Buchungen und es kann nur archiviert werden“

## ADDED Requirements

### Requirement: Record costs from the property page
The property page SHALL offer „Kosten erfassen“ to users with at least `EDITOR` as effective role on the property or one of its units. It MUST create an ordinary transaction on an account chosen by the user, with the property or unit preselected as the target. The rules for entering and attributing transactions MUST apply unchanged.

#### Scenario: Repair invoice
- **WHEN** Anna chooses „Kosten erfassen“ on "Haus Seeufer" and enters -320.00 "Handwerker" on the GbR's account
- **THEN** the transaction appears on the account and in the property's transactions with "Haus Seeufer" as its target

#### Scenario: No account rights
- **WHEN** Carla, as `EDITOR` of a unit without rights on any account, opens „Kosten erfassen“
- **THEN** the dialog offers no account and shows „Kein Konto mit Schreibrechten vorhanden“
