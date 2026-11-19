# Tasks

## 1. Name parts and new kinds

- [x] 1.1 Add `SensitiveValue.Kind` `OWNER` („Inhaber N“) and `LEGAL_ENTITY` („Rechtsträger N“), and apply the name-part rule of design D2 to `NAME`, `OWNER` and `LEGAL_ENTITY` in `Pseudonymizer`. Verify with new `PseudonymizerTest` cases (invented values):
  - „Lisa van der Berg“ keeps „der“ in other text;
  - „Anna Beispiel GbR“ replaces „Beispiel“ but not a lone „GbR“;
  - replacements of owners and legal entities.

## 2. Owner, legal entity and property values

- [x] 2.1 Collect the owner values of design D1 in `MailExportService` (display name, username, visible legal entities, street, postal code and city of visible properties), using the realestate module's visibility. Verify with a `MailExportTest` case: an export of a mail addressed to the owner's GbR with the property address contains none of these values, and a property shared with the owner counts while an unshared one does not.

## 3. Agency domains

- [x] 3.1 Add `MailParser.agencyDomains()` and `MailHandler.agencyDomains()` (design D4), implement them for the Fewo & Meer and Holidu parsers and `BookingMailHandler`, and add the domains to `keepDomains` in the export. Verify with a `MailExportTest` case: a mail from the agency's second domain keeps its sender address, and a mail from a public provider does not.

## 4. Labels in the index

- [x] 4.1 Add `MailHandler.resultLabels()` and `kindLabels()` (design D3), implement them in `BookingMailHandler` with the labels of `mailResultLabel` and `mailKindLabel`, add a comment on both sides, and use them for `index.csv`. Verify with a `MailExportTest` case: a recognised confirmation's row shows „Buchung angelegt“ and „Bestätigung“; a test pins the vacation rental labels.

## 5. Verification

- [x] 5.1 Run `cd backend && ./mvnw test` with the e2e stack stopped. It must be green.
- [x] 5.2 Run `e2e/tests/mail-export.spec.ts` against a stack with the new backend image. It must be green; the UI is unchanged, so `e2e/COVERAGE.md` stays as it is.
- [x] 5.3 Check that no value from the real export the user uploaded appears in the diff: grep the diff for its owner, property and agency staff names.

## Workflow follow-up

- Archive with `/opsx:archive` and sync the `mail-export` spec.
