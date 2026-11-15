## ADDED Requirements

### Requirement: Bulk actions on bookings
The booking list SHALL offer, for selected bookings, „Bestätigen“ for bookings „zu prüfen“, „Stornieren“, „Löschen“ and „Abrechnung zuordnen“. Assigning SHALL let the user choose a statement and enter a commission rate in percent and a cleaning amount. Each booking SHALL be assigned with its expected rent as gross, the commission rounded to cents, and the cleaning amount. Bookings of another unit than the statement's („Die Buchung gehört zu einer anderen Einheit“), bookings already on a statement („Die Buchung ist bereits abgerechnet“) and bookings without expected rent („Bitte einen Bruttobetrag angeben.“) MUST fail with that message. Confirming SHALL apply to new imported bookings only: a booking with a proposed change MUST fail with „Änderungsvorschläge bitte einzeln prüfen“, so old and new values are compared one by one, and any other booking with „Die Buchung ist nicht „zu prüfen““. Every action MUST apply the rules of the single action.

#### Scenario: Confirm imported bookings
- **WHEN** Anna filters by „zu prüfen“, selects all 6 bookings and chooses „Bestätigen“
- **THEN** all 6 become ordinary bookings and disappear from the filtered list

#### Scenario: Assign a month to a statement
- **WHEN** Anna selects the 7 Fewo & Meer bookings of „Haus Seeufer“ in October 2026 and assigns them to the October statement with 15 % commission and 60.00 cleaning
- **THEN** the statement has 7 lines, each with the expected rent as gross, 15 % of it as commission and 60.00 cleaning, and the bookings show the status settled

#### Scenario: Booking of another unit
- **WHEN** Anna assigns a booking of „Whg. 1“ together with others to a statement of „Haus Seeufer“
- **THEN** that booking is not assigned and the report names „Die Buchung gehört zu einer anderen Einheit“
