# Spec Delta

## MODIFIED Requirements

### Requirement: Occupancy calendar
The system SHALL show the occupancy calendar per property as a continuous sequence of months, one below the other, starting with the current month, with one row per vacation rental unit visible to the user and one bar per booking that is not cancelled. Scrolling SHALL load further months in both directions without a fixed limit, and the user SHALL be able to return to the current month. Planned and settled bookings MUST be distinguishable. Units without an effective role for the user MUST NOT appear.

#### Scenario: November at the holiday home
- **WHEN** Anna opens the calendar of "Haus Seeufer" for November 2026
- **THEN** they see a bar from 6 to 8 November for booking "275505", marked as planned

#### Scenario: Record from the calendar
- **WHEN** Anna records a booking from the calendar view
- **THEN** the calendar shows its bar without reloading the page

#### Scenario: Unit-only user
- **WHEN** Carla, as `EDITOR` of "Whg. 2" only, opens the calendar of its property
- **THEN** they see only the row of "Whg. 2"

#### Scenario: Several months
- **WHEN** Anna opens the calendar in November 2026
- **THEN** they see November, December 2026 and January 2027 one below the other, and scrolling down shows February 2027

#### Scenario: Earlier months and back to today
- **WHEN** Anna scrolls up to October 2026 and then chooses „Heute“
- **THEN** the calendar shows October as well and then returns to November 2026

#### Scenario: Stay across the month boundary
- **WHEN** a booking runs from 2026-11-29 to 2026-12-02
- **THEN** November shows its bar on 29 and 30 November and December shows it on 1 December
