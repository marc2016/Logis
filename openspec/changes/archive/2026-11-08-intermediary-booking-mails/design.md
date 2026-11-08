# Design

## Context

`HoliduDsParser` accepts a mail when it contains „Buchungsmitteilung (Buchungsnummer: …)“ and the sender contains `ds-destinationsolutions` or the text contains "Holidu". It always sets the agency `"Holidu"`. Real DS mails name the intermediary in the line „Vermittler (Stornierung/Änderung): …“: „Holidu GmbH in Kooperation mit "DS Destination Solutions"“ for Holidu, „VVV-Stadt- und Citymarketing Nordhorn e.V.“ for VVV Nordhorn.

`FewoUndMeerParser` knows three subjects: „Neue Buchung für …“, „Last-Minute Buchung …“ and „Stornierung …“. `ImportEngine` matches mails to bookings by agency and booking number. Units are mapped per agency and property name (`UnitMapping`). The inbox already supports retrying a mail.

## Goals / Non-Goals

**Goals:**
- Correct agency for every DS intermediary without breaking matching of existing Holidu bookings.
- Recognise the Fewo & Meer block notice and keep it with the booking it belongs to.

**Non-Goals:**
- Correcting already imported bookings automatically.
- A list of short names for intermediaries.
- Recording payments made by the guest directly (VVV bookings are paid by the guest, not through a provider statement). That belongs to the finances.
- Teaching the AI fallback the new mail type.

## Decisions

### D0: FeWoScout as a Fewo & Meer sender

`FewoUndMeerParser.fromAgency` checks the sender address, which `MailContent` reduces to the bare address, for the endings `fewoundmeer.de` and `fewoscout.com`. Real confirmations arrive from `info@fewoscout.com` with the display name „FEWO &  MEER e.K.“ and without a signature, so neither the current domain check nor the text check matches.

- Alternative: also match the display name. Rejected, because `MailContent` drops it, and the domain is the more reliable signal anyway.
- A forwarded mail without any mention of Fewo & Meer stays unrecognised. In that case the sender is the user, and nothing in the mail points to the agency.

### D1: Agency from the intermediary line

`HoliduDsParser` reads `^Vermittler \(Stornierung/Änderung\):\s*(.+)$`, cuts off ` in Kooperation mit …`, trims and limits the result to 64 characters, the column size of `agency`.
- If the result contains "Holidu" (ignoring case) or there is no such line, the agency is `"Holidu"`. This keeps the agency of existing Holidu bookings identical, so change and cancellation mails still find them.
- Otherwise the cleaned name is the agency.

The parser accepts a mail with a booking number if the sender contains `ds-destinationsolutions`, the text contains "Holidu", or the intermediary line is present. The constant `AGENCY` and the class name stay. Renaming the class to `DsParser` would be cosmetic and is left out.

- Alternative: a fixed mapping from intermediary to short name (e.g. „VVV Nordhorn“). Rejected for now, because it needs maintenance per intermediary and the full name is stable.

### D2: Block notice as its own mail type

`ParsedMail.MailType` gets `BLOCK_NOTICE`. `FewoUndMeerParser` adds the subject rule

```
^Neue Blockung für (.+?),\s*(\S+), vom DATE bis DATE(?: durch (?:den |die |das )?(.+))?$
```

The property name comes before the number, and the comma can lack a following space, as in the sample. The intermediary is optional, and when read it goes into the detail shown in the inbox. `ParsedMail` needs no new field, because the detail is built from the subject in the engine. If the intermediary must travel, a nullable `intermediary` component is added to `ParsedMail`.

`usable()` treats `BLOCK_NOTICE` like a confirmation: it needs dates. The AI field description stays unchanged, so the AI never yields `BLOCK_NOTICE`.

### D3: Effect of a block notice

In `ImportEngine.apply`, `BLOCK_NOTICE` does not look up by agency and number, because the number belongs to Fewo & Meer while the booking carries the intermediary's number. Instead:
1. Map the unit as for a new booking (`UNASSIGNED_UNIT` if unknown or without edit rights).
2. Find the bookings of that unit that are not cancelled with `arrival` and `departure` equal to the mail's. Thanks to the overlap constraint there is at most one.
3. If found, attach the mail with the label „Blockung“ and return `ATTACHED` with the detail „Blockung durch <intermediary>“. Booking number and agency of the booking stay unchanged.
4. If not found, return `NOT_MATCHED` with the detail „Keine Buchung vom <arrival> bis <departure>“.

The change `owner-blocks` plans a similar lookup, for a manual owner block with the same period. Whichever change is implemented second reuses the repository query of the first.

### D4: Frontend

`api.ts` gets the label `BLOCK_NOTICE: 'Blockung'` for the inbox. Nothing else changes in the frontend.

## Risks / Trade-offs

- [A Holidu mail without "Holidu" in the intermediary line] → It would get a different agency, and later mails would not match. The sample confirms "Holidu GmbH …". In addition, a missing line falls back to "Holidu".
- [VVV bookings already imported as "Holidu"] → Fixed by hand. Until then, a later VVV change mail gets the VVV agency and does not find the old booking, so it lands in the inbox as „nicht zugeordnet“. Nothing is created twice.
- [A block notice arrives before the booking mail] → It stays „nicht zugeordnet“ and has to be retried by hand. Automatic retries are not worth it for this rare order.
- [Conflicting deltas with `owner-blocks`] → Both modify „Recognise booking mails“ and „Effects on bookings“. The change archived second must rewrite its MODIFIED blocks on top of the updated main spec.

## Migration Plan

No schema or data migration. Deploying the new parser only affects mails processed afterwards. Old „nicht erkannt“ block notices become recognised when they are retried in the inbox.
