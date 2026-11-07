# Design

## Context

logis requires a session with two-factor authentication for every `/api/**` request (`SecurityConfiguration`). Calendar apps cannot sign in; they poll a URL every few hours. Occupancy data comes from bookings in the module `vacationrental`, access is checked per unit through the sharing model (`RentalAccess`). Guest names are visible from `VIEWER` on (spec `bookings`, „Guest data“). Motivation is in proposal.md.

## Goals / Non-Goals

**Goals:**
- A read-only `.ics` feed per unit behind a secret link, with or without guest names.
- No way to read anything else with a link, and no leak of contact details.

**Non-Goals:**
- Writing back from the calendar app, CalDAV, or feeds across several units.
- Push updates; calendar apps decide how often they poll.
- Admin management of other users' links.

## Decisions

### D1: Link entity and token

Table `vacationrental.calendar_links`: `id`, `unit_id`, `created_by` (user id), `name`, `with_guests`, `token_hash` (unique), `created_at`. The token is 32 random bytes (`SecureRandom`), Base64URL-encoded (43 characters). Only its SHA-256 hash is stored; lookup is by hash, so no timing-sensitive comparison of secrets is needed. Revoking deletes the row.

- *Alternative:* store the token encrypted so it can be shown again. Rejected: one more key to manage, and creating a new link is cheap.

### D2: Endpoints

- `GET /api/vacationrental/calendar-links?unitId=` lists the caller's links of the unit (without token).
- `POST /api/vacationrental/calendar-links` `{unitId, name, withGuests}` checks that the caller can see the unit and returns the link with its feed `path` once (the frontend prefixes its own origin).
- `DELETE /api/vacationrental/calendar-links/{id}` only for the creator.
- `GET /api/ical/{token}.ics` is public: permitted for `GET` in `SecurityConfiguration` before the `/api/**` rule, no session, no CSRF. It hashes the token, loads the link, checks the creator's current effective role on the unit through the sharing model, and returns `text/calendar; charset=utf-8`. Any failure returns `404` with an empty body.

The feed path lives under `/api/` because nginx already forwards it to the backend; no proxy change is needed. The session filter leaves requests without a session alone, as for `/api/auth/**`.

### D3: Feed format

Hand-written iCalendar (RFC 5545), lines folded at 75 octets, text escaped (`\\`, `;`, `,`, newline):

```
BEGIN:VCALENDAR
VERSION:2.0
PRODID:-//logis//calendar//DE
X-WR-CALNAME:<link name>
BEGIN:VEVENT
UID:booking-<id>@logis
DTSTAMP:<now UTC>
DTSTART;VALUE=DATE:<arrival>
DTEND;VALUE=DATE:<departure + 1 day>
SUMMARY:Belegt | <guest> (<external ref>) [?]
TRANSP:OPAQUE
END:VEVENT
END:VCALENDAR
```

`DTEND` is exclusive, so departure + 1 shows the event through the departure day. Without guest data `SUMMARY` is „Belegt“ (plus „ ?“ when to be reviewed) and nothing else about the guest is written. No `DESCRIPTION`, `LOCATION` or attendees.

Range: bookings of the unit, not cancelled, with departure on or after today minus 12 months.

### D4: Frontend

A button „Abonnieren“ next to „Heute“ opens a dialog for the chosen unit: list of the caller's links (name, „mit Gastdaten“ / „ohne Gastdaten“, created, „Widerrufen“), and a form (name, switch „Gastdaten“, „Link erstellen“). After creating, the dialog shows the URL with „Kopieren“ and a hint that it is shown only once, plus an `webcal://` link for opening it on the phone.

## Risks / Trade-offs

- [Leaked link] → Read-only, one unit, revocable, no contact data; the „ohne Gastdaten“ option for links given to others.
- [Token in proxy and server logs] → URLs are logged by reverse proxies. Accepted for a read-only feed; the token reveals no other data.
- [Polling load] → One small query per poll; calendar apps poll every few hours.
- [Time zones] → All-day dates (`VALUE=DATE`) have no time zone, so no conversion issues.
