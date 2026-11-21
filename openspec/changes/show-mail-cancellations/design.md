# Design

## Context

See proposal.md for the motivation. Observed in the code:

- `ImportEngine.cancel` stores `BookingProposal.cancellation` on the booking and returns `CHANGE_PROPOSED` with the detail „Stornierung“. `Booking.setProposal` keeps the review state `NEW` when the booking is not confirmed yet, so the booking has state `NEW` and a proposal at once.
- `ReviewPanel` returns the „Aus E-Mail übernommen“ notice for `NEW` before it looks at `proposal`, so the cancellation is never rendered. After „Bestätigen“ the state becomes `CHANGE` and the cancellation appears as a second step.
- `BookingService.applyProposal` sets `cancelled` and clears the proposal but leaves the state `NEW`: a cancelled booking would stay „zu prüfen“.
- The list (`BookingView`) already carries `proposal`; the calendar (`OccupancyService.CalendarBooking`) only carries `reviewState`.
- Only cancellations can be proposed on a `NEW` booking: `ImportEngine.update` writes changes directly into unconfirmed bookings („Angaben ergänzt“).

## Goals / Non-Goals

**Goals:**
- A reported cancellation is visible in every place that marks a booking „zu prüfen“.
- One decision settles an unconfirmed booking the agency has cancelled.

**Non-Goals:**
- Cancelling automatically without the owner's decision.
- Changes to recognition, matching or the order in which fetched mails are processed.
- A list filter for reported cancellations.

## Decisions

**D1 – Keep the owner's decision, do not cancel automatically.** Every mail effect on a guest booking is reviewed today, and a wrongly matched cancellation would silently free a period. Alternative considered: cancel unconfirmed bookings directly, like extensions update them directly. Rejected because a cancellation is not a completion of the booking's data and the owner asked to see it, not to have it applied.

**D2 – Derive the marker from the stored proposal.** „Stornierung gemeldet“ is shown when `proposal.type == CANCELLATION`. No new column and no new review state, so no migration, and bookings that already carry such a proposal show it at once. Alternative: a review state `CANCELLATION`. Rejected: it would need a data migration and every `reviewState` switch in backend and frontend to change.

**D3 – Calendar flag.** `CalendarBooking` gets a boolean `cancellationReported` (also in the frontend type). `CalendarGrid` adds „Stornierung gemeldet“ to the bar's `aria-label` and title, sets `data-cancellation-reported`, and strikes the label through; the dashed review border stays. The booking keeps occupying its period until the cancellation is applied.

**D4 – Review panel.** In `ReviewPanel`, the branch for a cancellation proposal comes before the `NEW` branch. For `NEW` it renders the title „Stornierung per E-Mail – bitte prüfen“, the text from the spec and three buttons: „Stornierung übernehmen“ (`proposal/apply`), „Stornierung verwerfen“ (`proposal/discard`), „Buchung verwerfen“ (`discard`, closes). For confirmed bookings the panel stays as it is („Stornierung übernehmen“, „Verwerfen“). `BookingPage` opens the cancellation mail's source tab when the panel is shown, using the proposal's source that `BookingSources.setProposal` already records.

**D5 – Applying ends the review.** `applyProposal` sets the review state to `NONE` when it applies a cancellation to a `NEW` booking. A cancelled booking has nothing left to confirm; reinstating it later makes it an ordinary booking. `discardProposal` already leaves `NEW` untouched.

**D6 – Review mode.** `ReviewBar`/`reviewQueue` advance when the returned booking has `reviewState == NONE` or was deleted. „Stornierung verwerfen“ returns a booking that is still `NEW`, so the queue stays on it. This is the existing rule; a test pins it for the new buttons.

**D7 – Bulk confirm.** The bulk action in `BuchungenPage` checks `proposal` before the review state and skips with the existing message. The single `confirm` endpoint stays unchanged, because the booking page no longer offers „Bestätigen“ while a cancellation is reported.

## Risks / Trade-offs

- [Three buttons in one notice] → „Stornierung übernehmen“ is the primary button, the other two are outline buttons; the labels name their object.
- [Existing cancelled bookings still in state `NEW`, created by confirming in the wrong order before this change] → not migrated; they can be confirmed as before. Low count, and no data is lost.
- [Calendar clients that ignore the new flag] → the field is additive.

## Migration Plan

No schema or data migration. Deploy backend and frontend together; rollback is the previous images.
