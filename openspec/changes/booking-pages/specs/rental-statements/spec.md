## ADDED Requirements

### Requirement: Statement page
Every statement SHALL open as a page of its own with an address containing the statement's id, reached from the list of statements. The page SHALL have a header with provider, unit, period, statement date and payout, and SHALL show the statement's lines with their actions. The back button SHALL return to the list of statements with its unit filter. Earlier addresses that selected a statement in the list SHALL lead to its page. A statement that does not exist or is not visible MUST show „Abrechnung nicht gefunden“.

#### Scenario: Open a statement
- **WHEN** Anna clicks the November statement of „Haus Seeufer“ in the list
- **THEN** its page opens at its own address with the provider, the period 2026-11-01 to 2026-11-30 and the payout in the header

#### Scenario: Old link
- **WHEN** Anna opens a bookmarked address of the statement list with the November statement selected
- **THEN** the page of the November statement opens
