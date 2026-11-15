## ADDED Requirements

### Requirement: Bulk actions on transactions
Transaction lists SHALL offer, for selected transactions, „Kategorie zuordnen“, „Objekt zuordnen“ (a property or unit, or none) and „Löschen“. Each transaction SHALL be changed under the rules of a single change, including rights, active categories and targets only, and transfers being changed only as transfers („Umbuchungen werden über die Umbuchung bearbeitet.“). A transaction changed by someone else in the meantime MUST fail with „Die Transaktion wurde inzwischen geändert“ instead of being overwritten. Deleting one side of a transfer SHALL delete the transfer, as for a single deletion.

#### Scenario: Categorise a month
- **WHEN** Anna selects 14 card payments of the supermarket and assigns „Lebensmittel“
- **THEN** all 14 show the category „Lebensmittel“

#### Scenario: Transfer in the selection
- **WHEN** Anna assigns a category to 5 selected transactions, one of which is a transfer
- **THEN** 4 get the category and the report names „Umbuchungen werden über die Umbuchung bearbeitet.“ for the transfer
