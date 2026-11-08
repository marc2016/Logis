# Tasks

## 1. Sample mails

- [x] 1.1 Add anonymised sample mails `ds-vvv-confirmation.eml` (DS booking 6a29b55l9j by VVV Nordhorn), `fewo-block-notice.eml` (block notice 275206, from `info@fewoscout.com`) and `fewoscout-confirmation.eml` (confirmation 274907 from `info@fewoscout.com` without signature) to `backend/src/test/resources/mails/`, and verify they load in `MailParsersTest`

## 1a. FeWoScout sender

- [x] 1a.1 Accept `fewoscout.com` as a sender domain in `FewoUndMeerParser.fromAgency` (design D0); verify in `MailParsersTest` that `fewoscout-confirmation.eml` is read as a Fewo & Meer confirmation with 2 adults and 454.32, and in `MailImportTest` that it creates a booking „zu prüfen“

## 2. DS agency

- [x] 2.1 Read the agency from the line „Vermittler (Stornierung/Änderung)“ in `HoliduDsParser` and accept mails by that line (design D1); verify in `MailParsersTest` that the Holidu sample still yields "Holidu" and the VVV sample yields "VVV-Stadt- und Citymarketing Nordhorn e.V." with 2 adults, 2 children and 447.00
- [x] 2.2 Verify in `MailImportTest` that the VVV mail asks once for the unit of "Ferienhaus Seeufer" and then creates a booking „zu prüfen“ with the VVV agency, while the existing Holidu import test stays green

## 3. Block notice

- [x] 3.1 Add `BLOCK_NOTICE` to `ParsedMail.MailType` and the subject rule to `FewoUndMeerParser` (design D2); verify in `MailParsersTest` with the sample, a variant with a space after the comma, and a forwarded variant
- [x] 3.2 Handle `BLOCK_NOTICE` in `ImportEngine` by exact period on the mapped unit (design D3), with the document label „Blockung“; verify in `MailImportTest` the scenarios for a known stay, the notice arriving first followed by a retry, and a different period

## 4. Frontend and checks

- [x] 4.1 Add the inbox label `BLOCK_NOTICE: 'Blockung'` in `frontend/src/modules/vacationrental/api.ts`, and verify with `npm run lint` and `npm run build`
- [x] 4.2 Run `cd backend && ./mvnw test` and `cd frontend && npm test`, and verify both pass
