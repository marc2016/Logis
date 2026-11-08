# Spec Delta

## REMOVED Requirements

### Requirement: Occupancy calendar
**Reason**: Endless scrolling in both directions loaded one month per request without limit, shifted the view when earlier months were inserted, and made „Heute“ unreliable.
**Migration**: Replaced by „Occupancy calendar by year“, which shows one year with its neighbouring months and is paged by year. Links with a month in the address open the year of that month.

## ADDED Requirements

### Requirement: Occupancy calendar by year
The system SHALL show the occupancy calendar for one vacation rental unit chosen by the user as a table with one row per month and one column per day of the month, with one bar per booking that is not cancelled. It SHALL show one calendar year at a time: the last month of the previous year, the twelve months of the year, and the first month of the following year. It SHALL start with the current year. The user SHALL be able to page to the previous and the next year and to return to the current year with „Heute“, and the shown year SHALL be kept in the address. The months of the neighbouring years MUST be visually set apart from the year, and the current month MUST be marked. Planned and settled bookings MUST be distinguishable. Only units with an effective role for the user MUST be offered.

#### Scenario: November at the holiday home
- **WHEN** Anna opens the calendar of the unit "Haus Seeufer" in November 2026
- **THEN** they see in the row of November a bar from 6 to 8 November for booking "275505", marked as planned

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
