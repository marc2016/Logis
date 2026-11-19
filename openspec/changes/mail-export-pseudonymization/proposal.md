# Proposal

## Why

A real export showed gaps in the pseudonymization. Some values stayed readable:
- the mailbox owner's own name and the names of their GbR and other legal entities;
- the street and postal code of their properties.

Other values were replaced although they should not have been:
- A short lowercase name part of one guest (a particle like „der“) replaced the same word everywhere, e.g. „bis Ende Gast61 Herbstferien“.
- The sender address of an agency was replaced when the agency writes from a domain other than the one its booking mails come from.

`index.csv` also shows the vacation rental results and kinds as codes, e.g. `BOOKING_CREATED`, instead of the labels the inbox shows.

## What Changes

- The export also replaces:
  - the owner's display name and username;
  - the names of the legal entities the owner can see;
  - street, postal code and city of the properties the owner can see.
- A name part is only replaced on its own when it starts with an uppercase letter and is not a legal form (GbR, GmbH, KG, e.K., …). Full names are still replaced as a whole.
- Sender addresses on the domains that the recognition rules know as agency domains stay unchanged, in addition to the domains of senders whose mails a handler took.
- `index.csv` shows the German labels of results and kinds, as the inbox does.

## Capabilities

### New Capabilities

### Modified Capabilities
- `mail-export`:
  - „Replaced values“: owner, legal entity and property values; the rule for name parts.
  - „Export index“: labels instead of codes.
  - New requirement „Agency addresses“.

## Impact

- **Backend:**
  - `core/mail`: `MailExportService`, `Pseudonymizer`, `SensitiveValue.Kind`, and `MailHandler`, which gets labels and agency domains.
  - `core/realestate`: read access to the properties and legal entities visible to the owner.
  - `modules/vacationrental`: `BookingMailHandler` and the parsers supply labels and agency domains.
- **Unchanged:** API, frontend and database, so no migration.
