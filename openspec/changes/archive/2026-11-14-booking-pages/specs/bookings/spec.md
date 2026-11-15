## ADDED Requirements

### Requirement: Booking page
Every booking SHALL open as a page of its own with an address containing the booking's id. The booking list, the calendar and the inbox SHALL open bookings there, and the browser's back button SHALL return to the previous view with its filters or year. Recording a new booking SHALL stay a dialog.

The page SHALL have a header with the most important details: guest name, period with the number of nights, unit, agency, expected rent, status and, if present, the marker „zu prüfen“ or the proposed change. The fields SHALL be editable on the page by users with at least `EDITOR`.

For users with at least `EDITOR`, the page SHALL show the booking's sources next to the data, one tab per source. Focusing or clicking a field with a location SHALL show its source and highlight the location. Clicking a highlighted location SHALL focus its field. On narrow screens the sources SHALL be a separate tab of the page. A booking that does not exist or is not visible MUST show „Buchung nicht gefunden“.

#### Scenario: Open from the list
- **WHEN** Anna clicks booking 250903 in the booking list filtered by „Haus Seeufer“
- **THEN** the booking page opens at its own address, and the back button returns to the list filtered by „Haus Seeufer“

#### Scenario: Check the arrival
- **WHEN** Anna clicks the arrival field of the imported booking 250903
- **THEN** the source panel shows the confirmation mail with „23.12.2026“ highlighted next to „Anreise“

#### Scenario: Shared address
- **WHEN** Anna opens the address of booking 250903 in a new browser tab
- **THEN** the same booking page appears

#### Scenario: Unknown booking
- **WHEN** Carla opens the address of a booking of a unit she has no role on
- **THEN** the page shows „Buchung nicht gefunden“

## MODIFIED Requirements

### Requirement: Agency and pets
A booking SHALL optionally reference an agency from the shared list of agencies, and SHALL carry the number of pets. Both SHALL be shown in the booking form, on the booking page and in the dialog for a new booking. The form SHALL offer the active agencies for selection and SHALL allow creating a new agency from the form. A booking with an archived agency SHALL keep and show it. The agency SHALL be a filterable and sortable column „Vermittler“ of the booking list, and the list MUST NOT show a channel column.

#### Scenario: Agency from an imported mail
- **WHEN** a booking is created from a Holidu mail
- **THEN** its agency is "Holidu"

#### Scenario: Create an agency while recording a booking
- **WHEN** Anna types „Ferienbüro Nord“ into the agency field of the booking dialog, chooses to create it and saves the booking
- **THEN** the agency „Ferienbüro Nord“ exists in the shared list, and the booking references it

#### Scenario: Existing data after the update
- **WHEN** the system is updated with a booking that had the free-text agency „Fewo & Meer“ and one that had no agency but the channel `DIRECT`
- **THEN** the first references the agency „Fewo & Meer“ and the second the agency „Direkt“

### Requirement: Bookings awaiting review
A booking created from a mail SHALL have the review state „zu prüfen“ until a user with at least `EDITOR` confirms or discards it. It MUST occupy the calendar like a planned booking and MUST be marked in the booking list, the calendar and the header of the booking page, which SHALL offer confirming and discarding. Confirming SHALL make it an ordinary booking, possibly after editing. Discarding SHALL delete it together with its documents, mark the mail in the inbox as discarded and return to the previous view.

#### Scenario: Confirm an imported booking
- **WHEN** Anna opens the booking 250903 „zu prüfen“, corrects the guest name and confirms
- **THEN** the booking is an ordinary planned booking without the marker

#### Scenario: Filter bookings to review
- **WHEN** Anna filters the booking list by „zu prüfen“
- **THEN** they see only bookings and changes awaiting review

### Requirement: Proposed changes
A booking SHALL carry at most one proposed change from a mail: new values for period, guests, pets, expected rent and guest data, or a cancellation. The booking page MUST show old and new values, and clicking a new value SHALL show the proposing mail with the value highlighted. Applying SHALL take over the values or cancel the booking, subject to the usual rules such as the overlap check. Discarding SHALL keep the booking unchanged. A newer proposal MUST replace an older one.

#### Scenario: Apply a cancellation
- **WHEN** Anna applies the proposed cancellation of booking 250905
- **THEN** the booking is cancelled and the calendar period is free

#### Scenario: Change would overlap
- **WHEN** a proposed change moves a booking onto an occupied period and Anna applies it
- **THEN** the system rejects this with the message „Die Einheit ist in diesem Zeitraum bereits belegt“ and the proposal stays

#### Scenario: Where the new departure comes from
- **WHEN** Anna clicks the proposed new departure of booking 250905
- **THEN** the source panel shows the change mail with the new departure highlighted
