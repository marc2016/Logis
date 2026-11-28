## MODIFIED Requirements

### Requirement: Tile catalog
The catalog SHALL offer these tiles with these settings:
- „Zu erledigen“: no settings. Counts inbox mails waiting for a unit, mails with a proposed change, unrecognised mails and bookings awaiting review, each linking to the matching list; „Buchungen prüfen“ SHALL open the review mode of the bookings.
- „Nächste An- und Abreisen“: period of 7, 14 or 30 days; one unit or all. Lists arrivals and departures in that period, soonest first.
- „Belegung“: one unit or all; with or without the previous year. Shows the current year's occupancy per month and in total.
- „Einnahmen“: one unit or all. Shows the settled net income of the current year up to today and of the same period of the previous year.
- „Kontostände“: all accounts or a selection. Shows each account's balance.
- „Letzte Transaktionen“: 5 or 10 entries; one account or all. Lists the latest transactions.
- „Mietrückstände“: no settings. Lists the tenancies with an overdue month, largest arrears first, each with unit, tenants and arrears, linking to the tenancy; „Keine Mietrückstände.“ when there is none.

The tiles „Nächste An- und Abreisen“, „Belegung“ and „Einnahmen“ SHALL belong to the vacation rental module and exist only while it is active. The tile „Mietrückstände“ SHALL belong to the long-term rental module and exist only while it is active.

#### Scenario: Things to do
- **WHEN** Anna has two mails waiting for a unit and one booking awaiting review
- **THEN** „Zu erledigen“ shows 2 for „Einheit zuordnen“, linking to the inbox, and 1 for „Buchungen prüfen“, opening the review mode

#### Scenario: Arrivals of one unit
- **WHEN** „Nächste An- und Abreisen“ is set to 7 days and „Haus Düne“
- **THEN** it lists only arrivals and departures of „Haus Düne“ in the next 7 days, soonest first

#### Scenario: Nothing to do
- **WHEN** nothing needs attention
- **THEN** „Zu erledigen“ says „Alles erledigt.“

#### Scenario: Rent arrears
- **WHEN** Anna adds „Mietrückstände“ and one tenancy is 800,00 behind with an overdue month
- **THEN** the tile lists that tenancy with its unit, tenants and „800,00 €“, linking to the tenancy
