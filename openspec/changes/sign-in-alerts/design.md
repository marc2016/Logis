# Design

## Context

- **The hook after a completed sign-in.** Since remember-me, every sign-in step that reaches `AUTHENTICATED` calls `AuthController.completed(...)`, which calls `RememberedDeviceService.onAuthenticated`. This covers TOTP, enrollment, forced password change, passkey and the dev bypass.
  - Sessions restored from a remembered device do not go through it: they are not sign-ins.
- **Remembered devices.**
  - `RememberedDeviceService.restore` already rejects a different browser or OS family (`DeviceFamily`) and forgets the series.
  - `core.remembered_devices` stores `browser` and `os`.
- **Client address.** The client address comes from `request.getRemoteAddr()`, which reflects `X-Forwarded-For` through the trusted proxy chain (`forward-headers-strategy: native`, nginx `real_ip`).
- **Mail.** `core.mail.MailSender` sends plain text and throws `MailSendException`. `User.getEmail()` may be null.
- **Container.** The backend container runs as user `logis` (uid 10001) and has no volume today. It reaches the internet through the `egress` network.

## Goals / Non-Goals

**Goals:**
- One warning per suspicious sign-in, with few false alarms.
- Location without sending addresses anywhere.
- No effect on sign-in latency or success.

**Non-Goals:**
- Blocking or challenging suspicious sign-ins. Users are informed, nothing else.
- Impossible-travel or velocity checks.
- An in-app list of past sign-ins (the history is internal for now).
- City-level warnings (decided: country level).

## Decisions

### D1. Device identifier cookie
- Cookie `LOGIS_DEVICE`:
  - 32 random bytes, base64url;
  - `HttpOnly`, `Secure` behind HTTPS, `SameSite=Lax`, `Path=/api`, `Max-Age` one year.
- It is set (or kept) on every completed sign-in, and its lifetime is renewed each time.
- Only `SHA-256(cookie)` is stored, in `core.sign_ins.device_hash`.
- A sign-in counts as a known device if the presented cookie's hash appears in this user's sign-ins within the last 180 days.
- `/api/auth/logout` does not clear it, because it identifies the browser, not the session.
- Alternative considered: deciding "known" from the browser/OS family alone. Every Chrome on Windows would then be "known". The cookie is cheap and much more precise.

### D2. Sign-in history
- New table `core.sign_ins`:
  - `id`, `user_id` (FK, `ON DELETE CASCADE`), `at`, `device_hash BYTEA`;
  - `browser`, `os`, `country CHAR(2)` (nullable), `city VARCHAR(128)` (nullable);
  - an index on `(user_id, at)`.
- There is no IP column.
- A daily job deletes rows older than 180 days. It reuses the schedule of the remembered-device clean-up.

### D3. Decision and warning (`SignInMonitor`)
- `SignInMonitor.signedIn(request, response, user)` is called from `AuthController.completed` next to `onAuthenticated`. It:
  1. reads or creates the device cookie;
  2. looks up the location of `request.getRemoteAddr()` (D4);
  3. loads the user's sign-ins of the last 180 days;
  4. decides:
     - `first` = no rows;
     - `newDevice` = no row with this device hash;
     - `newLocation` = a country is known and no row has that country;
  5. inserts the new row;
  6. if `!first && (newDevice || newLocation)` and the user has an address and `MailSender.configured()`, hands the mail to the `applicationTaskExecutor` after the transaction commits (`TransactionSynchronization.afterCommit`, or right away without a transaction).
- The mail task catches every exception and logs only „Sign-in warning could not be sent“ with the user id.
- Timestamps in the mail are formatted in `Europe/Berlin` as `19.11.2026, 14:02 Uhr`.

### D4. GeoIP lookup
- Dependency `com.maxmind.db:maxmind-db` (4.2.0, the latest stable release; Apache 2.0) reads the DB-IP Lite City `.mmdb` (CC BY 4.0).
- `GeoLocator` is an interface with `Optional<Location> locate(String ip)`. `Location(countryCode, countryName, city)` uses the German names (`names.de`, falling back to `names.en`).
- The implementation `MmdbGeoLocator`:
  - holds a `Reader` that is swapped atomically after a new download;
  - returns empty for loopback, site-local, link-local, unique-local (fc00::/7), CGNAT (100.64/10) and benchmarking (198.18/15) addresses, for unknown addresses, and while no file exists.
