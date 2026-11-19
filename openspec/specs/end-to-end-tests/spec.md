# end-to-end-tests Specification

## Purpose

End-to-end tests check the main flows of Logis in a real browser against the shipped stack, on a desktop and a phone screen, and keep images of a commit from being published when a flow is broken.

## Requirements

### Requirement: Main flows covered
The end-to-end suite SHALL test in a real browser one journey per user-facing area:
- signing in with two-factor setup and recovery codes, with a passkey, leaving an unfinished sign-in, and the lockout after failed attempts;
- changing one's own password and recovery codes;
- creating, disabling and resetting a user, and managing categories as an admin;
- creating owners and accounts, entering transactions and transfers with correct balances, and filtering and bulk-assigning transactions;
- creating a property with units and costs and seeing its finances;
- sharing a property with a viewer and a unit with an editor, with what each of them sees;
- recording a booking on a property and seeing it in the calendar and on a statement;
- importing a booking from a mail server and confirming it;
- applying a change proposed by a mail on the booking page;
- bulk actions on bookings and statements, and assigning a unit to several inbox mails;
- renaming and merging agencies, and the evaluation of a unit;
- creating, fetching and revoking a calendar subscription.

Every test MUST run on a desktop screen (1280 px wide) and on a phone screen (390 px wide).

#### Scenario: Booking on the phone
- **WHEN** the suite records a booking on the phone screen
- **THEN** it checks that the booking appears as a bar in its month row of the calendar

#### Scenario: Subscription feed
- **WHEN** the suite creates a link without guest data and fetches its address without a session
- **THEN** it parses the feed and finds the booking titled „Belegt“ without the guest name

#### Scenario: What a viewer sees
- **WHEN** the suite shares a property with a second user as viewer and opens a booking as that user
- **THEN** it sees the guest name but no address, phone or email, and no button to change the booking

#### Scenario: Balances after a transfer
- **WHEN** the suite transfers 300,00 € from one account to another
- **THEN** the balance of the first account drops and that of the second rises by 300,00 €

### Requirement: Tested against the shipped stack
The suite MUST run against the stack from `compose.yml` with the images built from the same commit, including the web server in front of the backend. Each test MUST create its own users and data and MUST NOT depend on other tests or their order.

#### Scenario: Tests in any order
- **WHEN** a single test is run on its own against a fresh stack
- **THEN** it passes the same as in the full run

### Requirement: Gate for published images
The workflow MUST NOT publish images of a commit whose end-to-end suite failed. For a failed test, the workflow SHALL keep its screenshot and trace as a run artifact.

#### Scenario: Broken flow
- **WHEN** a commit breaks the calendar subscription dialog
- **THEN** the end-to-end job fails, no image is published, and the run offers the screenshot of the failed step

### Requirement: Coverage overview
The repository SHALL contain an overview that names, for every capability in the specs, the end-to-end journeys that cover it and the test layer that covers what the suite does not. A capability without end-to-end coverage MUST say why. A change that adds a user-facing journey SHALL update the overview.

#### Scenario: Module switched off
- **WHEN** a reader looks up the capability `module-system` in the overview
- **THEN** they find that it is not covered end to end because it needs another stack configuration, and which backend and frontend tests cover it

### Requirement: Layout check on phone and tablet
The end-to-end suite SHALL open the main list pages, a booking page and the sharing dialog at phone (390 px), tablet (820 px) and tablet landscape (1024 px) width with the sidebar open, and fail when interactive controls overlap each other or extend past the window or their dialog.

#### Scenario: Overlapping filters
- **WHEN** two filter controls on „Buchungen“ overlap at 1024 px
- **THEN** the layout check fails and names the page, the width and both controls

#### Scenario: Wide table
- **WHEN** a table is wider than the content area but scrolls inside its own frame
- **THEN** the layout check does not fail
