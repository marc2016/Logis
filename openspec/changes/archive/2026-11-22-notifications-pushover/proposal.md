# Proposal

## Why

Logis only tells users about things when they open the app: a booking from a mail waits for review, a mailbox stopped fetching, guests arrive tomorrow. The one exception is the sign-in warning by e-mail. Users want to hear about these events on their phone, through Pushover, and decide per event how they are told.

## What Changes

- A notification layer in the core. Parts of Logis register **event types**; the core decides per recipient and **channel** whether to send, and sends in the background.
- Two channels: **E-Mail** (the existing outgoing mail server) and **Pushover** (new). Further channels can be added later without touching the event types.
- Each event type provides a **short form** (used by Pushover; no guest names, no amounts) and a **long form** (used by e-mail; with those details), plus a link into the app.
- A card „Benachrichtigungen“ under „Mein Konto“ with a matrix of event type × channel. Every switch is off by default.
- Pushover set-up on two levels: an admin stores the application token under *Einstellungen* → „Pushover“; each user stores their own user key under „Mein Konto“ and can send a test notification.
- Event types in this change:
  - „Neue Buchung zu prüfen“ and „Änderung oder Stornierung zu prüfen“ (vacation rental, from the mail import)
  - „Anreisen morgen“ and „Abreisen morgen“ (vacation rental, one digest per user, daily at 18:00)
  - „Postfach-Abruf fehlgeschlagen“ and „E-Mails brauchen Aufmerksamkeit“ (mail inbox)
  - „Neue Anmeldung“ (sign-in alerts)
- The existing sign-in warning mail becomes the e-mail side of „Neue Anmeldung“. It stays mandatory and cannot be switched off; Pushover can be added to it.
- Recipients follow the sharing model: booking events go to users with a role on the unit, mail events to the mailbox owner. The role `ADMIN` alone receives nothing about domain data.

Nothing existing is removed, and no existing data changes. Users who change nothing get exactly the mails they get today.

## Capabilities

### New Capabilities
- `notifications`: event types, channels, the per-user matrix, short and long form, recipients, defaults and background delivery, including the e-mail channel.
- `pushover`: the Pushover channel: the admin's application token, the user's own key, test notification, what is sent to Pushover and how failures are handled.

### Modified Capabilities
- `sign-in-alerts`: the warning becomes a notification that is always sent by e-mail and optionally also by Pushover.
- `bookings`: notifications about bookings and changes awaiting review, and the daily arrival and departure digest.
- `mail-inbox`: notifications about a failing regular fetch and about new mails that need attention.

## Impact

- **Backend:** new package `core.notifications` (public API for modules, channel SPI, settings API) with the Pushover channel; `SharingService`/unit access gain a lookup of the users with a role on an object; `SignInMonitor`, `MailService` and the vacation rental mail import raise notifications; a daily job in vacation rental.
- **Database:** new tables in `core` (notification settings, Pushover application token, Pushover user keys) and one in `vacationrental` (digest runs), each by a new migration. Additions only.
- **Frontend:** new card on `/konto`, new admin page `/einstellungen/pushover` with a sidebar entry.
- **External:** a new outbound connection from the backend to `api.pushover.net`. Short texts (property and unit names, periods, agency names, browser and location of a sign-in) leave the system to Pushover; guest data and amounts do not.
- **Configuration:** `LOGIS_PUBLIC_URL` is reused for links in notifications; without it notifications carry no link. A base address for the Pushover API is configurable for tests.
- **Tests:** backend tests per capability, frontend tests for both pages, a new end-to-end journey with a stand-in for the Pushover API in the e2e stack, and rows in `e2e/COVERAGE.md`.
