# Tasks

## 1. Foundations

- [x] 1.1 Add `spring-boot-starter-mail`, `jsoup`, the Anthropic Java SDK and GreenMail (test) to `pom.xml`, and enable scheduling in `VacationRentalConfiguration` (D4). Verify: `./mvnw verify` stays green and `ModularityTest` passes.
- [x] 1.2 Create `V20261108.1200__vacationrental_mail_import.sql` according to D2, including the replaced booking number index. Verify: the backend starts with the `dev` profile on an existing dev database with bookings, and existing bookings have `review_state = 'NONE'`.
- [x] 1.3 Add `Documents.store` for server-created files with a type whitelist including `message/rfc822` (D5). Verify: a test stores and downloads an `.eml`, and uploading an `.eml` through `/api/documents` is still rejected.

## 2. Bookings

- [x] 2.1 Add agency and pets to bookings, the uniqueness per unit and agency, the list filter `review` and the sort key `agency`. Verify: the scenarios of the modified requirement „Unique booking number per unit“ and „Agency and pets“ pass, and the existing booking tests stay green.
- [x] 2.2 Implement review state and proposals with confirm, discard, apply and discard proposal (D7 endpoints). Verify: integration tests cover every scenario of „Bookings awaiting review“ and „Proposed changes“, including the overlap message on apply.

## 3. Parsing

- [x] 3.1 Add the anonymised sample mails as test resources and implement `ParsedMail`, `FewoUndMeerParser` and `HoliduDsParser` (D1). Verify: unit tests read all samples correctly, including "2 Erwachsene, 1 Kind", the multi-line address, German amounts and dates, and the Holidu total price 309.08.
- [x] 3.2 Implement `AiMailParser` with the configuration of D6 behind an interface. Verify: with a stub client, a fixed JSON becomes a `ParsedMail`; incomplete JSON gives `NOT_RECOGNISED`; with AI disabled the parser is not called.

## 4. Import

- [ ] 4.1 Implement `Mailbox`, `MailboxService` and `MailboxController` with encrypted password and connection test. Verify: tests with GreenMail cover saving, the password never in responses, and „Das Postfach ist nicht erreichbar oder die Anmeldung ist fehlgeschlagen“.
- [ ] 4.2 Implement `MailPoller` and the fetch of older mails (D4). Verify: GreenMail tests cover the cut-off date, processing each Message-ID once, unchanged flags in the mailbox, and an error stored in `last_error`.
- [ ] 4.3 Implement `ImportEngine` and `UnitMapping` (D3), attaching the `.eml` to bookings. Verify: integration tests cover every row of D3 and every scenario of „Effects on bookings“ and „Unit mapping per agency“.
- [ ] 4.4 Implement `InboxService` and `InboxController` (list, assign unit, retry, ignore, delete, text preview). Verify: tests cover the inbox scenarios and that another user gets an empty inbox and 404 on someone else's mail.

## 5. Frontend

- [ ] 5.1 Show agency, pets and „zu prüfen“ in the grid (filter and column), calendar (dashed bars) and `BookingDialog` with confirm/discard and the proposal diff (D7). Verify: component tests cover confirm, discard, applying a cancellation proposal and the review filter.
- [ ] 5.2 Add the page „Posteingang“ with the settings card and the inbox table and actions. Verify: component tests cover saving the mailbox without showing the password, assigning a unit and retrying, and the AI notice when AI is active.

## 6. Acceptance

- [ ] 6.1 Run `./mvnw verify` and `npm test && npm run lint && npm run build`. Verify: everything is green.
- [ ] 6.2 Manual run against a GreenMail or real test mailbox: deliver the five sample mails (anonymised), assign "Haus Seeufer - Nordhorn" and "Ferienhaus Seeufer" to the same unit, confirm a booking, apply a cancellation, and check the inbox results. Verify: all steps behave as described in the specs.
- [ ] 6.3 Document the mailbox setup, the AI settings and the privacy notice in the README. Verify: the section names `LOGIS_AI_ENABLED`, `LOGIS_ANTHROPIC_API_KEY` and `LOGIS_AI_MODEL` and that AI is off by default.
