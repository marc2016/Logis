# app-shell Specification

## Purpose

The app shell is the shared frame of the web interface. It provides navigation through a left sidebar in which each active module has its own group, and it shows the modules' pages in the content area.

## Requirements

### Requirement: Sidebar with one group per module
The interface SHALL show a sidebar on the left. It MUST contain a section for the core (at least „Übersicht“) and a separate group for each active module with that module's navigation entries. Disabled modules MUST NOT appear in the sidebar.

#### Scenario: Active module appears as a group
- **WHEN** a signed-in user opens the application and `vacationrental` is active
- **THEN** the sidebar shows a group „Ferienvermietung“ with its navigation entries

#### Scenario: Disabled module is missing
- **WHEN** a signed-in user opens the application and `vacationrental` is disabled
- **THEN** the sidebar shows no group „Ferienvermietung“

### Requirement: Collapsible sidebar
The sidebar SHALL collapse to an icon-only bar and expand again. The chosen state MUST persist when the page is reloaded in the same browser.

#### Scenario: Collapse
- **WHEN** the user collapses the sidebar
- **THEN** the sidebar shows only icons, and the content area takes up the freed width

#### Scenario: State persists
- **WHEN** the user has collapsed the sidebar and reloads the page
- **THEN** the sidebar is still collapsed

### Requirement: Navigation to module pages
Clicking a navigation entry SHALL show the corresponding page in the content area without reloading the whole page. The active entry MUST be highlighted in the sidebar. Every page MUST have its own URL that can be opened directly.

#### Scenario: Click an entry
- **WHEN** the user clicks „Belegung“ in the group „Ferienvermietung“
- **THEN** the content area shows the occupancy page, the URL changes accordingly, and „Belegung“ is highlighted

#### Scenario: Open a URL directly
- **WHEN** the signed-in user opens the URL of a module page directly in the browser
- **THEN** the application shows that page inside the shell

#### Scenario: URL of a disabled module
- **WHEN** the user opens the URL of a page belonging to a disabled module
- **THEN** the application shows a "page not found" view inside the shell

### Requirement: Usable on narrow screens
On narrow screens (smartphone width) the sidebar SHALL be hidden by default and open as an overlay via a button.

#### Scenario: Smartphone
- **WHEN** the application is opened in a window of smartphone width
- **THEN** the sidebar is hidden, and a menu button opens it as an overlay

### Requirement: Light appearance
The interface SHALL be shown in a light design only: light backgrounds and dark text. This applies regardless of whether the operating system or browser is set to dark mode.

#### Scenario: Dark mode in the system
- **WHEN** the user opens the application on a device with dark mode enabled
- **THEN** shell, sidebar and content are shown in the light design

### Requirement: German interface
All interface text SHALL be in German.

#### Scenario: Labels
- **WHEN** the user looks at the shell
- **THEN** navigation, buttons and messages are labeled in German

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
