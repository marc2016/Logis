## Purpose

Booking sources keep the documents a booking's data came from in a readable form, and record for each field where its value was found, so that a user can check every imported value against its source at a glance.

## ADDED Requirements

### Requirement: Sources of a booking
When a mail creates, completes, changes, cancels or is attached to a booking, the system SHALL store the mail as a source of that booking. A source SHALL hold its kind, a label (agency and mail kind, such as „Fewo & Meer Buchungsbestätigung“), the date of the mail, its subject and the text the recognition read, exactly as it was read. A booking's sources SHALL be listed oldest first. A source MUST belong to the mail document attached to the booking: deleting that document or the booking MUST delete the source. The kind of a source SHALL allow other kinds than mails, such as PDF documents, without changing existing sources.

#### Scenario: Imported confirmation
- **WHEN** the Fewo & Meer confirmation of booking 250903 creates the booking
- **THEN** the booking has one source „Fewo & Meer Buchungsbestätigung“ with the mail's date, subject and text

#### Scenario: Later change mail
- **WHEN** a change mail for booking 250903 arrives and proposes a new departure
- **THEN** the booking has two sources, the confirmation first

#### Scenario: Mail document deleted
- **WHEN** Anna deletes the `.eml` document of the change mail from booking 250903
- **THEN** the change mail is no longer a source of the booking, and the confirmation still is

### Requirement: Field locations
For each booking field whose value was read from a mail, the system SHALL store the source and the location of the value in it: in the subject or in the text, with start and end position, and the value as written in the mail. This SHALL cover booking number, property name, arrival, departure, adults, children, pets, expected rent and the guest's name, street, postal code, city, country, phone and email. A field SHALL have at most one current location, pointing to the mail its current value came from. A mail whose value matches the current value SHALL add a location for a field that has none and MUST NOT replace an existing one. A value without a location MUST be shown without a highlight rather than with a guessed one.

#### Scenario: Locations of a confirmation
- **WHEN** the Fewo & Meer confirmation of booking 250903 creates the booking
- **THEN** arrival, departure, adults, children, expected rent and the guest fields each point to the place in the mail text where they were read, and the booking number and property name point to the subject

#### Scenario: Completed by a second mail
- **WHEN** a booking „zu prüfen“ without phone number is completed by a second mail that carries the phone number
- **THEN** the phone points to the second mail, and the other fields still point to the first

### Requirement: Locations of proposed changes
A proposed change from a mail SHALL carry the locations of its new values in that mail. Applying the proposal SHALL make these the current locations of the changed fields. Discarding the proposal, or a newer proposal replacing it, MUST drop them and keep the current locations.

#### Scenario: Apply a change
- **WHEN** Anna applies the proposed new departure of booking 250905
- **THEN** the departure points to the change mail, and the arrival still points to the confirmation

#### Scenario: Discard a change
- **WHEN** Anna discards the proposed change of booking 250905
- **THEN** all fields keep pointing to the confirmation

### Requirement: Values edited by hand
A field whose current value no longer matches the value at its location SHALL keep its location and SHALL be marked as differing from its source, with the source's value shown. Editing the value back to the source's value SHALL remove the mark. Recording or editing a booking by hand MUST NOT create locations.

#### Scenario: Corrected guest name
- **WHEN** Anna changes the imported guest name „Erika Musterfrau“ to „Erika Muster-Frau“ and saves
- **THEN** the name field is marked „weicht vom Dokument ab“ and shows „Erika Musterfrau“ as the value in the mail

### Requirement: Privacy of sources
Sources and field locations MUST be visible only to users with at least `EDITOR` on the booking's unit, like booking documents. For other users the booking MUST appear without sources and locations, and requesting a source by its address MUST return HTTP 404. Deleting the guest data of a booking MUST delete the locations of the guest fields together with their mail values, and MUST keep the sources, like the documents they belong to.

#### Scenario: Viewer opens an imported booking
- **WHEN** Dieter, as `VIEWER` of the property, opens booking 250903
- **THEN** they see no source and no highlights, and requesting the source's text returns HTTP 404

#### Scenario: Delete guest data
- **WHEN** Anna deletes the guest data of booking 250903
- **THEN** the guest fields have no locations any more, and arrival and departure still point to the mail

### Requirement: Sources of existing bookings
After the update, the system SHALL derive sources and field locations once for existing bookings from the mail documents attached to them, reading them with the fixed rules only and in the order they were attached. A field's location SHALL come from the latest of these mails that carries the field. As on import, block notices and cancellations MUST only add a source and MUST NOT set locations. Fields whose current value differs from that mail SHALL be marked as differing. The derivation MUST NOT change any booking data, MUST NOT send mails to the AI, MUST skip mails no rule recognises, and MUST NOT create a second source for a mail document that already has one.

#### Scenario: Booking imported before the update
- **WHEN** the system is updated and booking 250902 has its confirmation `.eml` attached
- **THEN** the booking shows the confirmation as its source with highlighted fields, and its data is unchanged

#### Scenario: Block notice of an existing booking
- **WHEN** the system is updated and booking 3e98f765kl has its confirmation and a block notice with the number 250904 attached
- **THEN** both mails are sources, and the booking number points to the confirmation and does not differ

#### Scenario: Restart
- **WHEN** the system restarts after the derivation has run
- **THEN** no booking gets a second source for the same mail
