## ADDED Requirements

### Requirement: Date fields
Every date field SHALL accept a typed date in the form `TT.MM.JJJJ`, SHALL offer a calendar in which month and year can be chosen directly, and SHALL be as high as the other fields of its form and fit its column. A date that is incomplete or invalid MUST show „Bitte ein gültiges Datum eingeben.“ once the field is left. A form MUST NOT be saved while a required date field is empty or invalid. A date stored before SHALL be shown as `TT.MM.JJJJ`.

#### Scenario: Type a date
- **WHEN** Anna types 01.07.2030 into „Anreise“ of a new booking and 08.07.2030 into „Abreise“ and saves
- **THEN** the booking runs from 1 to 8 July 2030

#### Scenario: Choose from the calendar
- **WHEN** Anna opens the calendar of „Datum“ in a new transaction and picks a day
- **THEN** the field shows that day as `TT.MM.JJJJ`

#### Scenario: Invalid date
- **WHEN** Anna types 31.02.2030 into „Abrechnungsdatum“ and leaves the field
- **THEN** the field shows „Bitte ein gültiges Datum eingeben.“, and „Speichern“ does not save the statement

#### Scenario: Required date missing
- **WHEN** Anna leaves „Datum“ of a new property cost empty and saves
- **THEN** nothing is saved and the field is marked as missing

#### Scenario: Existing date
- **WHEN** Anna opens a booking from 1 to 8 July 2030
- **THEN** „Anreise“ shows 01.07.2030 and „Abreise“ 08.07.2030

#### Scenario: Safari
- **WHEN** Anna opens the booking dialog in Safari
- **THEN** „Anreise“ and „Abreise“ show the placeholder `TT.MM.JJJJ` and do not stick out of their columns
