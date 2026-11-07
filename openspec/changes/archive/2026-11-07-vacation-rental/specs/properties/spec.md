# Spec Delta

## MODIFIED Requirements

### Requirement: Edit and archive a property
An `OWNER` of a property SHALL be able to change its name and address and to archive or restore it. The legal entity of a property MUST NOT be changed afterwards. Archived properties SHALL be hidden from lists by default and shown on request. A property SHALL only be deletable if no transactions are attributed to it or its units, no costs are recorded on it or its units, and no module still references one of its units, for example through bookings.

#### Scenario: Editor edits a property
- **WHEN** Bert, as `EDITOR` of a property, tries to change its name
- **THEN** the system responds with HTTP 403

#### Scenario: Delete with attributed transactions
- **WHEN** Anna tries to delete a property to whose unit transactions are attributed
- **THEN** the system rejects this with the message „Dem Objekt sind noch Transaktionen zugeordnet und es kann nur archiviert werden“

#### Scenario: Delete with bookings
- **WHEN** Anna tries to delete a property whose unit has bookings
- **THEN** the system rejects this with the message „Zum Objekt gibt es noch Buchungen und es kann nur archiviert werden“

#### Scenario: Delete with costs
- **WHEN** Anna tries to delete a property with a recorded cost
- **THEN** the system rejects this with the message „Zum Objekt sind noch Kosten erfasst und es kann nur archiviert werden“
