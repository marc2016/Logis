## ADDED Requirements

### Requirement: Bulk actions in the inbox
The inbox SHALL offer, for selected mails, „Erneut verarbeiten“, „Ignorieren“ and „Löschen“. A handling part MAY add bulk actions. The booking part SHALL add „Einheit zuordnen“ for mails waiting for a unit, which assigns the chosen unit to each mail's agency and property name and processes the waiting mails again. Mails a bulk action does not apply to MUST fail with a message instead of being changed.

#### Scenario: Assign a unit to several mails
- **WHEN** Anna selects three mails of Fewo & Meer for „Haus Seeufer - Nordhorn“ that wait for a unit and assigns „Haus Seeufer“
- **THEN** all three are processed again and show „Buchung angelegt“ or „angehängt“

#### Scenario: Delete old mails
- **WHEN** Anna selects 20 ignored mails and confirms „Löschen“
- **THEN** they disappear with their content and are not fetched again
