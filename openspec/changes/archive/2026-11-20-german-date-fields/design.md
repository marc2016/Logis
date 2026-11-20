# Design

## Context

`components/DateField.tsx` already exists and is used by the inbox filters, the mailbox's „Ältere Mails ab“, the mail export and, since the last fix, the bookings and transactions filters. Its behaviour:
- a text input with placeholder `TT.MM.JJJJ`, plus a calendar button;
- it reports a valid ISO date as soon as the typed date is complete, and `''` otherwise;
- an invalid date shows its message once the field is left;
- an optional `max` sets the latest date.

It has no notion of „required“. The nine form fields rely on the native `required` attribute of `<input type="date">`, so the browser blocks submitting while one is empty.

## Goals / Non-Goals

**Goals:**
- The same date field everywhere.
- Required date fields keep blocking the form, now also for invalid typed text.

**Non-Goals:**
- New date rules, e.g. departure after arrival: the existing checks stay as they are.
- Changing any API.

## Decisions

### D1 — Required through the browser's own constraint validation

`DateField` gets `required?: boolean` and passes it to its text input, so an empty field blocks `form.submit` exactly as before, with the browser's hint.

For typed text that is not a valid date (or after `max`), the input gets `setCustomValidity(message)` with the same German message the field shows. The browser then refuses to submit and points to the field. When the text becomes valid or empty, the custom validity is cleared.

**Alternative:** every form checks its dates itself before saving. Rejected: nine forms with the same check, and the browser already does it for every other required field.

### D2 — Same ids and labels

Each field keeps its `id`, so `<Label htmlFor>` and the booking form's `Field` wrapper keep working: `data-field` and the source passage shown on focus. The calendar button is named after the field („Kalender öffnen: Anreise“).

### D3 — Tests type German dates

- Frontend tests that read `toHaveValue('2026-11-09')` read `09.11.2026`.
- e2e tests that fill „Anreise“/„Abreise“ with ISO dates use a helper `germanDate(iso)` in `e2e/support/data.ts`.
- `layout.spec.ts` checks the booking dialog at phone and tablet widths.

## Risks / Trade-offs

- **Browsers report custom validity differently.** → The field also shows its own message below, so the reason is visible in every browser.
- **A typed but incomplete date while saving.** → The field is invalid, so the form is not saved, which is the same as an empty required field.
