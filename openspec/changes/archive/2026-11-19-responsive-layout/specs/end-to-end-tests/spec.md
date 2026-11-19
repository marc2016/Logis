# Spec Delta

## ADDED Requirements

### Requirement: Layout check on phone and tablet
The end-to-end suite SHALL open the main list pages, a booking page and the sharing dialog at phone (390 px), tablet (820 px) and tablet landscape (1024 px) width with the sidebar open, and fail when interactive controls overlap each other or extend past the window or their dialog.

#### Scenario: Overlapping filters
- **WHEN** two filter controls on „Buchungen“ overlap at 1024 px
- **THEN** the layout check fails and names the page, the width and both controls

#### Scenario: Wide table
- **WHEN** a table is wider than the content area but scrolls inside its own frame
- **THEN** the layout check does not fail
