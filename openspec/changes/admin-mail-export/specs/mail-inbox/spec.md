## MODIFIED Requirements

### Requirement: Inbox
A user SHALL see the mails fetched from their mailbox with date, sender, subject, and what the handling part reported (kind, result, linked record). They SHALL be able to open the mail text, retry processing, ignore a mail and delete it; handling parts MAY offer further actions, such as assigning a unit. Deleting MUST remove the stored content including guest data and MUST keep the mail from being fetched again. Other users MUST NOT see a user's inbox. The only exception is the pseudonymized export by an admin, which needs the owner's consent (see `mail-export`).

#### Scenario: Retry after assigning a unit
- **WHEN** Anna assigns a unit to a mail „Einheit zuordnen“
- **THEN** the mail is processed again and shows „Buchung angelegt“

#### Scenario: Delete a mail
- **WHEN** Anna deletes a mail in the inbox
- **THEN** it disappears from the list together with its content, and the next poll does not fetch it again

#### Scenario: Another user's inbox
- **WHEN** an admin without Anna's consent looks for Anna's mails
- **THEN** they find neither the inbox nor an export of it
