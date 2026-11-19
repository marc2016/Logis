# Proposal

## Why

When the owner writes to Fewo & Meer, for example to request an owner block, the agency's ticket system answers with an automatic receipt: „vielen Dank für Ihre E-Mail … Ticket-ID: …“. These receipts carry no booking data. Today they stay in the inbox as „Nicht erkannt“; a real export had about 50 of them among about 400 unrecognised mails. When AI is enabled, each of them is also sent to the AI for nothing.

## What Changes

- The booking mail rules recognise Fewo & Meer ticket receipts and mark them „Ignoriert“ with the kind „Eingangsbestätigung“ and the ticket ID as reference. They do this before any other rule and before the AI.
- Replies from agency staff in the same ticket thread (same „[#…]“ subject prefix, no receipt text) are not affected and stay as today.
- Receipts already in the inbox change when the user runs „Erneut verarbeiten“ on them. There is no automatic change of stored mails.

## Capabilities

### New Capabilities

### Modified Capabilities
- `booking-mails`: new requirement „Ticket receipts“.

## Impact

- **Backend:** `modules/vacationrental`:
  - the Fewo & Meer rules get the receipt check;
  - `BookingMailHandler` reports the result `IGNORED` with kind `TICKET_RECEIPT`.
- **Frontend:** the kind label „Eingangsbestätigung“ in `mailKindLabel`. If `mail-export-pseudonymization` is in place, the label also goes into the backend labels.
- **Database:** no schema change, no migration. Stored mails change only through „Erneut verarbeiten“.
