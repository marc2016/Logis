## ADDED Requirements

### Requirement: Charts of the yearly overview
Above the table of the yearly overview of a unit, the evaluation page SHALL show two charts for the chosen year, one bar group per month:
- „Belegung“: the occupancy rate, with owner-use nights stacked on top as their share of the month and in their own colour;
- „Einnahmen“: settled net and planned rent as separate bars.

Settled net and planned rent MUST NOT be added up. The charts MUST show the same figures as the table. Each chart SHALL have a text alternative naming what it shows.

#### Scenario: Mixed month
- **WHEN** Anna opens the overview of a unit for 2026, and November has 11 booked nights, no owner use, a settled net of 540.00 and a planned rent of 248.58
- **THEN** „Belegung“ shows a bar of 37 % for November, and „Einnahmen“ shows a bar of 540.00 and a separate bar of 248.58 for November

#### Scenario: Owner use
- **WHEN** June 2027 has 4 booked nights and 5 owner-use nights
- **THEN** „Belegung“ shows 13 % booked for June with the owner-use share stacked on top in another colour

#### Scenario: Phone
- **WHEN** Anna opens the evaluation page on a phone
- **THEN** both charts fit the screen width without horizontal scrolling, and the month labels are abbreviated

### Requirement: Previous year in the charts
The evaluation page SHALL offer the switch „Vorjahr einblenden“. When it is on, the charts of a unit SHALL also show the previous year as a line: the occupancy rate in „Belegung“ and the settled net in „Einnahmen“. The switch SHALL be kept in the page address.

#### Scenario: Compare with the previous year
- **WHEN** Anna switches on „Vorjahr einblenden“ for 2026
- **THEN** both charts show a line with the figures of 2025 next to the bars of 2026

#### Scenario: Reload
- **WHEN** Anna reloads the page with the switch on
- **THEN** the switch is still on

### Requirement: Overview of all units
The unit selection of the evaluation page SHALL offer „Alle Einheiten“. It SHALL show, for the chosen year, every vacation rental unit for which the user has an effective role, across properties, in a chart of the occupancy rate per unit and a chart of settled net and planned rent per unit. Each unit SHALL be labelled with its property. Units without a role for the user MUST NOT appear.

#### Scenario: Two properties
- **WHEN** Anna can see the unit „Haus Seeufer“ of one property and the units „Wohnung 1“ and „Wohnung 2“ of another, and chooses „Alle Einheiten“ for 2026
- **THEN** the charts and the table show all three units, each with its property name

#### Scenario: Same figures as the unit overview
- **WHEN** „Haus Seeufer“ has 120 nights and a settled net of 9,840.00 in 2026 in its yearly overview
- **THEN** its row under „Alle Einheiten“ shows 120 nights and 9,840.00

#### Scenario: Shared unit
- **WHEN** Dieter is `VIEWER` of one property only and chooses „Alle Einheiten“
- **THEN** only the units of that property appear

### Requirement: Table of all units
Below the charts of „Alle Einheiten“, a table SHALL list one row per unit and a total row, with nights, occupancy rate, owner-use nights, settled net and planned rent of the year. The year figures of a unit MUST equal the totals of its yearly overview.

#### Scenario: Total row
- **WHEN** „Wohnung 1“ has 80 nights and „Wohnung 2“ 60 nights in 2026
- **THEN** the total row shows 140 nights

### Requirement: Archived units in the comparison
Under „Alle Einheiten“, archived units and units of archived properties SHALL only be listed if they have nights, owner-use nights, settled net or planned rent in the chosen year.

#### Scenario: Archived unit without figures
- **WHEN** an archived unit has no bookings and no statements in 2026
- **THEN** it does not appear under „Alle Einheiten“ for 2026

#### Scenario: Archived unit with figures
- **WHEN** a unit archived in 2027 had a settled stay in 2026
- **THEN** it appears under „Alle Einheiten“ for 2026
