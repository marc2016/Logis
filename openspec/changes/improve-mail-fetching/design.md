# Design

## Context

- `MailController` starts `poll` and `fetch-older` on the application task executor and returns `202` without any status. The frontend shows a toast and reloads once after three seconds.
- `JakartaImapClient.fetchSince` searches the folder, downloads every unknown mail into a list and returns it; `MailService.fetch` processes the list afterwards. Long fetches therefore show nothing until the end and hold all raw mails in memory.
- Uniqueness is guaranteed by `UNIQUE (owner_user_id, message_id)` and the `known` predicate. A parallel poll and older fetch can both try to store the same mail; the constraint stops the duplicate, but the mail fails with an exception.
- `DataTable` sorts only on the server (`manualSorting`); the inbox passes no sort and has no filters. The inbox endpoint returns all mails of the user, which is fine for the expected volume (hundreds).
- No calendar component exists yet; date fields use `<input type="date">`.

## Goals / Non-Goals

**Goals:**
- One observable fetch run per mailbox with live progress.
- Streamed processing: each mail is stored and handled right after it is downloaded.
- Client-side filtering and sorting of the inbox.

**Non-Goals:**
- Server-side paging or filtering of the inbox (not needed for hundreds of mails).
- Persisting fetch runs or a fetch history; only the current or last run is shown.
- Cancelling a running fetch.
- Read/unread state of mails.

## Decisions

### D1: In-memory fetch run per mailbox
`MailService` keeps a `ConcurrentHashMap<Long, FetchRun>` keyed by owner. A run holds kind (`POLL`, `OLDER`), from-date, total, checked, created, started/finished time, state (`RUNNING`, `DONE`, `FAILED`) and error. Starting a run uses `compute` to atomically refuse a second `RUNNING` run (`409` „Es läuft bereits ein Abruf.“ for manual starts; the scheduler silently skips). The finished run stays in the map so the UI can show the result.

*Alternative:* a DB table for runs. Rejected: no need for history, adds a migration, and a run is meaningless after a restart. Logis runs as a single backend instance, so in-memory state is sufficient.

### D2: Status endpoint and polling from the frontend
`GET /api/mail/mailbox/fetch-status` returns the run of the current user (or `null`). The mailbox card queries it every 1.5 s while the state is `RUNNING` (React Query `refetchInterval`), otherwise not at all; starting a fetch invalidates it. When a run turns from running to finished, the card invalidates the inbox. While running, the inbox is also refreshed every few seconds so new mails appear.

*Alternative:* SSE/WebSocket. Rejected: more infrastructure for a single progress bar.

### D3: Streaming IMAP client
`ImapClient.fetchSince(config, since, known, listener)` with a listener receiving `onTotal(int)` after the search and `onMail(FetchedMail)` / `onSkipped()` per message. `MailService` processes each mail inside `onMail` and updates the run counters. Processing errors of a single mail stay per mail as today. `process` additionally catches a `DataIntegrityViolationException` on save and treats it as "already known", as a safety net.

### D4: Date choice in the frontend
Add the shadcn `calendar` component (`react-day-picker`) with `captionLayout="dropdown"` for month/year selection, inside a popover next to a text input. The text input parses `TT.MM.JJJJ` (strict, four-digit year) on blur/enter; quick-range buttons set the date to today minus 1/2/5 years. Validation messages per spec. The selected date is held as ISO string, so the request body stays `{ from }`. The backend additionally rejects a future date with `400`.

*Alternative:* only fix the native input. Rejected: it cannot jump years and hides incomplete input.

### D5: Client-side sorting and filtering
`DataTable` gets an opt-in `clientSort` mode using TanStack's `getSortedRowModel` with the sort state still passed in/out, so the header UI stays the same. Filtering happens in `PosteingangPage` before the data reaches the table (plain array filter), which keeps `DataTable` simple. Filter and sort state live in URL search params (`ergebnis`, `art`, `absender`, `von`, `bis`, `q`, `sort`), like `BuchungenPage` uses `q`. „Offen“ maps to `ATTENTION_RESULTS`. Result, kind and sender options are derived from the loaded mails with their German labels. Sorting of „Art“ and „Ergebnis“ uses the displayed label; „Absender“ uses the displayed value (source or sender). Selection is pruned to visible rows before bulk actions.

## Risks / Trade-offs

- [In-memory run is lost on restart] → The UI simply shows no run; the next poll continues normally. Acceptable.
- [Several backend instances would not share run state] → Logis runs as one instance; documented here. Revisit if that changes.
- [IMAP connection held open during handler work (AI fallback can be slow)] → The existing 20 s socket timeout applies per command, not to idle time between commands; if servers drop idle connections, the run fails with a message and can be restarted; already processed mails are skipped.
- [Large client-side lists] → Fine for hundreds of mails; if the inbox grows to many thousands, switch to server-side paging later.

## Migration Plan

No schema or data change. Deploy backend and frontend together; the old `fetch-older` request body stays compatible.
