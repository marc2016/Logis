# Proposal

## Why

Fewo & Meer confirmations that the user forwards (for example with Apple Mail) arrive as „Nicht erkannt“. In the forwarded text, a field label is followed by a space and then a tab („Buchungsnummer ⇥274907“), and the value may start with a space. The Fewo & Meer rule only accepts a tab or at least two spaces directly after the label, so it reads no field and drops the mail. The AI fallback is off, so nothing else catches it. Test mails so far only used the original layout.

## What Changes

- The Fewo & Meer rule accepts any mix of spaces and tabs between a field label and its value, as long as it contains a tab or at least two spaces. This applies to all table fields, including the multi-line address.
- A new test mail in the forwarded layout (invented guest) covers confirmation fields, guest data and address.
- The other rules are checked for the same weakness; the DS rule reads „Label: value“ with flexible spacing and stays unchanged unless the check finds a gap.
- Mails already stored as „Nicht erkannt“ are recognised after „Erneut verarbeiten“ in the inbox; no migration.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `booking-mails`: Forwarded mails are recognised even when the forwarding changes the spacing between field labels and values.

## Impact

- **Backend:** `FewoUndMeerParser` (field and address patterns), `MailParsersTest`, `MailImportTest`, a new test mail under `src/test/resources/mails/`.
- **Frontend, API, data:** unchanged.
- **Data protection:** the sample the user sent contains a real guest; only its structure is used, the test mail gets invented guest data.
