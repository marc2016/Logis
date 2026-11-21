# Spec Delta

## ADDED Requirements

### Requirement: Notify about a failing fetch
When the regular poll of a mailbox fails after the previous poll succeeded, the system SHALL raise the notification „Postfach-Abruf fehlgeschlagen“ for the mailbox owner, as an event type of the group „Posteingang“, with a link to the inbox. Further failed polls MUST NOT raise it again until a poll succeeded. Fetches the user starts MUST NOT raise it. The notification MUST NOT contain the mailbox password or the server's error text.

#### Scenario: Server becomes unreachable
- **WHEN** the IMAP server of Anna's mailbox stops answering and Anna switched on Pushover for „Postfach-Abruf fehlgeschlagen“
- **THEN** Anna gets one notification after the first failed poll and none after the following failed polls

#### Scenario: Fails again later
- **WHEN** the poll succeeds again and fails once more the next day
- **THEN** Anna gets a second notification

#### Scenario: Manual fetch
- **WHEN** Anna chooses „Jetzt abrufen“ and the fetch fails
- **THEN** the page shows the error and no notification is sent

### Requirement: Notify about mails that need attention
When a regular poll stores mails that need attention (the mails the quick filter „Offen“ shows), the system SHALL raise one notification „E-Mails brauchen Aufmerksamkeit“ per poll for the mailbox owner, naming their number, with a link to the inbox filtered by „Offen“. Older mails the user fetches, mails processed again, mails received more than two days before and mails Logis sent itself MUST NOT count. The short text MUST NOT contain senders or subjects.

#### Scenario: Unassigned unit
- **WHEN** a poll stores a confirmation whose unit is not assigned and Anna switched on Pushover for „E-Mails brauchen Aufmerksamkeit“
- **THEN** Anna gets „1 E-Mail braucht Aufmerksamkeit“ with a link to the inbox

#### Scenario: Several mails in one poll
- **WHEN** one poll stores three mails that are „Nicht erkannt“
- **THEN** Anna gets one notification naming three mails

#### Scenario: Handled mails
- **WHEN** a poll stores only mails that created bookings
- **THEN** this notification is not sent

#### Scenario: Subjects only by mail
- **WHEN** Anna switched on both channels and a poll stores two mails that are „Nicht erkannt“
- **THEN** the mail lists sender and subject of both, and the Pushover notification only their number

#### Scenario: Own notification mails
- **WHEN** Anna's mailbox is the one her notifications are sent to, and a poll stores a notification mail from Logis
- **THEN** that mail raises no notification
