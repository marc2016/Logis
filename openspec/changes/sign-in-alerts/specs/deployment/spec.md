## ADDED Requirements

### Requirement: GeoIP database
The backend SHALL download the free DB-IP Lite city database at startup if it is missing, and then once a month, into a volume that survives the recreation of the containers. A failed download MUST be logged and MUST NOT stop the backend; the previous file stays in use. Without a database, sign-ins have no location and everything else works. A setting `LOGIS_GEOIP_ENABLED=false` SHALL switch the download and the lookups off. The README MUST credit DB-IP as the database licence requires.

#### Scenario: First start
- **WHEN** the stack starts for the first time with outbound access
- **THEN** the backend downloads the database, and later sign-ins have a location

#### Scenario: No outbound access
- **WHEN** the download fails
- **THEN** the backend runs, sign-ins have no location, and the log names the failed download

#### Scenario: Recreated containers
- **WHEN** the containers are recreated after an update
- **THEN** the downloaded database is still there and is not downloaded again before the next month

## MODIFIED Requirements

### Requirement: Outbound connections only from the backend
The backend SHALL be able to open outbound connections, for example to an IMAP server, to the configured outgoing mail server, to the download of the GeoIP database and, if enabled, to the AI service. The database and the backup service MUST NOT be able to open connections outside the stack. Outbound access MUST NOT publish any additional port.

#### Scenario: Mail import in the container stack
- **WHEN** a user configures an IMAP mailbox on the internet in the running stack
- **THEN** the backend can connect to it, and no new host port is opened

#### Scenario: Database stays isolated
- **WHEN** the database container tries to reach a host outside the stack
- **THEN** no connection is possible
