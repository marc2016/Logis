# Design

## Context

See proposal.md for motivation. What exists today and shapes the approach:

- `core.mail.MailSender` is the only way out of Logis to a user. It has two callers: `SignInMonitor` (warning mail, sent after commit on `applicationTaskExecutor`) and `EmailChangeService` (codes and notices, which stay direct mails and are not notifications).
- The admin's SMTP settings (`core.smtp_settings`, one row, secret encrypted with `shared.SecretCipher`, connection tested before saving) are the pattern for a system-wide secret.
- Extension points are Spring beans implementing a core interface: `ShareableType`, `MailHandler`, `CategoryContribution`. Module beans carry `@ConditionalOnLogisModule`, so a disabled module contributes nothing.
- `SharingService` answers "which objects may this user see" and, for an actor, "who has a share on this object". Nothing answers "which users have an effective role on this unit" for the system itself.
- `MailService.handle` is used for freshly fetched mails, for older mails (`FetchRun.Kind.OLDER`) and for processing a stored mail again. `MailHandler.handle` cannot tell these apart.
- `core.user_preferences` returns every value to the browser and is meant for interface choices.
- `logis.auth.public-url` (`LOGIS_PUBLIC_URL`) is the only place the public address is known; it is optional.
- This container could not reach `pushover.net`, so the API details in D7 come from memory of the Pushover documentation and are confirmed in task 4.1 before the client is written.

## Goals / Non-Goals

**Goals:**
- One place that decides who is told what through which channel; callers only say what happened and who may be concerned.
- A new channel is one class plus its set-up; a new event type is one bean plus the calls that raise it.
- No behaviour change for users who touch nothing.

**Non-Goals:**
- No notification history, no in-app notification centre, no retry queue. A failed send is logged and lost.
- No Pushover priorities, sounds, device selection, quiet hours or emergency acknowledgements.
- No choice of lead time or time of day for the digest (fixed at 18:00 for the next day).
- Codes and notices about the user's own e-mail address stay plain mails outside the matrix.
- No notifications for long-term rental in this change; the module can register types later.

## Decisions

### D1 — Package `core.notifications` with a small public API

```
 modules / core parts                      core.notifications
 --------------------                      ------------------
 NotificationType bean  ---- registers --> registry (key -> type)
 Notifications.send(type, userIds, n) ---> after commit, in background:
                                             for each active user
                                               for each channel
                                                 enabled? usable? -> channel.send
```

Public (used by modules): `NotificationType` (key, label, group, mandatory channels), `Notification` (title, shortText, longText, path), `Notifications` (the one service callers use). Internal: `NotificationChannel` (id, label, `Form` it wants, `usableFor(userId)` with a German reason, `send`), the settings store and controller.

Callers pass the candidate recipients as user IDs. The core does not know domain objects, so it cannot work out who may see a booking; the module that raises the event does, with its own access rules. The core filters to active users and applies the switches.

*Alternative:* publish Spring application events and let a listener in `core.notifications` map them. Rejected: every event type would need a mapping in the core, which pulls domain knowledge into it.

### D2 — Two forms, chosen by the channel

`Notification` carries both texts. A channel declares `Form.SHORT` or `Form.LONG`. Pushover takes short, e-mail takes long. The rule "no guest data in the short text" is a duty of the code that builds the notification and is checked by its tests; the core cannot verify it.

*Alternative:* one render method per channel on every event type. Rejected: every new channel would touch every module.

### D3 — Own tables, not `user_preferences`

```sql
core.notification_settings (user_id, event_type, channel, enabled, updated_at,
                            PRIMARY KEY (user_id, event_type, channel))
core.pushover_settings     (id = 1, app_token_enc, updated_at, version)
core.pushover_user_keys    (user_id PRIMARY KEY, user_key_enc, invalid_since, updated_at)
```

A missing row in `notification_settings` means the default (off, or on if mandatory). The backend reads the switches on every event, and the Pushover key is a secret, so neither belongs in a store that is returned to the browser as a whole and capped at 100 keys. All three are additions with `ON DELETE CASCADE` to `core.users`; no existing row changes. Rows for unknown event types or channels are kept and ignored, which covers disabled modules.

### D4 — Send after commit, on the existing executor

`Notifications.send` registers an after-commit synchronisation (or runs directly without a transaction) and hands the work to `applicationTaskExecutor`, generalising what `SignInMonitor.afterCommit` does today. Switches, usability and the user's active state are read at send time in the background thread. Each channel call is wrapped: an exception is logged with type, channel and user ID and never propagates.

### D5 — The sign-in warning moves onto the layer without changing its mail

`SignInMonitor` registers the type `core.signIn` with `mail` as a mandatory channel and calls `Notifications.send` instead of `MailSender`. The long text is the current mail text unchanged and the title is the current subject, so the existing `SignInAlertsTest` assertions on the mail keep holding. The condition `user.getEmail() != null` moves from the monitor into the mail channel's usability check, so a user without an address can still get the Pushover warning.

### D6 — Mail-driven events are raised only for fresh, regular polls

`MailService` knows the fetch kind. After storing and handling a mail in a `POLL` run whose mail was received within the last two days, it publishes an application event `MailImported(ownerUserId, mailId, handlerKey, result, linkType, linkId)` (public type in `core.mail`). Older fetches and reprocessing publish nothing.

