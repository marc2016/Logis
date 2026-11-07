# Spec Delta

## Purpose

Occupancy shows how vacation rental units are used over time: a monthly calendar of stays per property and a yearly overview per unit with booked nights, occupancy rate and net income. It is derived from bookings and statements and stores no data of its own.

## ADDED Requirements

### Requirement: Occupancy calendar
The system SHALL show a monthly calendar per property with one row per vacation rental unit visible to the user and one bar per booking that is not cancelled. Planned and settled bookings MUST be distinguishable. Units without an effective role for the user MUST NOT appear.

#### Scenario: November at the holiday home
- **WHEN** Anna opens the calendar of "Haus Seeufer" for November 2026
- **THEN** they see a bar from 6 to 8 November for booking "275505", marked as planned

#### Scenario: Record from the calendar
- **WHEN** Anna records a booking from the calendar view
- **THEN** the calendar shows its bar without reloading the page

#### Scenario: Unit-only user
- **WHEN** Carla, as `EDITOR` of "Whg. 2" only, opens the calendar of its property
- **THEN** they see only the row of "Whg. 2"

### Requirement: Night-based counting
For all figures, a booking SHALL count its nights from arrival up to the night before departure. A stay across a month boundary MUST be split by night. Rent and net amounts MUST be distributed over the nights in proportion, with any rounding remainder on the last night. Cancelled bookings MUST NOT count as nights.

#### Scenario: Stay across the month boundary
- **WHEN** a settled booking from 2026-03-28 to 2026-04-04 has a net of 700.00
- **THEN** March counts 4 nights and 400.00, April 3 nights and 300.00

### Requirement: Yearly overview per unit
The system SHALL show, for a vacation rental unit and a year, per month: booked nights, occupancy rate (booked nights / days of the month), settled net and planned rent. Settled net SHALL come from statement lines and planned rent from the expected rent of planned bookings, so the two are never added up. A cancellation payout SHALL count as settled net in the month of its booking's arrival.

#### Scenario: Mixed month
- **WHEN** November 2026 has a settled stay of 9 nights with a net of 540.00 and a planned stay of 2 nights with an expected rent of 248.58
- **THEN** the overview shows 11 nights, an occupancy rate of 37 %, a settled net of 540.00 and a planned rent of 248.58

#### Scenario: Viewer sees the overview
- **WHEN** Dieter, as `VIEWER` of the property, opens the overview of one of its units
- **THEN** they see the same figures as Anna
