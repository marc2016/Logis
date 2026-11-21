# Design

## Context

- **Per-user storage:** the only per-user setting store is `core.dashboards` (one JSON row per user for the start page).
- **List columns:** `DataTable` remembers hidden columns per browser in `localStorage` under `logis.table.<tableId>.hidden`, as an array of column IDs. It is used by:
  - the booking list (`vacationrental.bookings`);
  - the inbox (`mail.inbox`);
  - the property finances (`realestate.finances`);
  - the transactions list.
- **Booking form:** `BookingForm` renders kind, unit, dates, booking number, agency, persons, pets, rent, guest and documents. Each field sits in a `Field` wrapper with `data-field`, its source location and the „weicht vom Dokument ab“ hint.

## Goals / Non-Goals

**Goals:**
- A small, general per-user store that later settings reuse without backend work.
- The compact view.
- The list columns moved into the store.

**Non-Goals:**
- A settings page.
- Admin defaults for all users.
- Moving the dashboard tiles into the store: they have their own table and validation.

## Decisions

### D1 — `core.user_preferences`, key-value per user

New migration `V<date>.<time>__core_user_preferences.sql`:

```
core.user_preferences (
  user_id    BIGINT REFERENCES core.users (id) ON DELETE CASCADE,
  key        VARCHAR(100),
  value      JSONB NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL,
  PRIMARY KEY (user_id, key)
)
```

API in a new core package `core/preferences`, for the signed-in user only:
- `GET /api/preferences` returns `{ key: value, ... }`;
- `PUT /api/preferences/{key}` with a JSON body as the value sets one key (upsert);
- `DELETE /api/preferences/{key}` removes one key.

Validation (HTTP 400, „Die Einstellung ist ungültig.“):
- the key matches `^[a-z]+(\.[A-Za-z0-9-]+)+$` and is at most 100 characters;
- the value is at most 4 KB;
- at most 100 keys per user. Updating an existing key never counts against this limit.

**Alternatives considered:**
- **One JSON document per user.** Two tabs changing different settings would overwrite each other. Rejected.
- **Columns on `core.users`.** Every setting would need a migration. Rejected.

### D2 — `usePreference(key, fallback)` in the core

- **Loading:** a React Query hook loads all preferences once per session (`['preferences']`, long `staleTime`).
- **Value:** `usePreference` returns `[value, set]`; the value is the stored one, or `fallback` while there is none or loading failed.
- **Setting:** `set` updates the cache at once (optimistic) and sends `PUT`. On failure the cache keeps the new value for this session and nothing else happens, so the page stays switched (spec „Settings per user“).
- **Before loading:** components render with the fallback until the preferences have loaded. This avoids blocking pages on the request.

### D3 — `DataTable` columns

The key is `core.tableColumns.<tableId>` and the value is the array of hidden column IDs (the same format as today).

On first render with loaded preferences:
- if the key is missing but `localStorage` has the old entry, `DataTable` puts that entry and removes it from `localStorage`;
- reading and writing then go through `usePreference` only.

Test tables (`tableId` like `t1`) work the same way.

### D4 — Compact view

The setting is `vacationrental.bookingView`, either `"full"` (the default) or `"compact"`.

The booking page shows a `ToggleGroup` „Kompakt | Alle Angaben“ above the form for users who may edit. `BookingForm` gets `compact?: boolean`; in compact mode it renders only these `Field`s:
- arrival and departure;
- adults, children and rent, except for owner uses.

Every other field is still rendered when `locations[field]?.differs` is set or the form reports an error for it. Hidden fields keep their state, so saving sends the full booking as today.

The dialog for a new booking does not pass `compact`. The actions below the form (Stornieren, Gastdaten löschen, Löschen) stay visible in both views.

## Risks / Trade-offs

- **The first render shows „Alle Angaben“ for a moment** before the preference has loaded. → Preferences load right after sign-in, and the booking page waits for its own data anyway. Accepted.
- **Two tabs changing the same key.** → The last write wins. Accepted for view settings.
- **Old `localStorage` entries on other devices** are taken over there on first use, but only if the user has not set the key elsewhere already. This is harmless: the stored setting wins.
