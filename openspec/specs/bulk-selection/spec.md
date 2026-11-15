# bulk-selection Specification

## Purpose

Bulk selection lets a user pick several entries of a list and apply one action to all of them, with each entry following the usual rules and a clear report of what did not work.

## Requirements

### Requirement: Select entries in a list
Lists that offer bulk actions SHALL show a checkbox per row and, in the header, a checkbox that selects or clears all rows of the current page. Clicking a checkbox with Shift held SHALL select the range from the last clicked row. The selection SHALL be limited to the current page and MUST be cleared when the filter, the sort order or the page changes. Selecting a row MUST NOT open it. Rows the user may not change SHALL NOT offer a checkbox, and a list without such rows SHALL show no checkboxes at all.

#### Scenario: Select a page
- **WHEN** Anna ticks the header checkbox of the booking list showing 50 bookings
- **THEN** all 50 bookings of the page are selected and the action bar shows „50 ausgewählt“

#### Scenario: Range with Shift
- **WHEN** Anna ticks the third row and then, holding Shift, the eighth row
- **THEN** rows three to eight are selected

#### Scenario: Filter clears the selection
- **WHEN** Anna has selected 5 bookings and changes the property filter
- **THEN** no booking is selected

#### Scenario: Viewer
- **WHEN** Dieter, as `VIEWER`, opens the booking list
- **THEN** he sees no checkboxes and no action bar

### Requirement: Run a bulk action
While entries are selected, an action bar SHALL show their number and the actions that apply to them. Deleting MUST ask for confirmation naming the number of entries. A bulk action SHALL process every selected entry on its own, under the same rules and rights as the single action. Entries that fail MUST NOT stop the others. Afterwards the system SHALL report how many entries succeeded and, grouped by message, how many failed and why. The failed entries SHALL stay selected, the others SHALL be deselected, and the list SHALL show the new state.

#### Scenario: Partial failure
- **WHEN** Anna deletes 10 selected bookings, 2 of which are on a statement
- **THEN** 8 bookings are deleted, she sees „8 gelöscht, 2 nicht: Die Buchung ist abgerechnet und kann nur storniert werden“, and the 2 remain selected

#### Scenario: Confirmation
- **WHEN** Anna chooses „Löschen“ for 12 selected mails
- **THEN** she is asked „12 Einträge löschen?“ and nothing is deleted until she confirms
