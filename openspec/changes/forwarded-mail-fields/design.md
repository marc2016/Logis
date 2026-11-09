# Design

## Context

`MailContent` turns the HTML part of a mail into text; table cells are joined with a tab, so the original confirmation reads „Buchungsnummer⇥250902“. `FewoUndMeerParser.field()` matches `^Label(?:\t+| {2,})(.*)$`, and the address uses the same separator. When a mail program forwards the confirmation, cell contents keep surrounding spaces (or the plain-text part uses „Label ⇥ value“), giving „Buchungsnummer ␣⇥250902“ or „Anreise ␣⇥␣01.11.2026“. Neither alternative matches, all fields stay empty, `ParsedMail.usable()` fails, and the mail is stored as not recognised. Motivation is in proposal.md.

## Goals / Non-Goals

**Goals:**
- Read Fewo & Meer fields regardless of how spaces and tabs mix between label and value.
- Keep label matching exact, so „Telefon“ does not read the line „Telefon (mobil)“.

**Non-Goals:**
- Other forwarding damage (quoted „>“ prefixes, line-wrapped values); none was observed.
- Turning on the AI fallback.

## Decisions

### D1: One separator pattern

`SEPARATOR = "[ \\t\\u00a0]*(?:\\t|[ \\u00a0]{2})[ \\t\\u00a0]*"`: any run of spaces, non-breaking spaces and tabs that contains a tab or two spaces. Fields become `^Label` + SEPARATOR + `(.*?)\\s*$`, so leading and trailing blanks of the value are dropped. The address pattern uses the same separator. A single space stays insufficient, so prose lines that merely start with a label word („Anreise am …“) are not read as fields.

- *Alternative:* normalise whitespace in `MailContent` for all rules. Broader effect on the DS rule and stored mail text; not needed for this bug.

### D2: Test mail

`fewo-forward-apple-plain.eml` and `fewo-forward-apple-html.eml`: the same forward as plain text and as HTML (logis prefers the HTML part, so both layouts need a sample), („Anfang der weitergeleiteten Nachricht:“) of a confirmation with the separators from the sample, sent from the user's own address, guest „Johanna Beispiel“ with an invented address. Parser test checks every field; an import test checks that the mail creates the booking instead of „Nicht erkannt“.

## Risks / Trade-offs

- [More lenient matching reads a wrong line] → The label must still start the line and be followed by a tab or two spaces; existing parser tests guard the original layout.
