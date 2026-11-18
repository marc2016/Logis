# Spec Delta

## ADDED Requirements

### Requirement: Owner use in the calendar
The occupancy calendar SHALL show owner uses that are not cancelled as bars labelled „Eigenbelegung“, distinguishable from planned and settled guest bookings.

#### Scenario: Owner use in June
- **WHEN** Anna opens the calendar of "Haus Seeufer" for 2027 and an owner use runs from 2027-06-09 to 2027-06-14
- **THEN** the June row shows an „Eigenbelegung“ bar from 9 to 13 June, set apart from guest bookings

## MODIFIED Requirements

### Requirement: Yearly overview per unit
The system SHALL show, for a vacation rental unit and a year, per month: booked nights, occupancy rate (booked nights / days of the month), owner-use nights, settled net and planned rent. Owner uses MUST NOT count as booked nights or towards the occupancy rate; their nights SHALL be shown on their own as owner-use nights. Settled net SHALL come from statement lines and planned rent from the expected rent of planned bookings, so the two are never added up. A cancellation payout SHALL count as settled net like any other statement line.

#### Scenario: Mixed month
- **WHEN** November 2026 has a settled stay of 9 nights with a net of 540.00 on a statement dated in November, and a planned stay of 2 nights with an expected rent of 248.58
- **THEN** the overview shows 11 nights, an occupancy rate of 37 %, 0 owner-use nights, a settled net of 540.00 and a planned rent of 248.58

#### Scenario: Viewer sees the overview
- **WHEN** Dieter, as `VIEWER` of the property, opens the overview of one of its units
- **THEN** they see the same figures as Anna

#### Scenario: Month with an owner use
- **WHEN** June 2027 has a planned stay of 4 nights and an owner use from 2027-06-09 to 2027-06-14
- **THEN** the overview shows 4 nights, an occupancy rate of 13 % and 5 owner-use nights
