# dashboard Specification

## Purpose

The start page „Übersicht“ shows each user a dashboard of tiles they choose and configure themselves: what needs attention, upcoming stays, occupancy, income and accounts.

## Requirements

### Requirement: Tiles on the start page
The start page „Übersicht“ SHALL show the signed-in user's tiles in their chosen order. The layout SHALL arrange the tiles in columns that fit the screen: one column on phones, more on wider screens. A tile MUST show only data the user may see through the per-object sharing model.

#### Scenario: Own arrangement
- **WHEN** Anna has arranged „Kontostände“ above „Belegung“
- **THEN** her start page shows „Kontostände“ first, then „Belegung“

#### Scenario: Phone
- **WHEN** Anna opens the start page on a phone
- **THEN** the tiles appear in one column in her order, and no tile sticks out of the page

### Requirement: Default arrangement
A user who has never customised the start page SHALL see the default tiles „Zu erledigen“, „Nächste An- und Abreisen“, „Belegung“ (all units) and „Kontostände“ (all accounts). Tiles of inactive modules MUST be left out.

#### Scenario: New user
- **WHEN** a new user signs in for the first time
- **THEN** the start page shows the default tiles

#### Scenario: Module inactive
- **WHEN** the vacation rental module is inactive
- **THEN** the default shows „Zu erledigen“ and „Kontostände“ only

### Requirement: Customising the start page
The start page SHALL offer „Anpassen“. In that mode the user SHALL be able to:
- add a tile from the catalog of tiles of active modules;
- remove a tile;
- move a tile up or down;
- change a tile's settings;
- reset to the default with „Standard wiederherstellen“.

A tile type MAY appear more than once with different settings. The arrangement SHALL be saved per user and apply on every device. „Fertig“ SHALL end the mode.

#### Scenario: Add a second occupancy tile
- **WHEN** Anna adds „Belegung“ for „Haus Düne“ next to her existing „Belegung“ for all units
- **THEN** both tiles show, each with its own unit, also after signing in on another device

#### Scenario: Reorder and remove
- **WHEN** Anna moves „Kontostände“ up and removes „Letzte Transaktionen“
- **THEN** the new order is shown and kept after a reload

#### Scenario: Reset
- **WHEN** Anna chooses „Standard wiederherstellen“
- **THEN** her start page shows the default tiles again

#### Scenario: Users do not share arrangements
- **WHEN** Anna customises her start page
- **THEN** Bert's start page stays unchanged

### Requirement: Tile catalog
The catalog SHALL offer these tiles with these settings:
- „Zu erledigen“: no settings. Counts inbox mails waiting for a unit, mails with a proposed change, unrecognised mails and bookings awaiting review, each linking to the matching list; „Buchungen prüfen“ SHALL open the review mode of the bookings.
- „Nächste An- und Abreisen“: period of 7, 14 or 30 days; one unit or all. Lists arrivals and departures in that period, soonest first.
- „Belegung“: one unit or all; with or without the previous year. Shows the current year's occupancy per month and in total.
- „Einnahmen“: one unit or all. Shows the settled net income of the current year up to today and of the same period of the previous year.
- „Kontostände“: all accounts or a selection. Shows each account's balance.
- „Letzte Transaktionen“: 5 or 10 entries; one account or all. Lists the latest transactions.

The tiles „Nächste An- und Abreisen“, „Belegung“ and „Einnahmen“ SHALL belong to the vacation rental module and exist only while it is active.

#### Scenario: Things to do
- **WHEN** Anna has two mails waiting for a unit and one booking awaiting review
- **THEN** „Zu erledigen“ shows 2 for „Einheit zuordnen“, linking to the inbox, and 1 for „Buchungen prüfen“, opening the review mode

#### Scenario: Arrivals of one unit
- **WHEN** „Nächste An- und Abreisen“ is set to 7 days and „Haus Düne“
- **THEN** it lists only arrivals and departures of „Haus Düne“ in the next 7 days, soonest first

#### Scenario: Nothing to do
- **WHEN** nothing needs attention
- **THEN** „Zu erledigen“ says „Alles erledigt.“

### Requirement: Settings that are no longer valid
A tile whose setting points to a unit or account the user can no longer see SHALL show „Nicht mehr verfügbar“ with a hint to change its settings, and MUST NOT show that object's data. Tiles of an inactive module MUST be hidden, and their settings MUST be kept, so that they return when the module is active again.

#### Scenario: Share revoked
- **WHEN** Anna's „Belegung“ tile shows „Haus Düne“ and Bert revokes her share
- **THEN** the tile shows „Nicht mehr verfügbar“ and no occupancy figures

#### Scenario: Module switched off and on again
- **WHEN** the vacation rental module is deactivated and later activated again
- **THEN** Anna's vacation rental tiles are hidden in between and return afterwards with their settings
