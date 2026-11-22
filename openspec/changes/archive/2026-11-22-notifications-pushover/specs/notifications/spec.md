# Spec Delta

## Purpose

Tells users about events in Logis without them opening the app, through the channels they choose per kind of event. Parts of Logis contribute the kinds of events; the channels are independent of them, so a new channel needs no change to the events.

## ADDED Requirements

### Requirement: Event types
Parts of Logis SHALL be able to register event types for notifications. An event type MUST have a stable key made of a module part and a name (such as `vacationrental.bookingReview`), a German label and a group label. Event types of a disabled module MUST NOT be offered and MUST NOT cause notifications; the user's switches for them MUST be kept.

#### Scenario: Types of an active module
- **WHEN** the module `vacationrental` is active and Anna opens „Benachrichtigungen“
- **THEN** the group „Ferienvermietung“ lists „Neue Buchung zu prüfen“

#### Scenario: Module switched off and on again
- **WHEN** Anna has switched on Pushover for „Neue Buchung zu prüfen“, the module is disabled and later enabled again
- **THEN** the type is not listed while the module is disabled, and afterwards the switch is on as before

### Requirement: Channels
The system SHALL offer the channels „E-Mail“ and „Pushover“. A channel MUST be usable for a user only when it is set up for the system and for that user: „E-Mail“ needs a configured outgoing mail server and an e-mail address of the user. A notification MUST NOT be sent through a channel that is not usable, and this MUST NOT affect the other channels.

#### Scenario: No e-mail address
- **WHEN** Bert has no e-mail address and an event occurs for which he switched on both channels
- **THEN** he gets the Pushover notification and no mail

#### Scenario: No outgoing mail server
- **WHEN** no outgoing mail server is configured and an event occurs for Anna
- **THEN** no connection to a mail server is attempted, and her Pushover notification is sent

### Requirement: Choose notifications per event type and channel
Under „Mein Konto“, the card „Benachrichtigungen“ SHALL show one row per event type, grouped, and one switch per channel. A change MUST be saved at once and MUST apply to later events on every device. A user MUST only read and change their own switches. The switches of a channel that is not usable for the user MUST be disabled with a hint that says what is missing, and MUST keep their stored value.

#### Scenario: Switch on a channel
- **WHEN** Anna switches on „Pushover“ for „Anreisen morgen“
- **THEN** the switch is on after a reload and on her other devices

#### Scenario: Hint for a missing address
- **WHEN** Bert, who has no e-mail address, opens „Benachrichtigungen“
- **THEN** the switches in the column „E-Mail“ are disabled with the hint „Keine E-Mail-Adresse hinterlegt“

#### Scenario: Own switches only
- **WHEN** Anna changes a switch
- **THEN** Bert's switches stay unchanged, and Bert cannot read Anna's

#### Scenario: Unknown event type
- **WHEN** a client sets a switch for the event type `vacationrental.nothing`
- **THEN** the system responds with HTTP 400

### Requirement: Nothing is sent unasked
Every switch SHALL be off until the user switches it on, unless the event type declares a channel as mandatory. A mandatory switch MUST be shown on and disabled, and requests to switch it off MUST be rejected with HTTP 400. Installing the change MUST NOT send anything to existing users that they did not get before.

#### Scenario: New user
- **WHEN** a new user opens „Benachrichtigungen“
- **THEN** every switch is off except „E-Mail“ for „Neue Anmeldung“, which is on and disabled

#### Scenario: Existing user after the update
- **WHEN** a booking mail arrives for Anna, who never opened „Benachrichtigungen“
- **THEN** no notification is sent

### Requirement: Short and long form
Every notification SHALL have a title, a short text and a long text. „Pushover“ MUST send the title and the short text, „E-Mail“ MUST send the title as the subject and the long text. The short text MUST NOT contain names or contact data of guests or tenants, amounts or booking numbers.

#### Scenario: Same event, two channels
- **WHEN** a booking of "Haus Seeufer" awaits review and Anna has switched on both channels
- **THEN** the Pushover notification names the property and the period, and the mail additionally names the guest and the expected rent

### Requirement: Link into the app
A notification SHALL carry a link to the page of the record it is about, or to the list that shows the records. The link MUST use the public address of Logis. If no public address is configured, notifications MUST be sent without a link.

#### Scenario: Link to the booking
- **WHEN** Anna opens the link of a notification about a booking awaiting review and is signed in
- **THEN** the page of that booking opens

#### Scenario: No public address
- **WHEN** no public address is configured and an event occurs
- **THEN** the notification is sent without a link

### Requirement: Recipients
A notification MUST only be sent to active users who may see the record it is about at the time of sending. The role `ADMIN` alone MUST NOT make a user a recipient of notifications about domain data. Disabled users MUST NOT receive notifications.

#### Scenario: Admin without a role
- **WHEN** a booking of "Haus Seeufer" awaits review and the admin has no role on the property
- **THEN** the admin gets no notification, whatever their switches are

#### Scenario: Disabled user
- **WHEN** Carla is disabled and an event occurs for a unit she has a role on
- **THEN** Carla gets no notification

### Requirement: Sending never disturbs the event
Notifications SHALL be sent in the background, after the event is stored. The action that causes an event MUST NOT wait for a notification and MUST NOT fail because sending fails. If one channel or one recipient fails, the others MUST still be served. Failures MUST be logged with the event type, the channel and the user's ID, and without the text of the notification.

#### Scenario: Channel down
- **WHEN** a booking mail is imported while Pushover is unreachable
- **THEN** the booking is created as usual, and the mail notification is sent

#### Scenario: Event rolled back
- **WHEN** an action that would cause a notification fails and its changes are not stored
- **THEN** no notification is sent
