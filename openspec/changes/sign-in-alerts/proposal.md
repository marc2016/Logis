# Proposal

## Why

„Angemeldet bleiben“ keeps devices signed in for up to 90 days without asking for anything. Logis has no risk detection that would notice a stolen account or cookie. Users should learn right away when someone signs in to their account from a device or country they have not used before. A stolen device cookie should stop working when it shows up in another country. Outgoing mail and user e-mail addresses exist since the change `outgoing-mail`.

## What Changes

- **Recognise devices.** Every complete sign-in sets a long-lived device cookie (random, 1 year), independent of „Angemeldet bleiben“. A sign-in without a known device cookie of this user counts as a **new device**.
- **Locate sign-ins.** Logis looks up the country, and for display the city, of the client address in a local GeoIP database (DB-IP Lite). No address is sent to a third party.
  - A sign-in from a country the user did not sign in from in the last 180 days counts as a **new location**.
  - Addresses from local networks have no location.
- **Warning mail.** After a complete sign-in from a new device or a new location, the user gets the mail „Neue Anmeldung bei Logis“ at their address.
  - The mail names the time, the device („Firefox auf Windows“) and the location („Hamburg, Deutschland“ or „unbekannt“).
  - It tells the user what to do if it was not them: change the password, and sign out all other devices under „Mein Konto“.
  - The very first sign-in of a user sends no warning.
  - Users without an address get no mail, and no mail is sent while no outgoing mail server is configured.
  - Sending runs in the background, so the sign-in does not wait for the mail server.
- **Remembered devices and location.** A remembered device's credential that is presented from another country than the one it was issued in is rejected, and the device is forgotten. The user then signs in normally, which can trigger the warning.
- **Device list.** „Angemeldete Geräte“ also shows the location of the last use.
- **GeoIP database.**
  - The backend downloads the free DB-IP Lite database (city level) at startup if it is missing, and then monthly, into a volume.
  - Without the database, locations are „unbekannt“, location checks are skipped, and the device warning still works.
  - The README credits DB-IP as the licence requires.
- **Sign-in history.** For each complete sign-in Logis stores user, time, device identifier hash, browser, operating system, country and city. It stores no IP address. Entries older than 180 days are deleted.

## Capabilities

### New Capabilities
- `sign-in-alerts`: device recognition, location of sign-ins, the warning mail and the sign-in history behind them.

### Modified Capabilities
- `authentication`:
  - „Protection of remembered devices“ also rejects a credential from another country;
  - „Manage signed-in devices“ shows the location of the last use.
- `deployment`:
  - new requirement for the GeoIP database (download, volume, operation without it);
  - „Outbound connections only from the backend“ names the GeoIP download.

## Impact

- **Backend (`core.auth`):**
  - device cookie `LOGIS_DEVICE` and the sign-in history;
  - the check after every completed sign-in (the hook `onAuthenticated` from remember-me);
  - the warning mail through `MailSender`, sent asynchronously after commit;
  - the country check on restoring a remembered device;
  - location in the device list.
- **GeoIP:** a new dependency `com.maxmind.db:maxmind-db`, which reads `.mmdb` files (Apache 2.0).
  - A downloader runs at startup and monthly.
  - A lookup service returns country code, German country name and German city name.
- **Database:** one new migration in `core`:
  - a new table `core.sign_ins`;
  - nullable `country` and `city` columns on `core.remembered_devices`.
  - Existing data stays unchanged.
- **Deployment:**
  - a volume `geoip` for the backend at `/var/lib/logis/geoip`; the Dockerfile creates the directory for the `logis` user;
  - `LOGIS_GEOIP_ENABLED` (default `true`) to switch the download off.
- **Frontend:** location in the device list.
- **Tests:**
  - backend tests with a small test `.mmdb` file built in the test;
  - mail tests with GreenMail;
  - an e2e test: the second sign-in from a new browser sends the warning mail to the test mailbox.
