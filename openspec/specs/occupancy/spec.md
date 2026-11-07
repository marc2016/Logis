# occupancy Specification

## Purpose

Occupancy shows how vacation rental units are used over time: a calendar of stays per unit with one row per month, and a yearly overview per unit with booked nights, occupancy rate and net income. It is derived from bookings and statements and stores no data of its own.

## Requirements

### Requirement: Occupancy calendar
The system SHALL show the occupancy calendar for one vacation rental unit chosen by the user as a continuous table with one row per month and one column per day of the month, starting with the current month, with one bar per booking that is not cancelled. Scrolling SHALL load further months in both directions without a fixed limit, and the user SHALL be able to return to the current month. Planned and settled bookings MUST be distinguishable. Only units with an effective role for the user MUST be offered.

#### Scenario: November at the holiday home
- **WHEN** Anna opens the calendar of the unit "Haus Seeufer" for November 2026
- **THEN** they see in the row of November a bar from 6 to 8 November for booking "275505", marked as planned

#### Scenario: Record from the calendar
- **WHEN** Anna records a booking from the calendar view
- **THEN** the calendar shows its bar without reloading the page

#### Scenario: Unit-only user
- **WHEN** Carla, as `EDITOR` of "Whg. 2" only, opens the calendar
- **THEN** only "Whg. 2" can be chosen and the calendar shows its bookings

#### Scenario: Several months
- **WHEN** Anna opens the calendar in November 2026
- **THEN** they see the rows November, December 2026 and January 2027 one below the other, and scrolling down shows February 2027

#### Scenario: Earlier months and back to today
- **WHEN** Anna scrolls up to October 2026 and then chooses „Heute“
- **THEN** the calendar shows October as well and then returns to November 2026

#### Scenario: Stay across the month boundary
- **WHEN** a booking runs from 2026-11-29 to 2026-12-02
- **THEN** the November row shows its bar on 29 and 30 November and the December row shows it on 1 December

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
