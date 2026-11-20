# Design

## Context

`FewoUndMeerParser.parse` recognises Fewo & Meer mails by subject: confirmation, short notice, cancellation, block notice and owner block. Extension notices come from agency staff, often through the ticket system. Their subject varies, e.g. „[#…]: Information über eine Verlängerungsbuchung …“ or „[#…]: Verlängerungsbuchung zum Objekt …“. Their body is a fixed plain-text block:

```
wir möchten Sie über eine Verlängerungsbuchung informieren.
Objekt: <property>
Gäste: <name>
Zeitraum ursprüngliche Buchung: TT.MM.JJJJ — TT.MM.JJJJ
Verlängerung: TT.MM.JJJJ — TT.MM.JJJJ
Personen: (3) Erwachsene/ (0) Kinder        (also "( 3 ) Erwachsene/ ( 0 ) Kinder")
Haustiere: nein | 0
```

The block contains no booking number. One subject variant carried two numbers („#nnnnnn/#nnnnnn“), the other none. So the numbers cannot be relied on.

`ImportEngine`:
- matches block notices by exact period on the mapped unit (`editableUnit` and `BookingService.findSamePeriod`);
- turns differences between a mail and an existing booking into a proposal (`update` and `differences`). A booking that is still `NEW` takes the values directly („Angaben ergänzt“).

## Goals / Non-Goals

**Goals:** recognise the extension block and reuse the existing period matching and the existing change proposal.

**Non-Goals:**
- Using the subject numbers.
- Pets: „nein“ or „0“ only says there are none, which the booking already knows.
- Shortened stays and TOMAS notices.

## Decisions

### D1 — Recognised by body, not by subject

The extension rule runs after the subject rules, so a subject match always wins. It requires:
- `fromAgency(mail)`;
- the phrase „Verlängerungsbuchung“;
- the lines `Zeitraum ursprüngliche Buchung:` and `Verlängerung:`, each with two dates.

Date separators may be a hyphen, en dash or em dash with blanks around them.

It reads:
- `Objekt:` as the property name;
- `Personen:` as adults and children, accepting blanks inside the parentheses.

Values are read through `Reading`, so the inbox keeps showing where each value was found. Lines quoted with „>“ are left out, as for ticket receipts.

**Alternative:** match by subject. Rejected: the subject varies, and it is often a ticket subject.

### D2 — `ParsedMail` carries the original period

- New `MailType.EXTENSION`.
- `ParsedMail` gets a nullable component `original` (`record Period(LocalDate arrival, LocalDate departure)`), with the existing overloads defaulting it to `null`.
- For an extension:
  - `arrival` and `departure` are the merged period: earliest arrival, latest departure;
  - `original` is the period to match;
  - `bookingRef` is `null`.
- `usable()` accepts `EXTENSION` without a booking number when `original` and the merged dates are valid.

**Alternative:** a separate parse result type. Rejected: the engine, the sources and the inbox already work with `ParsedMail`.

### D3 — Matching in `ImportEngine`

`apply` handles `EXTENSION` before the lookup by number, like `BLOCK_NOTICE`:

1. `editableUnit`. Unknown property → „Einheit zuordnen“.
2. `findSamePeriod(unit, original)`, restricted to guest bookings (no owner use). Found → `update(owner, agency, booking, in)`. Its `differences` then proposes the merged dates and different adults or children, or applies them directly to a `NEW` booking.
3. Otherwise `findSamePeriod(unit, merged)` for a guest booking. Found → attach with „Verlängerung bereits erfasst“.
4. Otherwise `NOT_MATCHED` with „Keine Buchung vom TT.MM.JJJJ bis TT.MM.JJJJ“ for the original period.

An overlap with another booking is checked when the owner confirms the proposal, as for every change.

### D4 — Rules only, labels

- `MailRecognizer.toParsed` refuses `EXTENSION` from the AI, as it does for block notices.
- `ImportEngine.label(EXTENSION)` is „Verlängerung“; it is used for document names and source labels.
- The kind label „Verlängerung“ goes into `BookingMailHandler.KIND_LABELS` and the frontend's `mailKindLabel`. `MailLabelsTest` keeps them equal.
- `BookingSourceBackfill` stays as it is: it only derives sources for mails that carry a booking number.

## Risks / Trade-offs

- **Two bookings with the same original period on one unit.** This is impossible for guest bookings, because overlaps are rejected; cancelled bookings are excluded by `findSamePeriod`.
- **The agency changes the wording of the block.** → The mail falls back to „Nicht erkannt“, which is the same as today.
- **The original booking was already moved to other dates.** → „nicht zugeordnet“ with the original period in the detail, so the owner can find and adjust it by hand.
