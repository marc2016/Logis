# Proposal

## Why

The occupancy of a holiday home is only visible inside logis. The owner wants to see it in the calendar app on their phone, and wants to give helpers such as the cleaning service a read-only view without an account and without guest data.

## What Changes

- A user can create subscription links for a vacation rental unit in the calendar („Abonnieren“). Each link has a name and is created either with or without guest data.
- A link is a secret address of an iCalendar feed (`.ics`) that calendar apps (Google, Apple, Outlook) fetch without signing in.
- The feed contains the unit's bookings that are not cancelled, from 12 months back without a limit into the future, as all-day events from arrival through the departure day.
- Without guest data, every event is titled „Belegt“. With guest data, the title is the guest name and the booking number.
- The full link is shown once when it is created. Links can be revoked. A link stops working when its creator loses access to the unit.

## Capabilities

### New Capabilities

- `calendar-subscription`: Read-only iCalendar feeds of a unit's occupancy behind secret, revocable links.

### Modified Capabilities

(none)

## Impact

- **Backend:** module `vacationrental`: a table for subscription links in schema `vacationrental`, endpoints to list, create and revoke links, and a public feed endpoint that is reached without a session. The security configuration permits the feed path. No new library: the feed is plain text.
- **Frontend:** a button „Abonnieren“ on the calendar page and a dialog with the links of the chosen unit.
- **Security:** whoever knows a link can read its feed. Tokens are long and random, stored only as a hash, and checked against the creator's current access on every request.
- **Assumptions** (proposed during exploration, not explicitly confirmed; easy to change):
  - The calendar button is the only place to manage links.
  - Bookings still to be reviewed get a „?“ in their title.
  - The full link is shown only right after creating it, because the secret is not stored in readable form; a lost link is replaced by creating a new one.
