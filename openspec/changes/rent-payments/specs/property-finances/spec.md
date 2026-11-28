## MODIFIED Requirements

### Requirement: Contributed items
Modules SHALL be able to contribute income and expense items to a property's finances, each with date, unit, label and amount. The vacation rental module MUST contribute, per statement line, the gross as income and the commission and cleaning as expenses, dated on the statement date. Planned bookings MUST NOT contribute. The long-term rental module MUST contribute each rent payment as income labelled „Miete“ with the tenants' names, dated on the payment date. Items of a disabled module MUST NOT appear.

#### Scenario: Settled booking on the property
- **WHEN** booking "250905" is on a statement dated 2026-11-30 with gross 248.58, commission 37.29 and cleaning 60.00
- **THEN** the property's finances show in November an income of 248.58 and expenses of 37.29 („Provision“) and 60.00 („Reinigung“)

#### Scenario: Rent payment on the property
- **WHEN** a rent payment of 800,00 dated 3 April 2027 is recorded for the tenancy of Erika Musterfrau in „Whg. 1“
- **THEN** the property's finances show in April an income of 800,00 („Miete Erika Musterfrau“) for „Whg. 1“, and the payment's transaction does not count a second time
