## ADDED Requirements

### Requirement: Bulk actions on statements
The list of statements SHALL offer „Löschen“ for selected statements, with the effect of deleting each one. A statement page SHALL offer „Buchungen zuordnen“, which lists the unit's bookings that are on no statement, filtered to the statement's period by default. The selected bookings are assigned with their expected rent as gross, a commission rate in percent and a cleaning amount entered once. The statement page SHALL also offer to remove selected lines. Each line MUST follow the rules of the single action.

#### Scenario: Fill a statement
- **WHEN** Anna opens the October statement of „Haus Seeufer“, chooses „Buchungen zuordnen“, keeps the 7 preselected bookings of October and enters 15 % and 60.00
- **THEN** the statement shows 7 lines and the payout as the sum of their nets

#### Scenario: Remove lines
- **WHEN** Anna selects 2 lines of the October statement and removes them
- **THEN** the 2 bookings are planned again and the payout drops by their nets
