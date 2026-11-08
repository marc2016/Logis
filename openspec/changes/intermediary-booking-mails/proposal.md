# Proposal

## Why

Bookings sold by the local tourist office VVV Nordhorn arrive as two mails, and the mail import handles neither correctly. The booking mail comes through DS Destination Solutions, like Holidu's, and is imported with the agency "Holidu" because that agency is hard-coded for every DS mail. Fewo & Meer then reports the same stay as „Neue Blockung … durch den VVV Nordhorn“, which no rule recognises, so it stays „nicht erkannt“. Real data is being entered now, so wrong agencies keep piling up.

## What Changes

- Fewo & Meer also sends from `info@fewoscout.com`, its booking portal, and some of these mails carry no signature. Such mails are not recognised today. `fewoscout.com` counts as a Fewo & Meer sender.
- DS booking mails take their agency from the line „Vermittler (Stornierung/Änderung): …“. A mail whose intermediary mentions Holidu keeps the agency "Holidu", so existing Holidu bookings still match later mails. Any other intermediary becomes the agency under its own name, e.g. "VVV-Stadt- und Citymarketing Nordhorn e.V.". A DS mail without that line keeps the agency "Holidu" as today.
- DS mails are also recognised by the intermediary line together with the booking number, not only by the sender or the word "Holidu".
- The Fewo & Meer mail „Neue Blockung für <property>,<number>, vom <date> bis <date> durch <intermediary>“ is recognised as a block notice. It never creates a booking. It is attached to the booking on the mapped unit that is not cancelled and has exactly the same period. Without such a booking it stays in the inbox as „nicht zugeordnet“ and can be retried once the intermediary's booking mail has been processed.
- No database change. Bookings already imported as "Holidu" that came from VVV Nordhorn are corrected by hand in the booking dialog, where the agency is an editable field.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `booking-mails`: Fewo & Meer mails from `fewoscout.com`, agency of DS mails from the intermediary line, and recognition and effect of the Fewo & Meer block notice.

## Impact

- Backend, module `vacationrental`: `HoliduDsParser`, `FewoUndMeerParser`, `ParsedMail`, `ImportEngine`, and probably a query for bookings of a unit with an exact period in `BookingRepository`.
- Frontend: label of the new mail kind in the inbox, if kinds are labelled there.
- Tests: `MailParsersTest`, `MailImportTest`, new sample mails for the VVV booking and the block notice.
- The first VVV mail asks once to assign a unit, because units are mapped per agency.
- The active change `owner-blocks` modifies the same two requirements of `booking-mails`. Whichever change is archived second must base its delta on the updated main spec.
