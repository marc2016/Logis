## ADDED Requirements

### Requirement: Ticket receipts
The system SHALL recognise a mail from Fewo & Meer whose text confirms receipt of the owner's mail under a ticket ID („vielen Dank für Ihre E-Mail“ and „Ticket-ID:“) as a ticket receipt. A ticket receipt SHALL be shown in the inbox as „Ignoriert“ with the kind „Eingangsbestätigung“, the agency „Fewo & Meer“ and the ticket ID as reference. It MUST NOT create, change or attach to a booking, and it MUST NOT be sent to the AI. A mail of the same ticket without the receipt text MUST be handled as before.

#### Scenario: Receipt for an owner block request
- **WHEN** Fewo & Meer answers the owner's mail with „vielen Dank für Ihre E-Mail … Ticket-ID: ABC-123-45678“ under the subject „[#ABC-123-45678]: Eigentümerblockung“
- **THEN** the mail shows as „Ignoriert“ with the kind „Eingangsbestätigung“ and the reference „ABC-123-45678“, and no booking is created or changed

#### Scenario: Staff reply in the same ticket
- **WHEN** a mail with the subject „[#ABC-123-45678]: Eigentümerblockung“ contains a staff reply without the receipt text
- **THEN** it is handled as before, for example „Nicht erkannt“

#### Scenario: Receipt with AI enabled
- **WHEN** AI is enabled and a ticket receipt arrives
- **THEN** the mail is not sent to the AI

#### Scenario: Stored receipt processed again
- **WHEN** a receipt stored as „Nicht erkannt“ before this change is processed again with „Erneut verarbeiten“
- **THEN** it shows as „Ignoriert“ with the kind „Eingangsbestätigung“
