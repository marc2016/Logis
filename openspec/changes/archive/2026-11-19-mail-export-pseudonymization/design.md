# Design

## Context

`MailExportService.export` collects the values to replace per mailbox owner through `MailHandler.sensitiveValues(owner)`. Only `BookingMailHandler` contributes, with guest values and booking numbers. The core itself adds nothing about the owner.

`Pseudonymizer.pairs` adds every part of a NAME value with at least 3 code points as its own pair. Matching is case-sensitive, so a lowercase particle like „der“ matches the ordinary word.

`keepDomains` contains only the domains of senders whose mails a handler took. Fewo & Meer's booking mails come from its booking portal's domain; its staff and ticket system write from the agency's own domain. `FewoUndMeerParser.fromAgency` already knows both domains.

The labels of results and kinds exist only in the frontend (`mailResultLabel`, `mailKindLabel` in `modules/vacationrental/api.ts`). `MailExportService` only labels the three core codes.

See proposal.md (Why) for the findings of the real export.

## Goals / Non-Goals

**Goals:** close the four gaps named in the proposal, keeping the rules of the existing requirements.

**Non-Goals:**
- Names of agency staff in signatures. They are not known values, and there is no reliable pattern; this stays best effort.
- Property and unit names such as „Haus …“. The user chose to keep them.
- Reading PDF attachments.

## Decisions

### D1 — Owner values are collected in the core

`MailExportService` adds per owner, once per export:
- display name and username, as kind `OWNER`;
- names of the legal entities the owner can see, as kind `LEGAL_ENTITY`. These are the entities the owner manages, plus the legal entities of the properties the owner can see;
- `street`, `postal_code` and `city` of the properties the owner can see, as the existing kinds `STREET`, `POSTAL_CODE` and `CITY`.

Visibility uses the existing per-object sharing model through the realestate module's listing for the owner (the same properties the owner sees on „Objekte“). It does not use a separate query.

New replacements:
- `OWNER` → „Inhaber N“;
- `LEGAL_ENTITY` → „Rechtsträger N“.

They differ from „Gast N“ so that the export still shows who is who.

**Alternative:** let a handler contribute these values. Rejected: the owner and the properties are core data and apply to every handler.

### D2 — Rule for name parts

Parts of `NAME`, `OWNER` and `LEGAL_ENTITY` values become pairs of their own only when all of these hold:
- the part has at least 3 letters;
- its first code point is an uppercase letter;
- it is not in a fixed list of legal forms: GbR, GmbH, gGmbH, UG, KG, OHG, AG, e.K., e.V., mbH, Co, Co., KGaA, PartG.

The full value is always a pair. „Lisa van der Berg“ keeps „Lisa“ and „Berg“ as parts and drops „van“ and „der“. „Anna Beispiel GbR“ keeps „Anna“ and „Beispiel“.

**Alternative:** a stop-word list of common German words. Rejected: it never ends, and the uppercase rule already covers the particles.

### D3 — Labels from the handlers

`MailHandler` gets `default Map<String, String> resultLabels()` and `default Map<String, String> kindLabels()`, which return empty maps by default. `BookingMailHandler` returns the same German labels as `mailResultLabel` and `mailKindLabel` in the frontend.

`MailExportService` merges the core labels with all handlers' labels. Codes without a label are written as they are.

**Duplication:** the labels then exist in the backend and in the frontend. A backend test pins the vacation rental labels, and a comment on both maps points to the other side.

**Alternative:** serve labels from an API and drop the frontend maps. Rejected: it is a larger change for one CSV column.

### D4 — Agency domains from the parsers

`MailParser` gets `default Set<String> agencyDomains()`:
- `FewoUndMeerParser` returns its two domains;
- `HoliduDsParser` returns the domain it checks.

`BookingMailHandler` exposes the union through a new `MailHandler.agencyDomains()`, which returns an empty set by default. `MailExportService` adds these domains to the domains derived from handled senders. Public mail providers are still removed by `MimeRewriter.agencyDomains`.

## Risks / Trade-offs

- **A guest whose name part is lowercase but not a particle** (e.g. a lowercase-only name written in a form). → The full name is still replaced. Only a bare lowercase part elsewhere stays; this is accepted best effort.
- **Owner display name is also a common word** (e.g. a username like „admin“). → Usernames are replaced as a whole word only, like every value. Accepted.
- **Labels drift between backend and frontend.** → The backend test pins them, and comments on both maps point to the other side.
