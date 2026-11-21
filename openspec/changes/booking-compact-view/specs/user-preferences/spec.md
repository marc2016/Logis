## Purpose

Remembers a user's choices in the interface, such as views and list columns, per user and on every device. The store is built so that later settings need no new storage.

## ADDED Requirements

### Requirement: Settings per user
The system SHALL keep settings per user under a key, so that a setting chosen on one device applies on every device the user signs in with.

A user MUST only read and change their own settings.

Without a stored setting, the interface SHALL use the setting's default. A setting SHALL be saved as soon as the user changes it.

If saving fails, the change SHALL still apply on the current page, without being remembered.

#### Scenario: Another device
- **WHEN** Anna chooses „Kompakt“ on her laptop and later opens a booking on her phone
- **THEN** the booking shows „Kompakt“

#### Scenario: Own settings only
- **WHEN** Anna changes a setting
- **THEN** Bert's settings stay unchanged, and Bert cannot read Anna's

#### Scenario: No setting yet
- **WHEN** a new user opens a booking
- **THEN** it shows „Alle Angaben“

### Requirement: Limits of settings
A key MUST consist of a module part and a name, such as `vacationrental.bookingView`. A value MUST be valid JSON of at most 4 KB, and a user MUST have at most 100 settings.

A request outside these limits MUST be rejected with HTTP 400 and „Die Einstellung ist ungültig.“.

Settings of an inactive module SHALL be kept.

#### Scenario: Value too large
- **WHEN** a setting of 5 KB is sent
- **THEN** it is rejected with „Die Einstellung ist ungültig.“

### Requirement: Remembered list columns
The column choice of every list with a column menu SHALL be a setting of the user, per list. A choice stored in the browser before SHALL be taken over the first time the list opens without a stored setting, and then removed from the browser.

#### Scenario: Columns on another device
- **WHEN** Anna hides „Vermittler“ in the booking list and later opens the list on another device
- **THEN** „Vermittler“ is hidden there as well

#### Scenario: Taken over from the browser
- **WHEN** Anna had hidden „Absender“ in the inbox before settings were stored per user, and opens the inbox
- **THEN** „Absender“ stays hidden, the choice is stored as her setting, and the browser no longer holds it
