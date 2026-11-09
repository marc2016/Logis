# Spec Delta

## Purpose

Property finances show on each property what it earns and what it costs: costs recorded directly on the property or its units, items contributed by modules such as vacation rental statements, and the resulting income, expenses and result per month and year. They work without bank accounts and can later be linked to bank transactions.

## ADDED Requirements

### Requirement: Record a cost
A user with at least `EDITOR` on a property, or as effective role on one of its units, SHALL be able to record a cost on it with date, amount, and optionally category, counterparty and note. The amount MUST be positive, have at most two decimal places and not exceed 999,999,999.99. Only active expense categories MUST be selectable. No account MUST be required.

#### Scenario: Repair invoice
- **WHEN** Anna records a cost of 320.00 on 2026-11-12 on "Haus Seeufer" with category "Reparatur" and counterparty "Handwerker Meyer"
- **THEN** the cost appears in the property's finances as an expense of 320.00 in November

#### Scenario: Viewer may not record
- **WHEN** Dieter, as `VIEWER` of the property, tries to record a cost
- **THEN** the system responds with HTTP 403

### Requirement: Edit and delete a cost
A user with at least `EDITOR` on the cost's target SHALL be able to change and delete it. Concurrent changes MUST be detected, and the second change MUST be rejected with „Die Kosten wurden inzwischen geändert“.

#### Scenario: Correct the amount
- **WHEN** Anna changes the cost from 320.00 to 340.00
- **THEN** November's expenses rise by 20.00

### Requirement: Receipts on costs
A user with at least `EDITOR` on the cost's target SHALL be able to attach receipts to a cost, download them and delete them. The same file rules as for booking documents MUST apply. Deleting a cost MUST delete its receipts.

#### Scenario: Attach an invoice
- **WHEN** Anna attaches the PDF invoice to the repair cost
- **THEN** the cost shows the receipt, and Anna can download it

### Requirement: Contributed items
Modules SHALL be able to contribute income and expense items to a property's finances, each with date, unit, label and amount. The vacation rental module MUST contribute, per statement line, the gross as income and the commission and cleaning as expenses, dated on the statement date. Planned bookings MUST NOT contribute. Items of a disabled module MUST NOT appear.

#### Scenario: Settled booking on the property
- **WHEN** booking "250905" is on a statement dated 2026-11-30 with gross 248.58, commission 37.29 and cleaning 60.00
- **THEN** the property's finances show in November an income of 248.58 and expenses of 37.29 („Provision“) and 60.00 („Reinigung“)

### Requirement: Finance overview
The property page SHALL show, for a year, per month and in total: income, expenses and result (income − expenses), from costs and contributed items. It SHALL list all items of the year, filterable by unit and kind and sortable by date, amount, unit and label. Transactions attributed to the property MUST NOT count in the result.

#### Scenario: November at the holiday home
- **WHEN** November 2026 has the settled booking above and the repair of 320.00
- **THEN** the overview shows for November an income of 248.58, expenses of 417.29 and a result of -168.71

### Requirement: Visibility of finance items
Items of a unit MUST be visible to users with an effective role on that unit. Items on the property itself MUST be visible only to users with a role on the property. Users without either MUST receive HTTP 404 for the property's finances.

#### Scenario: Unit-only user
- **WHEN** Carla, as `EDITOR` of "Whg. 2" only, opens the finances of its property
- **THEN** they see the items of "Whg. 2" only, and the totals cover only those
