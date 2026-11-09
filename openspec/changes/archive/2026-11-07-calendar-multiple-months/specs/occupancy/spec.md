# Spec Delta

## MODIFIED Requirements

### Requirement: Occupancy calendar
The system SHALL show the occupancy calendar for one vacation rental unit chosen by the user as a continuous table with one row per month and one column per day of the month, starting with the current month, with one bar per booking that is not cancelled. Scrolling SHALL load further months in both directions without a fixed limit, and the user SHALL be able to return to the current month. Planned and settled bookings MUST be distinguishable. Only units with an effective role for the user MUST be offered.

#### Scenario: November at the holiday home
- **WHEN** Anna opens the calendar of the unit "Haus Seeufer" for November 2026
- **THEN** they see in the row of November a bar from 6 to 8 November for booking "250905", marked as planned

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
