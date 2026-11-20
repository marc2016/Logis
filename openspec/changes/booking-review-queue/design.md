# Design

## Context

**Booking page:** `BookingPage` (`/ferienvermietung/buchungen/:id`).
- It shows the `ReviewPanel` for bookings with `reviewState` NEW or CHANGE.
- `ReviewPanel` posts `confirm`, `discard`, `proposal/apply` or `proposal/discard` and then calls `onChanged(next)`. The page navigates back only when the booking is gone (discard).
- The back link comes from the location state `back`, the list's address with its filters.

**Booking list and API:**
- The list keeps its filters in the address.
- The API filters with `review=true` and sorts with `sort=arrival,asc`, at most 100 per page.

**Form:** `BookingForm` saves with „Speichern“ and has no notion of unsaved edits.

**Start page:** the dashboard's „Buchungen prüfen“ links to `/ferienvermietung/buchungen?review=true` (module `todoSources`).

## Goals / Non-Goals

**Goals:** click through the bookings to review on the booking page, with the existing review panel and sources.

**Non-Goals:**
- A separate review screen.
- Keys for decisions.
- Changes to the review rules or the API.

## Decisions

### D1 — The queue lives in the address

The review mode is the booking page with `?pruefen=1` plus the list's filter parameters, e.g. `/ferienvermietung/buchungen/57?pruefen=1&unitId=7`. The queue is fetched live with `useBookings({...filters, review: true, sort: 'arrival,asc', size: 100})`:
- the position is the current booking's index in it;
- the count is `totalItems`.

A reload or a shared link keeps the mode. Because the queue is live, a decided booking simply drops out.

**Alternative:** freeze the list of IDs in the location state when the mode starts. Rejected: it would not survive a reload, and it would show bookings that someone already decided on in another tab.

### D2 — Moving on

The page remembers the arrival and ID of the current booking:
- **„Weiter“** opens the queue's next entry after the current position.
- **„Zurück“** opens the previous one.
- **After a decision**, the queue is refetched and the first entry that comes after the decided booking in the queue order (arrival, then ID) opens. Because a decided booking leaves the queue, this is the booking that was next.
- **Empty queue** → „Alles geprüft.“ with a link to the list and its filters.

`ReviewPanel` gets an `onDecided` callback for all decisions. Outside the review mode, the behaviour stays as today.

### D3 — Entry points

**Booking list:** when the current filters match bookings to review, the header shows „Prüfen (n)“ next to „Buchung“. The count comes from a `review=true&size=1` query with the same filters. The button opens `/ferienvermietung/pruefen?<filters>`.

**Start route:** `/ferienvermietung/pruefen` is a new module route. It:
- loads the queue;
- replaces itself with the first booking in review mode;
- shows „Alles geprüft.“ when the queue is empty.

The dashboard's todo source links there.

### D4 — Keys and unsaved edits

**Keys:** ← and → are handled on `window` only in review mode, and only when the event target is not an `input`, `textarea`, `select`, `[contenteditable]` or an open dialog/popover. No other keys are bound.

**Unsaved edits:**
- `BookingForm` reports `dirty` (current values differ from the loaded booking) through an `onDirtyChange` prop.
- Before moving by „Zurück“, „Weiter“ or an arrow key, a dirty form opens a dialog: „Ungespeicherte Änderungen verwerfen?“ with „Verwerfen“ and „Abbrechen“.
- After a decision, the page moves on without asking. „Bestätigen“ of a new booking uses the saved values, so unsaved edits are lost; the panel's text already says to save first. The dialog is not shown here, to keep the flow fast.

### D5 — Layout

- **Navigation:** a bar above the header with the back link, „x von n“, „Zurück“ and „Weiter“. On phones it wraps, and the buttons stay at least 40 px high.
- **Single booking:** the link to the list shows as „Zur Liste“ in review mode.

## Risks / Trade-offs

- **More than 100 bookings to review.** → The queue shows the first 100 by arrival, and the count uses `totalItems`, e.g. „1 von 140“. Deciding moves the window forward. Paging beyond 100 by „Weiter“ alone is not supported, which is accepted for now.
- **Unsaved edits lost on „Bestätigen“.** → This is the same as today; the dialog covers navigation only. Worth revisiting if users trip over it.