- Vacation rental listens: for `BOOKING_CREATED` and `CHANGE_PROPOSED` with a booking ID it loads the booking, checks the review state, resolves the users with at least `EDITOR` on the unit and sends `vacationrental.bookingReview` or `vacationrental.changeReview`.
- The core counts the mails of a run whose result needs attention and sends one `core.mailAttention` at the end of the run.
- `core.mailFetchFailed` is sent at the end of a `POLL` run that failed while `Mailbox.lastError` was empty before; no new column is needed.

The set of results that "need attention" lives in the frontend today (`ATTENTION_RESULTS` in `inboxFilter.ts`). It moves to the backend: `MailHandler` gains `attentionResults()` (default empty), the core adds its own `NOT_RECOGNISED` and `FAILED`, and the inbox API exposes the set so the quick filter „Offen“ and the notification cannot drift apart.

*Alternative:* let `BookingMailHandler` send directly from `handle`. Rejected: it would notify for every reprocessed and every older mail, e.g. 40 notifications for one backfill.

### D7 — Pushover client

A `PushoverClient` interface with one HTTP implementation (Spring `RestClient`, 5 s connect and 10 s read timeout) against `logis.pushover.base-url`, default `https://api.pushover.net`:

- `POST /1/messages.json` with `token`, `user`, `title`, `message`, optionally `url` and `url_title` („In Logis öffnen“). Title is cut to 250 and message to 1024 characters with „…“.
- `POST /1/users/validate.json` with `token` and `user` to check a user's key on save.
- Checking the application token alone (admin page): call `validate.json` with the token and a syntactically valid dummy user key and read which field the error names; a response that names `token` as invalid means a bad token, one that only names `user` means the token was accepted.
- A 4xx answer that names the user key as invalid sets `invalid_since`; the channel is then not usable for that user until a new key is saved. 429 and 5xx are logged and dropped.

No Pushover SDK is added; two form posts do not justify a dependency. The interface lets backend tests use a fake, and the configurable base address lets the e2e stack point at a stand-in.

### D8 — Recipient lookup for units

`SharingService` gains `usersWithRole(type, objectId, minimum)`, which includes roles inherited from the parent type, and unit access exposes it for units. It is a system-side query without an actor and is only used to address notifications, never to answer a request. Booking review uses minimum `EDITOR` (only they can confirm), the digests use `VIEWER`.

### D9 — Digest job with a run table

```sql
vacationrental.notification_digests (day DATE PRIMARY KEY, sent_at TIMESTAMPTZ NOT NULL)
```

A `@Scheduled(cron = "0 0 18 * * *", zone = "Europe/Berlin")` job and an `ApplicationReadyEvent` listener call the same method: if the local time is 18:00 or later and inserting today's row succeeds (`ON CONFLICT DO NOTHING` returns one row), build the digests. The insert makes the run safe against restarts and against two instances. The job loads tomorrow's arrivals and departures once, groups them per unit, resolves the users per unit (D8) and sends at most two notifications per user. The path of the link is the booking list filtered to the day.

### D10 — API and pages

- `GET /api/notifications` → channels (id, label, usable, hint) and types (key, label, group, per channel: enabled, mandatory). `PUT /api/notifications/{type}/{channel}` with `{ "enabled": true }`.
- `GET|PUT|DELETE /api/account/pushover`, `POST /api/account/pushover/test`.
- `GET|PUT|DELETE /api/admin/pushover` under the existing admin guard, including the fresh sign-in the admin area requires.
- Frontend: `NotificationsCard` and `PushoverCard` on `/konto`; `PushoverPage` under `core/admin/pushover` with the sidebar entry „Pushover“ next to „E-Mail-Versand“. Labels of types and groups come from the backend, so a module needs no frontend change to add a type.

## Risks / Trade-offs

- [Pushover's answers for an invalid token or key differ from D7] → Task 4.1 confirms the request and response shapes against the Pushover documentation before the client is written; if a token cannot be checked alone, the admin page asks for a user key to send a test with, and the spec requirement „Check the token on save“ is adjusted before implementation continues.
- [A noisy mailbox raises a notification every five minutes] → One notification per poll with a count, only for mails of the last two days, and off by default.
- [Guest data leaks into a short text by mistake] → Each event type has a test that builds a notification from a booking with marker values and asserts that none of them appear in title or short text.
- [A lost notification goes unnoticed] → Accepted for now; everything a notification points to stays visible in the app. A history is a possible later change.
- [Texts go to a service outside the EU] → Stated on the admin page; nothing is sent until an admin stores a token and a user stores a key and switches a type on.
- [Sign-in warning regresses while moving] → The existing backend and e2e tests for the warning mail stay unchanged and must pass.
- [Pushover's monthly message limit] → Not tracked; a rejected message is logged like any other failure.

## Migration Plan

1. New migrations create the four tables; no existing table or row changes, so a rollback to the previous image only leaves unused tables behind.
2. After deployment nothing is sent through Pushover until an admin stores a token. The sign-in mail keeps working through the mandatory default without any stored row.
3. Operators whose firewall restricts outbound traffic need to allow `api.pushover.net:443`; the deployment notes in the README say so.

## Open Questions

- Should „Neue Anmeldung“ use a higher Pushover priority than the other types? It can be added later as a property of the event type without changing stored data.