- Tests use a test file written by a small test helper (`MmdbTestWriter`) for a handful of documentation networks (203.0.113.0/24 → DE Hamburg, 198.51.100.0/24 → ES Madrid). The integration tests also use a stub `GeoLocator` keyed by IP for the flows.

### D5. Download
- `GeoIpUpdater` has two settings:
  - `logis.geoip.enabled` (env `LOGIS_GEOIP_ENABLED`, default `true`);
  - `logis.geoip.directory` (default `/var/lib/logis/geoip`; locally `./data/geoip` in the dev profile).
- At startup (asynchronous, so startup is not delayed) and by cron on the 3rd of each month, it:
  - downloads `https://download.db-ip.com/free/dbip-city-lite-YYYY-MM.mmdb.gz` for the current month, falling back to the previous month if the current one is not published yet (404);
  - stores it gzip-decoded as `dbip-city-lite.mmdb.tmp`, opens it as a test, renames it atomically to `dbip-city-lite.mmdb`, and swaps the reader;
  - skips the download if the existing file is from the current month (file modification time).
- Failures are logged at WARN, and the old file stays in use.
- The HTTP client is the JDK `HttpClient` with a 30 s connect and 5 min overall timeout and redirects followed.

### D6. Remembered devices and country
- `core.remembered_devices` gets `country CHAR(2)` and `city VARCHAR(128)`, both nullable. Existing rows keep `NULL`, which means "not compared".
- Issue and restore set them from the lookup of the current request.
- `restore` rejects and forgets the series, like a browser change (log reason `REMEMBER_COUNTRY_CHANGED`), if both the stored and the current country are known and differ.
- The stored country is the one of the last known use: issue, restore and the hourly touch while a session is active all set it when a location is known. A fresh sign-in replaces the device anyway.
- `last_used` updates refresh country and city. Restore updates them.
- The device list returns `city` and `countryName`. The frontend shows „Hamburg, Deutschland“, the country alone, or nothing.

### D7. Deployment
- The Dockerfile creates `/var/lib/logis/geoip` owned by `logis`, so a new named volume inherits that ownership.
- `compose.yml` mounts the volume `geoip:/var/lib/logis/geoip` and passes `LOGIS_GEOIP_ENABLED`.
- The e2e env sets `LOGIS_GEOIP_ENABLED=false`: e2e clients use the benchmarking range and must not download 130 MB in CI.
- The README describes the download, the volume, the switch, and credits „IP Geolocation by DB-IP“ (https://db-ip.com) under CC BY 4.0.

## Risks / Trade-offs

- [VPN users or travellers get warnings] → Only the country is compared, and a known device plus a known country sends nothing. One warning per new country is acceptable.
- [Cleared cookies or private windows count as new devices] → They get a warning on each sign-in. That is correct from a security point of view, and the mail says what to do.
- [DB-IP URL scheme changes] → The download fails gracefully, and the device warnings keep working. The URL lives in one constant.
- [130 MB download per month on a mini PC] → It runs once a month and only if the file is outdated. It can be switched off.
- [Remembered device rejected after a holiday abroad] → The user signs in normally once. That is the intended trade-off for theft protection.
- [Mail task lost on shutdown] → It is best effort: the warning is informational, and the history row is already stored.
- [Dormant accounts] → A user without any stored sign-in counts as "first", so a sign-in after more than 180 days without one sends no warning. This is accepted to avoid a warning for every existing user right after the update.

## Migration Plan

1. Migration `V20261119.1500__core_sign_in_alerts.sql`: creates `core.sign_ins` and adds `country` and `city` to `core.remembered_devices`. No data changes.
2. Existing users have no history, so their first sign-in after the update counts as "first" and sends no warning.
3. Deployment: new volume in `compose.yml`. `docker compose up -d` creates it.
4. Rollback: the previous release ignores the table, the columns and the cookie.
