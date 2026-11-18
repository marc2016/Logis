# Proposal

## Why

Fetching older mails is hard to use: the native date field makes jumping back several years tedious, the „Ältere Mails abrufen“ button stays disabled while a typed date is incomplete, and after starting a fetch the user cannot tell whether mails are actually being downloaded. Once hundreds of older mails are in the inbox, the user also cannot find the ones that still need attention, because the inbox table can neither be filtered nor sorted.

## What Changes

- Choosing the start date for older mails: quick ranges („1 Jahr“, „2 Jahre“, „5 Jahre“) plus a date field that accepts typed dates (`TT.MM.JJJJ`) and offers a calendar with month and year selection. The button is enabled as soon as a valid date is chosen; an invalid or future date shows a message instead of silently disabling the button.
- A visible fetch progress: while a fetch runs (manual, older mails or the regular poll), the mailbox card shows a progress bar with checked and total mails and how many were new, and the result when it ends.
- Only one fetch per mailbox at a time. Starting another fetch while one runs is rejected with a message; the regular poll skips a mailbox that is being fetched. Mails stay unique by Message-ID as before, and the UI states that already fetched mails are skipped.
- Mails are processed while they are downloaded, so they appear in the inbox during a long fetch instead of only at its end.
- The inbox table can be sorted by every column and filtered by result (with a quick filter „Offen“ for mails that need attention), kind, sender, received period and a search in the subject. Filters survive a page reload.

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `mail-inbox`: fetching older mails gets a date choice with quick ranges, a progress display and a single-fetch rule; the inbox gets sorting and filtering.

## Impact

- Backend `core.mail`: `MailService`, `MailPoller`, `MailController`, `ImapClient`/`JakartaImapClient` (per-mail callback instead of a collected list), a new fetch status endpoint. No schema change, no migration.
- Frontend `core/mail`: `MailboxCard` (date choice, progress), `PosteingangPage` (filters, sorting).
- New frontend dependency for the shadcn calendar (`react-day-picker`).
- Tests: `MailInboxTest`, `Mail.test.tsx`, `DataTable.test.tsx`, e2e `mail-import.spec.ts`/new inbox filter journey, `e2e/COVERAGE.md`.
