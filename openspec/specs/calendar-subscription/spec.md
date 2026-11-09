# calendar-subscription Specification

## Purpose

Calendar subscriptions let users follow the occupancy of a vacation rental unit in their calendar app, and give helpers such as a cleaning service a read-only view without an account. Each subscription is a secret, revocable link to an iCalendar feed, with or without guest names.

## Requirements

### Requirement: Subscription links
A user who can see a vacation rental unit SHALL be able to create subscription links for it, each with a name and the choice whether guest data is included, and SHALL be able to list and revoke their own links of that unit. A link MUST be a secret, randomly generated address that cannot be derived from other data. The system MUST NOT store the secret in readable form, so the full address SHALL be shown only right after creating the link.

#### Scenario: Create a link for the cleaning service
- **WHEN** Anna creates the link „Reinigung“ without guest data for "Haus Seeufer"
- **THEN** they see its address once and the link appears in the list of the unit as „ohne Gastdaten“

#### Scenario: Revoke a link
- **WHEN** Anna revokes the link „Reinigung“
- **THEN** fetching its address fails and the link disappears from the list

#### Scenario: Viewer creates a link
- **WHEN** Dieter, as `VIEWER` of "Haus Seeufer", creates a link with guest data
- **THEN** the link is created, because viewers may see guest names

### Requirement: Calendar feed
Fetching the address of a valid link without signing in SHALL return an iCalendar feed of the unit's bookings that are not cancelled and whose departure lies at most 12 months in the past. Each booking MUST be one all-day event from the arrival date through the departure date, and its identifier MUST stay the same when the booking changes. Without guest data the title MUST be „Belegt“ and the event MUST NOT contain any guest data or booking number. With guest data the title SHALL be the guest name and the booking number, and MUST NOT contain address, phone or email. A booking still to be reviewed SHALL be marked with „?“ in the title.

#### Scenario: Feed without guest data
- **WHEN** the cleaning service's calendar app fetches the link „Reinigung“ while booking "250905" of Erika Musterfrau runs from 2026-11-06 to 2026-11-08
- **THEN** the feed contains an all-day event „Belegt“ from 6 through 8 November and the name „Erika Musterfrau“ appears nowhere in it

#### Scenario: Feed with guest data
- **WHEN** Anna's calendar app fetches their link with guest data
- **THEN** the same booking is titled „Erika Musterfrau (250905)“

#### Scenario: Cancelled booking
- **WHEN** a booking is cancelled
- **THEN** it is no longer in the feed

### Requirement: Feed access follows the creator
The system MUST deliver a feed only while its link exists and its creator still has an effective role on the unit. Otherwise the request MUST fail without revealing whether the link ever existed.

#### Scenario: Creator loses access
- **WHEN** the sharing of "Haus Seeufer" with Dieter is removed
- **THEN** Dieter's links of that unit no longer deliver a feed

#### Scenario: Unknown address
- **WHEN** someone fetches a feed address with a made-up secret
- **THEN** the request fails with the same response as for a revoked link
