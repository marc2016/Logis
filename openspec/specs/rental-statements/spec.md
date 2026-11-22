# rental-statements Specification

## Purpose

Rental statements record what a rental service provider settles for a unit: the bookings of a period with their gross amount and deductions, and the resulting payout. They are the source of the settled income per unit and, later, the counterpart of a payment on the account.

## Requirements

### Requirement: Create a statement
A user with at least `EDITOR` as effective role on a unit SHALL be able to create a statement for it with provider name, period (from, to) and statement date. The provider name MUST NOT be empty. The system SHALL suggest provider names from earlier statements of the same unit.

#### Scenario: October statement
- **WHEN** Anna creates a statement from "Ferienwohnungen Nord" for "Haus Seeufer", period 2026-10-01 to 2026-10-31
- **THEN** the statement appears in the unit's statements with a payout of 0.00

#### Scenario: Provider suggestion
- **WHEN** Anna starts a second statement for the same unit
- **THEN** "Ferienwohnungen Nord" is offered as the provider

### Requirement: Booking lines and payout
A user with at least `EDITOR` on the unit SHALL be able to assign bookings of the same unit to a statement with gross, commission and cleaning. Net MUST be gross minus commission minus cleaning. The payout of a statement MUST be the sum of its net amounts. A booking MUST be assigned to at most one statement. When it is assigned, its gross SHALL default to its expected rent.

#### Scenario: Assign a booking
- **WHEN** Anna assigns booking "250905" with gross 248.58, commission 37.29 and cleaning 60.00 to the November statement
- **THEN** the line shows a net of 151.29, the payout rises by 151.29, and the booking shows the status settled

#### Scenario: Booking of another unit
- **WHEN** Anna tries to assign a booking of "Whg. 1" to a statement of "Whg. 2"
- **THEN** the system rejects this with the message „Die Buchung gehört zu einer anderen Einheit“

#### Scenario: Already settled
- **WHEN** Anna tries to assign a booking that is already on another statement
- **THEN** the system rejects this with the message „Die Buchung ist bereits abgerechnet“

### Requirement: Cancellation payout
A cancelled booking SHALL be assignable to a statement like any other booking, so that a cancellation fee paid out by the provider is recorded as its gross. It MUST stay cancelled and MUST NOT occupy the calendar.

#### Scenario: Cancellation fee
- **WHEN** Anna assigns a cancelled booking with gross 80.00 and commission 12.00 to a statement
- **THEN** the payout rises by 68.00, and the booking stays cancelled

### Requirement: Edit and delete a statement
A user with at least `EDITOR` on the unit SHALL be able to change a statement's data and lines, remove a booking from it and delete it. A removed booking SHALL lose its amounts from the statement and become planned again, or stay cancelled. Deleting a statement MUST do this for all its bookings.

#### Scenario: Remove a line
- **WHEN** Anna removes booking "250905" from the November statement
- **THEN** the payout drops by its net, and the booking shows the status planned

### Requirement: Access through the unit
Statements MUST be visible exactly to users with an effective role on their unit. `VIEWER`s SHALL read statements and lines with guest names but without guest contact data. A statement of a unit without such a role MUST be treated as nonexistent (HTTP 404).

#### Scenario: Viewer reads a statement
- **WHEN** Dieter, as `VIEWER` of the property, opens a statement of one of its units
- **THEN** they see provider, period, lines and payout, but no button to change anything

### Requirement: Statement page
Every statement SHALL open as a page of its own with an address containing the statement's id, reached from the list of statements. The page SHALL have a header with provider, unit, period, statement date and payout, and SHALL show the statement's lines with their actions. The back button SHALL return to the list of statements with its unit filter. Earlier addresses that selected a statement in the list SHALL lead to its page. A statement that does not exist or is not visible MUST show „Abrechnung nicht gefunden“.

#### Scenario: Open a statement
- **WHEN** Anna clicks the November statement of „Haus Seeufer“ in the list
- **THEN** its page opens at its own address with the provider, the period 2026-11-01 to 2026-11-30 and the payout in the header

#### Scenario: Old link
- **WHEN** Anna opens a bookmarked address of the statement list with the November statement selected
- **THEN** the page of the November statement opens

### Requirement: Bulk actions on statements
The list of statements SHALL offer „Löschen“ for selected statements, with the effect of deleting each one. A statement page SHALL offer „Buchungen zuordnen“, which lists the unit's bookings that are on no statement, filtered to the statement's period by default. The selected bookings are assigned with their expected rent as gross, a commission rate in percent and a cleaning amount entered once. The statement page SHALL also offer to remove selected lines. Each line MUST follow the rules of the single action.

