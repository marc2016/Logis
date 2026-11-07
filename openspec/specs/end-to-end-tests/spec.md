# end-to-end-tests Specification

## Purpose

End-to-end tests check the main flows of Logis in a real browser against the shipped stack, on a desktop and a phone screen, and keep images of a commit from being published when a flow is broken.

## Requirements

### Requirement: Main flows covered
The end-to-end suite SHALL test in a real browser: signing in with two-factor setup and recovery codes, recording a booking on a property and seeing it in the calendar and on a statement, importing a booking from a mail server and confirming it, and creating, fetching and revoking a calendar subscription. Every test MUST run on a desktop screen (1280 px wide) and on a phone screen (390 px wide).

#### Scenario: Booking on the phone
- **WHEN** the suite records a booking on the phone screen
- **THEN** it checks that the booking appears as a bar in its month row of the calendar

#### Scenario: Subscription feed
- **WHEN** the suite creates a link without guest data and fetches its address without a session
- **THEN** it parses the feed and finds the booking titled „Belegt“ without the guest name

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
