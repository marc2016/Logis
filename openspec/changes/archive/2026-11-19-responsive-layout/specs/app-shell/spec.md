# Spec Delta

## ADDED Requirements

### Requirement: Controls fit the content area
Pages and dialogs SHALL fit the width available to them on phone, tablet and desktop screens, with the sidebar open or collapsed. Controls MUST NOT overlap each other, MUST NOT stick out of their page or dialog, and inputs MUST be wide enough to show their value or placeholder date in full. Wide tables MAY scroll sideways inside their own frame.

#### Scenario: Bookings filter on a tablet
- **WHEN** a user opens „Buchungen“ in a window 1024 px wide with the sidebar open
- **THEN** all filters are fully visible, none overlaps another, and „Von“, „Bis“ and „Suche“ show their full value or placeholder

#### Scenario: Sidebar collapsed
- **WHEN** the user collapses the sidebar on the same page
- **THEN** the filters use the freed width and still do not overlap

#### Scenario: Sharing on the phone
- **WHEN** a user opens „Freigeben“ on a property in a window 390 px wide
- **THEN** each share row with its role select and remove button, the user picker, the role select and „Einladen“ lie inside the dialog

### Requirement: Side-by-side layouts depend on the content width
A page that shows two panels side by side SHALL do so only when the content area is wide enough for both. Below that width it MUST show one panel at a time, with a way to switch between them.

#### Scenario: Booking page on a tablet
- **WHEN** a user opens a booking imported from a mail in a window 1024 px wide with the sidebar open
- **THEN** the page shows the tabs „Daten“ and „Quelle“, and the form uses the full content width

#### Scenario: Booking page on a wide screen
- **WHEN** the same booking is opened in a window 1440 px wide
- **THEN** form and source are shown side by side
