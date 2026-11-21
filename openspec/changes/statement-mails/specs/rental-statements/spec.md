## ADDED Requirements

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
