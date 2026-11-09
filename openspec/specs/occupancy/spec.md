# occupancy Specification

## Purpose

Occupancy shows how vacation rental units are used over time: a calendar of stays per unit with one row per month, and a yearly overview per unit with booked nights, occupancy rate and net income. It is derived from bookings and statements and stores no data of its own.

## Requirements

### Requirement: Occupancy calendar by year
The system SHALL show the occupancy calendar for one vacation rental unit chosen by the user as a table with one row per month and one column per day of the month, with one bar per booking that is not cancelled. It SHALL show one calendar year at a time: the last month of the previous year, the twelve months of the year, and the first month of the following year. It SHALL start with the current year. The user SHALL be able to page to the previous and the next year and to return to the current year with „Heute“, and the shown year SHALL be kept in the address. The months of the neighbouring years MUST be visually set apart from the year, and the current month MUST be marked. Planned and settled bookings MUST be distinguishable. Only units with an effective role for the user MUST be offered.

#### Scenario: November at the holiday home
- **WHEN** Anna opens the calendar of the unit "Haus Seeufer" in November 2026
- **THEN** they see in the row of November a bar from 6 to 8 November for booking "250905", marked as planned

#### Scenario: Record from the calendar
- **WHEN** Anna records a booking from the calendar view
- **THEN** the calendar shows its bar without reloading the page

#### Scenario: Unit-only user
- **WHEN** Carla, as `EDITOR` of "Whg. 2" only, opens the calendar
- **THEN** only "Whg. 2" can be chosen and the calendar shows its bookings

#### Scenario: Year with neighbouring months
- **WHEN** Anna opens the calendar in November 2026
- **THEN** they see the rows December 2025, January to December 2026 and January 2027, with December 2025 and January 2027 set apart and November 2026 marked as the current month

#### Scenario: Next year and back to today
- **WHEN** Anna pages to the next year and then chooses „Heute“
- **THEN** the calendar first shows December 2026 to January 2028 with 2027 as the year, and then again December 2025 to January 2027

#### Scenario: Year from the address
- **WHEN** Anna opens the calendar with the year 2028 in the address
- **THEN** the calendar shows December 2027 to January 2029

#### Scenario: Stay across the month boundary
- **WHEN** a booking runs from 2026-11-29 to 2026-12-02
- **THEN** the November row shows its bar on 29 and 30 November and the December row shows it on 1 December

#### Scenario: Stay across the year boundary
- **WHEN** a booking runs from 2026-12-30 to 2027-01-02
- **THEN** both the year 2026 and the year 2027 show its bar on 30 and 31 December and on 1 January

### Requirement: Counting nights and money
A booking SHALL count its nights from arrival up to the night before departure, and a stay across a month boundary MUST be split by night. Cancelled bookings MUST NOT count as nights. Money MUST NOT be split by night: settled amounts MUST count in the month of their statement date, the same as in the property finances, and planned rent in the month of arrival.

#### Scenario: Stay across the month boundary
- **WHEN** a booking from 2026-03-28 to 2026-04-04 with a net of 700.00 is on a statement dated 2026-04-05
- **THEN** March counts 4 nights and 0.00, April 3 nights and 700.00

### Requirement: Yearly overview per unit
The system SHALL show, for a vacation rental unit and a year, per month: booked nights, occupancy rate (booked nights / days of the month), settled net and planned rent. Settled net SHALL come from statement lines and planned rent from the expected rent of planned bookings, so the two are never added up. A cancellation payout SHALL count as settled net like any other statement line.

#### Scenario: Mixed month
- **WHEN** November 2026 has a settled stay of 9 nights with a net of 540.00 on a statement dated in November, and a planned stay of 2 nights with an expected rent of 248.58
- **THEN** the overview shows 11 nights, an occupancy rate of 37 %, a settled net of 540.00 and a planned rent of 248.58

#### Scenario: Viewer sees the overview
- **WHEN** Dieter, as `VIEWER` of the property, opens the overview of one of its units
- **THEN** they see the same figures as Anna
