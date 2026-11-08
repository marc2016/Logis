# Spec Delta

## Purpose

Agencies are the intermediaries through which vacation rental bookings arrive, e.g. Fewo & Meer, Holidu, VVV Nordhorn or a direct booking. One shared, managed list keeps their names consistent across users and maps the names used in booking mails to them.

## ADDED Requirements

### Requirement: Shared list of agencies
The system SHALL keep one list of agencies shared by all users, shown on the page „Vermittler“. Every signed-in user SHALL be able to see the list and create agencies. An agency MUST have a name of at most 64 characters, and names MUST be unique regardless of case. The list SHALL show for each agency its name, its mail names, the number of its bookings and whether it is archived. The number of bookings MUST only count bookings the user can see.

#### Scenario: Create an agency
- **WHEN** Anna creates the agency „Ferienbüro Nord“
- **THEN** it appears in the list of every user, also in Bert's

#### Scenario: Duplicate name
- **WHEN** Anna tries to create the agency „holidu“ while „Holidu“ exists
- **THEN** the system rejects this with the message „Ein Vermittler mit diesem Namen existiert bereits“

### Requirement: Rename and archive an agency
Every signed-in user SHALL be able to rename and archive or restore an agency. Renaming MUST keep the previous name as a mail name, so that later mails with that name still find the agency. Archived agencies MUST NOT be offered when choosing an agency for a booking, but MUST stay on existing bookings and MUST still be found by their mail names.

#### Scenario: Shorten an imported name
- **WHEN** Anna renames „VVV-Stadt- und Citymarketing Nordhorn e.V.“ to „VVV Nordhorn“
- **THEN** all its bookings show „VVV Nordhorn“, and a later mail naming „VVV-Stadt- und Citymarketing Nordhorn e.V.“ is assigned to „VVV Nordhorn“

#### Scenario: Archived agency
- **WHEN** Anna archives „Airbnb“
- **THEN** the booking dialog no longer offers it, and existing bookings still show „Airbnb“

### Requirement: Mail names
Every signed-in user SHALL be able to add and remove mail names of an agency. A mail name MUST be unique across all agencies regardless of case. An agency's own name SHALL always count as one of its mail names.

#### Scenario: Add a mail name
- **WHEN** Anna adds the mail name „Fewo und Meer“ to „Fewo & Meer“
- **THEN** a mail whose agency reads „Fewo und Meer“ is assigned to „Fewo & Meer“

#### Scenario: Mail name of another agency
- **WHEN** Anna tries to add the mail name „Holidu“ to „Fewo & Meer“
- **THEN** the system rejects this with the message „Dieser Name gehört bereits zu einem anderen Vermittler“

### Requirement: Merge agencies
Every signed-in user SHALL be able to merge one agency into another. Merging MUST move all bookings, unit mappings and mail names, including the merged agency's name, to the remaining agency and delete the merged one. A merge MUST be rejected with the message „Eine Buchungsnummer kommt bei beiden Vermittlern auf derselben Einheit vor“ if it would give two bookings of the same unit the same booking number.

#### Scenario: Merge duplicates
- **WHEN** Anna merges „holidu.de“ into „Holidu“
- **THEN** all bookings of „holidu.de“ show „Holidu“, „holidu.de“ is a mail name of „Holidu“, and „holidu.de“ is gone from the list

### Requirement: Delete an agency
Every signed-in user SHALL be able to delete an agency that has no bookings and no unit mappings. An agency still in use MUST NOT be deleted, and the system MUST reject this with the message „Der Vermittler wird noch verwendet und kann nur archiviert werden“.

#### Scenario: Delete an agency in use
- **WHEN** Anna tries to delete „Holidu“, which has bookings
- **THEN** the system rejects this with the message „Der Vermittler wird noch verwendet und kann nur archiviert werden“

### Requirement: Resolving names from mails
When a recognised booking mail names an agency, the system SHALL assign the agency whose mail names contain that name, regardless of case. If no agency knows the name, the system MUST create a new agency with that name.

#### Scenario: New agency from a mail
- **WHEN** a booking mail of the unknown intermediary „Ferienbüro Süd“ is imported
- **THEN** the agency „Ferienbüro Süd“ exists, and the new booking references it

#### Scenario: Known mail name
- **WHEN** a mail names „VVV-Stadt- und Citymarketing Nordhorn e.V.“ and Anna has renamed that agency to „VVV Nordhorn“
- **THEN** no new agency is created, and the booking references „VVV Nordhorn“
