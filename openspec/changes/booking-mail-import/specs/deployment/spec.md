# Spec Delta

## ADDED Requirements

### Requirement: Outbound connections only from the backend
The backend SHALL be able to open outbound connections, for example to an IMAP server and, if enabled, to the AI service. The database and the backup service MUST NOT be able to open connections outside the stack. Outbound access MUST NOT publish any additional port.

#### Scenario: Mail import in the container stack
- **WHEN** a user configures an IMAP mailbox on the internet in the running stack
- **THEN** the backend can connect to it, and no new host port is opened

#### Scenario: Database stays isolated
- **WHEN** the database container tries to reach a host outside the stack
- **THEN** no connection is possible
