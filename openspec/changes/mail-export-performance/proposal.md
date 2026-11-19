# Proposal

## Why

An admin export of about 600 mails takes very long. For every text of every mail (subject, address names, text and HTML parts), pseudonymization compiles and runs one regular expression per known value. Those values are all guest values of all bookings the owner can see, so there are often thousands of them. The cost is mails × texts × known values × text length, and each new booking makes every later export slower.

## What Changes

- Known values are replaced in one pass over each text instead of one pass per value. The lookup structure is built once per mail from that mail's values, not once per value and text.
- The replacement rules stay the same:
  - the longest value wins;
  - the word boundaries are unchanged;
  - name parts and HTML-escaped forms are replaced;
  - the same value gets the same replacement within one export;
  - the patterns for IBAN, e-mail and phone run afterwards.
- One difference in edge cases: a replacement that was already inserted is not searched again. When two known values overlap, the one that starts first wins, and if both start at the same place, the longer one wins.
- A test with invented data checks the export time: 600 mails and several thousand known values.

## Capabilities

### New Capabilities

### Modified Capabilities
- `mail-export`: adds two requirements.
  - Export duration: pseudonymization time must not multiply with the number of known values.
  - Overlapping values: how overlapping known values are replaced in one pass.

## Impact

- **Backend:**
  - `core/mail/Pseudonymizer` gets a new replacement algorithm for known values.
  - `core/mail/MimeRewriter` builds the lookup once per mail.
  - New tests in `PseudonymizerTest` and a new duration test.
- **Unchanged:**
  - API, frontend, database: no change.
  - `MailHandler.SensitiveValues` and `BookingMailHandler`: unchanged.
- **Data:** none touched; no migration.