#### Scenario: Fill a statement
- **WHEN** Anna opens the October statement of „Haus Seeufer“, chooses „Buchungen zuordnen“, keeps the 7 preselected bookings of October and enters 15 % and 60.00
- **THEN** the statement shows 7 lines and the payout as the sum of their nets

#### Scenario: Remove lines
- **WHEN** Anna selects 2 lines of the October statement and removes them
- **THEN** the 2 bookings are planned again and the payout drops by their nets

### Requirement: Owner use is not settled
An owner use MUST NOT be assigned to a statement, and statements MUST NOT offer owner uses as open bookings.

#### Scenario: Assign an owner use
- **WHEN** Anna tries to assign an owner use to a statement
- **THEN** the system rejects this with the message „Eine Eigenbelegung wird nicht abgerechnet“

### Requirement: Invoice number and stated payout
A statement SHALL have an optional invoice number and an optional stated payout, the amount the provider's document names as paid out. Both SHALL be editable by users with at least `EDITOR` on the unit and SHALL be shown in the header of the statement page. Existing statements SHALL have neither.

#### Scenario: Entered by hand
- **WHEN** an editor enters the invoice number „R-2027-03“ and the stated payout 1.200,00 on a statement
- **THEN** both are shown in the header of the statement page

#### Scenario: Existing statement
- **WHEN** a statement was created before these fields existed
- **THEN** it shows no invoice number and no stated payout, and its payout is unchanged

### Requirement: Open lines
A statement SHALL be able to hold open lines: rows of the provider's document that are on no booking yet, each with tenant name, rental period, gross, commission and an optional note. The statement page SHALL list them under „Offene Zeilen“ for users with at least `EDITOR`; `VIEWER`s SHALL see their number only. Open lines MUST NOT count in the payout.

An editor SHALL be able to assign an open line to a booking of the unit with „Zuordnen“. The booking then gets a line with the open line's amounts, or has them added to its line if it is already on this statement, and the open line is gone. Bookings on another statement and owner uses MUST NOT be offered. An editor SHALL be able to remove an open line with „Verwerfen“. Deleting a statement MUST delete its open lines.

#### Scenario: Assign an open line
- **WHEN** an editor assigns the open line of „Erika Musterfrau“ with gross 400,00 and commission 60,00 to a booking of the unit that is on no statement
- **THEN** the booking is on the statement with gross 400,00 and commission 60,00, and the open line is gone

#### Scenario: Add to a line of the same statement
- **WHEN** an editor assigns an open line with gross 90,00 and commission 15,00 to a booking that is on this statement with gross 350,00 and commission 55,00
- **THEN** the booking's line has gross 440,00 and commission 70,00

#### Scenario: Discard an open line
- **WHEN** an editor uses „Verwerfen“ on an open line
- **THEN** the open line is gone and the payout is unchanged

#### Scenario: Viewer
- **WHEN** a viewer opens a statement with two open lines
- **THEN** the page says that there are two open lines without tenant names or amounts

### Requirement: Statements to review
A statement SHALL be marked „zu prüfen“ while it has open lines or has a stated payout that differs from its payout. The mark SHALL appear in the list of statements and on the statement page together with its reasons, and SHALL disappear as soon as neither reason holds. On the statement page, a line whose gross differs from the expected rent of its booking SHALL be marked with „weicht vom Mietpreis ab“ and the expected rent.

#### Scenario: Open lines
- **WHEN** a statement has one open line
- **THEN** it is marked „zu prüfen“ with „1 offene Zeile“

#### Scenario: Payout differs
- **WHEN** a statement's lines sum to a payout of 1.150,00 and its stated payout is 1.200,00
- **THEN** it is marked „zu prüfen“ with „Auszahlung weicht um 50,00 € vom Saldo der Abrechnung ab“

#### Scenario: Resolved
- **WHEN** the last open line is assigned and the payout equals the stated payout
- **THEN** the statement is no longer marked

#### Scenario: Gross differs from the expected rent
- **WHEN** a line has gross 790,00 and its booking has an expected rent of 760,00
- **THEN** the line shows „weicht vom Mietpreis ab“ with 760,00 €

### Requirement: Documents of a statement
A statement SHALL have documents, shown on the statement page under „Dokumente“. Users with at least `EDITOR` on the unit SHALL be able to open, add and delete them; other users MUST NOT be able to read them. Deleting a statement MUST delete its documents.

#### Scenario: Imported PDF
- **WHEN** a statement was created from a mail with a PDF
- **THEN** an editor finds the PDF under „Dokumente“ and can open it

#### Scenario: Viewer
- **WHEN** a viewer opens the statement page
- **THEN** no documents are offered, and requesting one answers HTTP 404

#### Scenario: Statement deleted
- **WHEN** an editor deletes the statement
- **THEN** its documents are deleted as well
