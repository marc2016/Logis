# Development data

`seed.sql` fills an empty development database with test accounts and data. The backend runs it at startup with the `dev` profile (`./start.sh`), before anything else, and only when no user other than `admin` exists. It never runs in production.

Start over with fresh data:

```sh
./start.sh reset    # deletes the development database, then starts as usual
```

## Test accounts

Every account has the password `logis-dev-passwort` and the TOTP secret `LOGISDEVLOGISDEVLOGISDEVLOGISDEV`. Add it once to an authenticator app, as a manual entry or with this link: `otpauth://totp/Logis:dev?secret=LOGISDEVLOGISDEVLOGISDEVLOGISDEV&issuer=Logis`.

| User | Role | What it shows |
|---|---|---|
| `admin` | Admin | User management; no access to domain data |
| `anna` | Owner | Everything: two holiday homes with three units, an apartment building, her own flat, four accounts |
| `bert` | Editor | Only the holiday flat „Strandhaus Dünenblick · Whg. 1“ |
| `carla` | Viewer | „Haus Seeufer“ and the „GbR-Konto“, read only, without guest contact data |
| `dieter` | New user | Must set up two-factor sign-in and change the start password |
| `erik` | Disabled | Cannot sign in |

## Data

- **Holiday homes** (Muster GbR): „Haus Seeufer“ and „Strandhaus Dünenblick“ with Whg. 1 and Whg. 2.
  - Over 300 bookings from January 2025 to June 2027, with longer stays in summer, a few cancellations, and the agencies Fewo & Meer, Holidu, Airbnb, Booking.com and Direkt.
  - Monthly statements of Fewo & Meer, Holidu and Booking.com up to October 2026.
  - Four bookings imported from mails, with their mails as sources: one „zu prüfen“, one with a proposed change and a name edited by hand, one from Holidu, and one from VVV Nordhorn with a block notice.
- **Apartment building Lindenstraße** (Anna privat): four flats and a garage with monthly rents. The attic flat stands empty from March to May 2026.
- **Accounts:**
  - Girokonto, Tagesgeld, Barkasse and GbR-Konto, with salary, rents, payouts, utilities, property tax, insurance, groceries, a standing order and interest.
  - Repairs both as transactions and as property costs.

All names, addresses, IBANs and amounts are invented. The IBANs are the usual documentation examples.

When a migration changes a table the seed writes to, adapt `seed.sql` in the same change.
