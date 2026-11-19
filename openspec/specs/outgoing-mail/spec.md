# outgoing-mail Specification

## Purpose

Lets Logis send e-mails, for example security notices to users, through an outgoing mail server that the admin configures.

## Requirements

### Requirement: Configure the outgoing mail server
On the page „E-Mail-Versand“ under *Einstellungen*, an admin SHALL be able to configure one outgoing mail server for the whole system:
- server, port, encryption („Keine“, „STARTTLS“, „SSL/TLS“);
- username and password;
- sender address and sender name.

The password MUST be stored encrypted and MUST never be returned. Users without the role `ADMIN` MUST NOT see or change the configuration.

#### Scenario: Configure
- **WHEN** the admin enters server „smtp.example.org“, port 587, STARTTLS, username, password and sender „logis@example.org“, and saves
- **THEN** the configuration is stored, and the password field shows only that a password is set

#### Scenario: Keep the password
- **WHEN** the admin changes only the sender name and leaves the password empty
- **THEN** the stored password stays in use

#### Scenario: Ordinary user
- **WHEN** a user without the role `ADMIN` requests the configuration
- **THEN** the system responds with HTTP 403

### Requirement: Check the connection on save
Saving the configuration MUST test the connection with the given credentials. An unreachable server or wrong credentials MUST be rejected with „Der Mailserver ist nicht erreichbar oder die Anmeldung ist fehlgeschlagen“, and the previous configuration MUST stay in effect.

#### Scenario: Wrong password
- **WHEN** the admin saves the configuration with a wrong password
- **THEN** the system rejects it with that message, and the previous configuration is still used

### Requirement: Test mail
The page SHALL offer „Testmail senden“ to an address the admin enters. The test mail MUST be sent with the stored configuration. A failure MUST be shown with „Die Testmail konnte nicht gesendet werden“.

#### Scenario: Send a test mail
- **WHEN** the admin sends a test mail to „admin@example.org“
- **THEN** a mail with the subject „Testmail von Logis“ arrives there from the configured sender

### Requirement: Send system mails
Parts of Logis SHALL be able to send plain-text mails to an address through the configured server, with the configured sender. If no server is configured, sending MUST fail without trying. The caller MUST learn whether sending succeeded. Failures MUST be logged without the mail's text.

#### Scenario: No server configured
- **WHEN** no outgoing mail server is configured and a part of Logis sends a mail
- **THEN** no connection is attempted, and the caller learns that sending is not set up

#### Scenario: Server down
- **WHEN** the configured server is unreachable while a part of Logis sends a mail
- **THEN** the caller learns that sending failed, and the log names the recipient's domain but not the text
