# Proposal

## Why

When a guest extends a stay, Fewo & Meer sends „Information über eine Verlängerungsbuchung“, a plain-text notice from agency staff. It names the property, the original period and the extension period. It carries no booking number, so today no rule recognises it, and the AI cannot act on it either, because it needs a booking number. The booking stays at its old dates until the owner notices and corrects it by hand. Two such notices were among the unrecognised mails of a real export.

Changes that carry a booking number already work: any recognised mail with a known number and new dates becomes a proposed change, e.g. Holidu/DS „Änderung zur Buchung“. Extensions are the gap.

## What Changes

- The Fewo & Meer rules recognise an extension notice by its text: „Verlängerungsbuchung“, „Zeitraum ursprüngliche Buchung“ and „Verlängerung“, with the property and the number of adults and children. Forwarded notices count as well.
- The notice is matched to the guest booking on the mapped unit whose period is exactly the original period, the same way agency block notices are matched.
- The booking's new period runs from the earlier arrival to the later departure of both periods. It is proposed as a change for review, together with a different number of adults or children. A booking that is not yet reviewed takes the new values directly.
- If a booking already has the new period, the notice is only attached. If no booking has the original period, the mail stays as „nicht zugeordnet“.
- Extension notices are recognised only by the rules, never by the AI.
- New mail kind „Verlängerung“ in the inbox and in the export index.

## Capabilities

### New Capabilities

### Modified Capabilities
- `booking-mails`: new requirement „Extension notices“.

## Impact

- **Backend** (`modules/vacationrental`):
  - `FewoUndMeerParser` gets the extension rule;
  - `ParsedMail` gets the new type `EXTENSION` and the original period;
  - `ImportEngine` gets the matching by period;
  - `MailRecognizer` stops the AI from producing the type;
  - `BookingMailHandler` gets the kind label.
- **Frontend:** the kind label „Verlängerung“ in `mailKindLabel`.
- **Database:** none; no migration. Mails stored as „Nicht erkannt“ change through „Erneut verarbeiten“.
- **Out of scope:**
  - TOMAS „Umbuchung“ notices, whose data is only in a PDF;
  - shortened stays.
