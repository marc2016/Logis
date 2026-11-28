# bookings Specification

## Purpose

Bookings record who stays in a vacation rental unit and when, from the service provider's booking confirmation until the booking is settled on a statement or cancelled. They are the basis for the occupancy calendar and the income per unit.

## Requirements

### Requirement: Record a booking
A user with at least `EDITOR` as effective role on a unit SHALL be able to record a booking on it with arrival, departure, adults, and optionally children, external booking number, agency and expected rent. Only active units with usage `VACATION_RENTAL` MUST accept new bookings. Departure MUST be after arrival. A new booking SHALL have the status planned. A booking MUST NOT carry a channel.

#### Scenario: Booking from the provider's confirmation
- **WHEN** Anna records booking number "250905" on "Haus Waldeck" from 2026-11-06 to 2026-11-08 for 4 adults with an expected rent of 248.58
- **THEN** the booking appears as planned with 2 nights

#### Scenario: Unit not used for vacation rental
- **WHEN** Anna tries to record a booking on a unit with usage `LONG_TERM_RENTAL`
- **THEN** the system rejects this with the message „Die Einheit wird nicht als Ferienwohnung genutzt“

#### Scenario: Viewer may not record
- **WHEN** Dieter, as `VIEWER` of the property, tries to record a booking on one of its units
- **THEN** the system responds with HTTP 403

### Requirement: No overlapping bookings
Two bookings of the same unit that are not cancelled MUST NOT overlap. A stay SHALL occupy the nights from arrival up to the night before departure, so an arrival on another booking's departure day MUST be allowed.

#### Scenario: Overlap
- **WHEN** a booking exists from 2026-11-06 to 2026-11-08 and Anna records another one from 2026-11-07 to 2026-11-10 on the same unit
- **THEN** the system rejects this with the message „Die Einheit ist in diesem Zeitraum bereits belegt“

#### Scenario: Changeover day
- **WHEN** a booking ends on 2026-11-08 and Anna records one arriving on 2026-11-08
- **THEN** the booking is saved

### Requirement: Unique booking number per unit
An external booking number, if given, MUST be unique among all bookings of a unit with the same agency, including cancelled ones. Bookings without agency count as one agency. Agencies are compared by identity, not by name, so renaming an agency MUST NOT change which numbers collide.

#### Scenario: Same confirmation entered twice
- **WHEN** Anna records booking number "250905" a second time on the same unit
- **THEN** the system rejects this with the message „Diese Buchungsnummer ist bereits erfasst“

#### Scenario: Same number at two agencies
- **WHEN** a booking "1234" of Fewo & Meer exists and a booking "1234" of Holidu is recorded on the same unit
- **THEN** both bookings exist

### Requirement: Edit, cancel and delete a booking
A user with at least `EDITOR` on the unit SHALL be able to edit a booking, cancel it and delete it. A cancelled booking MUST keep its data and MUST NOT occupy the calendar. A cancellation SHALL be undoable only if the period is still free. A booking that is assigned to a statement MUST NOT be deleted.

#### Scenario: Cancel
- **WHEN** Anna cancels the booking from 2026-11-06 to 2026-11-08
- **THEN** the booking shows the status cancelled, and a new booking for that period can be recorded

#### Scenario: Delete a settled booking
- **WHEN** Anna tries to delete a booking that is assigned to a statement
- **THEN** the system rejects this with the message „Die Buchung ist abgerechnet und kann nur storniert werden“

### Requirement: Guest data
A booking SHALL hold the guest's name and optionally address, phone and email. Users with `VIEWER` as effective role on the unit MUST see the guest name but MUST NOT receive address, phone or email from the system. Users with at least `EDITOR` SHALL see all guest data.

#### Scenario: Viewer reads a booking
- **WHEN** Dieter, as `VIEWER` of the property, opens a booking of one of its units
- **THEN** they see period, guests, guest name and amounts, but no address, phone or email

### Requirement: Delete guest data
A user with at least `EDITOR` on the unit SHALL be able to delete the guest data of a booking. This MUST remove the guest name and all contact details permanently and MUST keep the booking with its period, guest counts, booking number and amounts. Guest data MUST NOT be deleted automatically.

#### Scenario: Delete guest data
- **WHEN** Anna deletes the guest data of a past booking
- **THEN** the booking stays in the calendar and on its statement, and shows „Gastdaten gelöscht“ instead of the guest

### Requirement: Booking documents
A user with at least `EDITOR` on the unit SHALL be able to attach documents to a booking, download them and delete them one by one. Only PDF, JPEG, PNG, WebP and HEIC files of at most 10 MB MUST be accepted. Documents MUST be visible only to users with at least `EDITOR`. Deleting the guest data MUST NOT delete documents. Deleting the booking MUST delete them.

#### Scenario: Attach the confirmation
- **WHEN** Anna attaches a screenshot of the provider's confirmation to booking "250905"
- **THEN** the booking lists the document with its name, and Anna can download it

#### Scenario: Wrong file type or too large
- **WHEN** Anna tries to attach a 12 MB PDF or a Word file
- **THEN** the system rejects this with the message „Nur PDF- oder Bilddateien bis 10 MB“

#### Scenario: Viewer and documents
- **WHEN** Dieter, as `VIEWER` of the property, opens a booking with a document
- **THEN** they see no documents, and downloading one by its address returns HTTP 404

### Requirement: Booking list
The system SHALL list the bookings visible to the user, filterable by property, unit, status, period (stays that overlap it) and a search text in booking number and guest name. The list SHALL be sortable by arrival, departure, unit, guest, nights, expected rent and status, and sorted by arrival descending by default. Users with a role below `EDITOR` MUST NOT find bookings by text that only appears in contact details.

#### Scenario: Upcoming stays of one unit
- **WHEN** Anna filters by "Haus Waldeck" and status planned and sorts by arrival ascending
- **THEN** they see the planned bookings of that unit, earliest first

#### Scenario: Search by booking number
- **WHEN** Anna searches for "250905"
- **THEN** the list contains exactly that booking

### Requirement: Access through the unit
Bookings MUST be visible exactly to users with an effective role on their unit. A booking of a unit without such a role MUST be treated as nonexistent (HTTP 404).

#### Scenario: Unit-only editor
- **WHEN** Carla, as `EDITOR` of "Whg. 2" only, lists bookings
- **THEN** they see the bookings of "Whg. 2" and none of "Whg. 1"

#### Scenario: No access
- **WHEN** Bert retrieves a booking of a unit they have no role on
- **THEN** the system responds with HTTP 404

### Requirement: Agency and pets
A booking SHALL optionally reference an agency from the shared list of agencies, and SHALL carry the number of pets. Both SHALL be shown in the booking form, on the booking page and in the dialog for a new booking. The form SHALL offer the active agencies for selection and SHALL allow creating a new agency from the form. A booking with an archived agency SHALL keep and show it. The agency SHALL be a filterable and sortable column „Vermittler“ of the booking list, and the list MUST NOT show a channel column.

#### Scenario: Agency from an imported mail
- **WHEN** a booking is created from a Holidu mail
- **THEN** its agency is "Holidu"

#### Scenario: Create an agency while recording a booking
- **WHEN** Anna types „Ferienbüro Nord“ into the agency field of the booking dialog, chooses to create it and saves the booking
- **THEN** the agency „Ferienbüro Nord“ exists in the shared list, and the booking references it

#### Scenario: Existing data after the update
- **WHEN** the system is updated with a booking that had the free-text agency „Fewo & Meer“ and one that had no agency but the channel `DIRECT`
- **THEN** the first references the agency „Fewo & Meer“ and the second the agency „Direkt“

### Requirement: Bookings awaiting review
A booking created from a mail SHALL have the review state „zu prüfen“ until a user with at least `EDITOR` confirms or discards it. It MUST occupy the calendar like a planned booking and MUST be marked in the booking list, the calendar and the header of the booking page, which SHALL offer confirming and discarding. Confirming SHALL make it an ordinary booking, possibly after editing. Discarding SHALL delete it together with its documents, mark the mail in the inbox as discarded and return to the previous view.

#### Scenario: Confirm an imported booking
- **WHEN** Anna opens the booking 250903 „zu prüfen“, corrects the guest name and confirms
- **THEN** the booking is an ordinary planned booking without the marker

#### Scenario: Filter bookings to review
- **WHEN** Anna filters the booking list by „zu prüfen“
- **THEN** they see only bookings and changes awaiting review

### Requirement: Proposed changes
A booking SHALL carry at most one proposed change from a mail: new values for period, guests, pets, expected rent and guest data, or a cancellation. The booking page MUST show old and new values, and clicking a new value SHALL show the proposing mail with the value highlighted. Applying SHALL take over the values or cancel the booking, subject to the usual rules such as the overlap check. Discarding SHALL keep the booking unchanged. A newer proposal MUST replace an older one.

#### Scenario: Apply a cancellation
- **WHEN** Anna applies the proposed cancellation of booking 250905
- **THEN** the booking is cancelled and the calendar period is free

#### Scenario: Change would overlap
- **WHEN** a proposed change moves a booking onto an occupied period and Anna applies it
- **THEN** the system rejects this with the message „Die Einheit ist in diesem Zeitraum bereits belegt“ and the proposal stays

#### Scenario: Where the new departure comes from
- **WHEN** Anna clicks the proposed new departure of booking 250905
- **THEN** the source panel shows the change mail with the new departure highlighted

### Requirement: Booking page
Every booking SHALL open as a page of its own with an address containing the booking's id. The booking list, the calendar and the inbox SHALL open bookings there, and the browser's back button SHALL return to the previous view with its filters or year. Recording a new booking SHALL stay a dialog.

The page SHALL have a header with the most important details: guest name, period with the number of nights, unit, agency, expected rent, status and, if present, the marker „zu prüfen“ or the proposed change. The fields SHALL be editable on the page by users with at least `EDITOR`.

For users with at least `EDITOR`, the page SHALL show the booking's sources next to the data, one tab per source. Focusing or clicking a field with a location SHALL show its source and highlight the location. Clicking a highlighted location SHALL focus its field. On narrow screens the sources SHALL be a separate tab of the page. A booking that does not exist or is not visible MUST show „Buchung nicht gefunden“.

#### Scenario: Open from the list
- **WHEN** Anna clicks booking 250903 in the booking list filtered by „Haus Waldeck“
- **THEN** the booking page opens at its own address, and the back button returns to the list filtered by „Haus Waldeck“

#### Scenario: Check the arrival
- **WHEN** Anna clicks the arrival field of the imported booking 250903
- **THEN** the source panel shows the confirmation mail with „23.12.2026“ highlighted next to „Anreise“

#### Scenario: Shared address
- **WHEN** Anna opens the address of booking 250903 in a new browser tab
- **THEN** the same booking page appears

#### Scenario: Unknown booking
- **WHEN** Carla opens the address of a booking of a unit she has no role on
- **THEN** the page shows „Buchung nicht gefunden“

### Requirement: Bulk actions on bookings
The booking list SHALL offer, for selected bookings, „Bestätigen“ for bookings „zu prüfen“, „Stornieren“, „Löschen“ and „Abrechnung zuordnen“. Assigning SHALL let the user choose a statement and enter a commission rate in percent and a cleaning amount. Each booking SHALL be assigned with its expected rent as gross, the commission rounded to cents, and the cleaning amount. Bookings of another unit than the statement's („Die Buchung gehört zu einer anderen Einheit“), bookings already on a statement („Die Buchung ist bereits abgerechnet“) and bookings without expected rent („Bitte einen Bruttobetrag angeben.“) MUST fail with that message. Confirming SHALL apply to new imported bookings only: a booking with a proposed change MUST fail with „Änderungsvorschläge bitte einzeln prüfen“, so old and new values are compared one by one, and any other booking with „Die Buchung ist nicht „zu prüfen““. Every action MUST apply the rules of the single action.

#### Scenario: Confirm imported bookings
- **WHEN** Anna filters by „zu prüfen“, selects all 6 bookings and chooses „Bestätigen“
- **THEN** all 6 become ordinary bookings and disappear from the filtered list

#### Scenario: Assign a month to a statement
- **WHEN** Anna selects the 7 Fewo & Meer bookings of „Haus Waldeck“ in October 2026 and assigns them to the October statement with 15 % commission and 60.00 cleaning
- **THEN** the statement has 7 lines, each with the expected rent as gross, 15 % of it as commission and 60.00 cleaning, and the bookings show the status settled

#### Scenario: Booking of another unit
- **WHEN** Anna assigns a booking of „Whg. 1“ together with others to a statement of „Haus Waldeck“
- **THEN** that booking is not assigned and the report names „Die Buchung gehört zu einer anderen Einheit“

### Requirement: Owner use
A booking SHALL be either a guest booking or an owner use („Eigenbelegung“), and existing bookings SHALL be guest bookings. An owner use SHALL have a unit, arrival and departure, and optionally an external booking number and agency; it MUST NOT carry adults, children, pets, guest data or an expected rent, and it MUST NEVER be „zu prüfen“. The same rules as for guest bookings SHALL apply to who may record, edit, cancel and delete it, to overlaps and to unique booking numbers. When recording a booking, the user SHALL choose „Gastbuchung“ or „Eigenbelegung“, with „Gastbuchung“ preselected. The booking list SHALL show the kind and SHALL be filterable by it, and the booking page of an owner use MUST NOT show guest, guest-count or rent fields.

#### Scenario: Record an owner use by hand
- **WHEN** Anna records an „Eigenbelegung“ on "Haus Waldeck" from 2027-06-09 to 2027-06-14
- **THEN** the owner use is saved with 5 nights and without guests or rent

#### Scenario: Owner use blocks the period
- **WHEN** an owner use runs from 2027-06-09 to 2027-06-14 and Anna records a guest booking from 2027-06-12 to 2027-06-15 on the same unit
- **THEN** the system rejects this with the message „Die Einheit ist in diesem Zeitraum bereits belegt“

#### Scenario: Filter by kind
- **WHEN** Anna filters the booking list by „Eigenbelegung“
- **THEN** only owner uses are listed

#### Scenario: Existing bookings
- **WHEN** the change is installed on a system with recorded bookings
- **THEN** all of them are guest bookings and otherwise unchanged

### Requirement: Review mode
The booking list SHALL offer „Prüfen (n)“ while n bookings „zu prüfen“ match its filters. It SHALL open the first of them in the review mode.

In the review mode, the booking page SHALL:
- show the position in the queue as „x von n“;
- offer „Zurück“ and „Weiter“, and the arrow keys ← and → while no input field has the focus.

The queue SHALL hold the bookings „zu prüfen“ that match the list's filters, sorted by arrival, soonest first.

After confirming, applying or discarding, the next booking of the queue SHALL open. When none is left, the page SHALL show „Alles geprüft.“ with a link back to the list.

Leaving a booking with unsaved edits MUST ask „Ungespeicherte Änderungen verwerfen?“ first. Decisions MUST NOT be bound to a key.

#### Scenario: Click through
- **WHEN** Anna has 3 bookings to review and chooses „Prüfen (3)“
- **THEN** the booking with the soonest arrival opens with „1 von 3“, and „Weiter“ opens the second one with „2 von 3“

#### Scenario: Confirm and go on
- **WHEN** Anna confirms the first booking of 3
- **THEN** the next booking opens with „1 von 2“

#### Scenario: Proposed change in the queue
- **WHEN** the queue reaches a booking with a proposed change
- **THEN** old and new values are shown, and only „Übernehmen“ or „Verwerfen“ moves on to the next booking

#### Scenario: Filtered queue
- **WHEN** the list is filtered to „Haus Düne“ and Anna chooses „Prüfen“
- **THEN** the queue holds only the bookings „zu prüfen“ of „Haus Düne“

#### Scenario: All done
- **WHEN** Anna decides on the last booking of the queue
- **THEN** the page shows „Alles geprüft.“ and a link back to the list

#### Scenario: Unsaved edits
- **WHEN** Anna changes the guest name without saving and chooses „Weiter“
- **THEN** she is asked „Ungespeicherte Änderungen verwerfen?“, and „Abbrechen“ keeps her on the booking with her edits

#### Scenario: Arrow keys while typing
- **WHEN** the cursor is in a field of the booking and Anna presses →
- **THEN** the cursor moves within the field and the booking stays open

### Requirement: Compact view
The booking page SHALL offer the views „Kompakt“ and „Alle Angaben“ for users who may edit the booking.
- „Kompakt“ SHALL show only arrival, departure, adults, children and the expected rent; for an owner use only arrival and departure.
- A field hidden in „Kompakt“ MUST still be shown while its value differs from its document or it has an error.
- The default SHALL be „Alle Angaben“.

The chosen view SHALL be remembered as a setting of the user and SHALL apply to every booking, also in the review mode. The dialog for recording a new booking MUST always show all fields.

#### Scenario: Switch to compact
- **WHEN** Anna opens a booking and chooses „Kompakt“
- **THEN** only Anreise, Abreise, Erwachsene, Kinder and Mietpreis are shown with „Speichern“, and the header still shows guest, unit, agency and booking number

#### Scenario: Remembered
- **WHEN** Anna chose „Kompakt“ and opens another booking
- **THEN** it opens in „Kompakt“

#### Scenario: Differing field stays visible
- **WHEN** a booking opens in „Kompakt“ and its guest name differs from its document
- **THEN** „Name“ is shown with the hint that it differs from the document

#### Scenario: Owner use
- **WHEN** Anna opens an owner use in „Kompakt“
- **THEN** only Anreise and Abreise are shown

#### Scenario: New booking
- **WHEN** Anna records a new booking while her view is „Kompakt“
- **THEN** the dialog shows all fields

### Requirement: Cancellation reported by mail
A booking whose proposed change is a cancellation SHALL carry the marker „Stornierung gemeldet“ in the booking list, the calendar and the header of the booking page, next to „zu prüfen“. This MUST also hold while the booking itself is not confirmed yet. The marker MUST disappear when the cancellation is applied or discarded. Bookings that already carried a proposed cancellation before this change MUST show the marker too.

#### Scenario: Marker in the list
- **WHEN** booking 250905 was created from a „Last-Minute Buchung“ mail, is not confirmed yet, and the mail „Stornierung 250905“ has been fetched
- **THEN** the booking list shows booking 250905 with „zu prüfen“ and „Stornierung gemeldet“

#### Scenario: Marker in the calendar
- **WHEN** Anna opens the calendar of the unit of booking 250905 with the reported cancellation
- **THEN** the booking is marked as „Stornierung gemeldet“ and still occupies its period

#### Scenario: Confirmed booking
- **WHEN** the confirmed booking 250903 gets a cancellation mail
- **THEN** the list, the calendar and the header of its page show „Stornierung gemeldet“

#### Scenario: Cancellation applied
- **WHEN** Anna applies the reported cancellation of booking 250905
- **THEN** the booking shows the status „Storniert“ and neither „zu prüfen“ nor „Stornierung gemeldet“

### Requirement: Review a reported cancellation of an unconfirmed booking
For a booking that is not confirmed yet and has a reported cancellation, the booking page SHALL show „Stornierung per E-Mail – bitte prüfen“ with the cancellation mail among its sources. It SHALL offer „Stornierung übernehmen“, „Stornierung verwerfen“ and „Buchung verwerfen“. Applying SHALL cancel the booking and end its review. Discarding the cancellation SHALL leave the booking „zu prüfen“.

#### Scenario: Notice on the booking page
- **WHEN** Anna opens booking 250905, which is not confirmed yet and has a reported cancellation
- **THEN** the page shows „Stornierung per E-Mail – bitte prüfen“ with the text „Der Vermittler meldet, dass diese Buchung storniert wurde. Die Buchung ist noch nicht bestätigt.“, and the sources include the cancellation mail

#### Scenario: Apply
- **WHEN** Anna chooses „Stornierung übernehmen“ on booking 250905
- **THEN** the booking is cancelled, it is no longer „zu prüfen“, its data and mails are kept, and the calendar period is free

#### Scenario: Discard the cancellation
- **WHEN** Anna chooses „Stornierung verwerfen“ on booking 250905
- **THEN** the booking is not cancelled, stays „zu prüfen“, and the page offers „Bestätigen“ and „Verwerfen“ as for any imported booking

#### Scenario: Discard the booking
- **WHEN** Anna chooses „Buchung verwerfen“ on booking 250905
- **THEN** the booking is deleted with its documents, as when discarding any imported booking

#### Scenario: Review mode
- **WHEN** the review queue reaches booking 250905 and Anna chooses „Stornierung übernehmen“
- **THEN** the next booking of the queue opens

#### Scenario: Review mode after discarding the cancellation
- **WHEN** the review queue shows booking 250905 and Anna chooses „Stornierung verwerfen“
- **THEN** the same booking stays open and offers „Bestätigen“ and „Verwerfen“

### Requirement: Bulk confirm skips reported cancellations
The bulk action „Bestätigen“ MUST NOT confirm a booking with a reported cancellation, also when the booking is not confirmed yet. Such a booking MUST fail with „Änderungsvorschläge bitte einzeln prüfen“ and MUST stay unchanged.

#### Scenario: Selection with a reported cancellation
- **WHEN** Anna selects two new imported bookings, one of them with a reported cancellation, and chooses „Bestätigen“
- **THEN** the one without is confirmed, and the other fails with „Änderungsvorschläge bitte einzeln prüfen“ and keeps both markers

### Requirement: Notify about bookings awaiting review
When a mail fetched by the regular poll creates a booking „zu prüfen“, the system SHALL raise the notification „Neue Buchung zu prüfen“; when it proposes a change or a cancellation, the notification „Änderung oder Stornierung zu prüfen“. Recipients MUST be the users with at least `EDITOR` on the booking's unit. Both are event types of the group „Ferienvermietung“. Mails fetched as older mails, mails processed again and mails received more than two days before MUST NOT raise them.

#### Scenario: New booking
- **WHEN** the poll imports a confirmation for "Haus Waldeck" from 2027-07-12 to 2027-07-19 and Anna, `OWNER` of the property, switched on Pushover for „Neue Buchung zu prüfen“
- **THEN** Anna gets „Neue Buchung zu prüfen“ naming "Haus Waldeck", the period and the agency, with a link to the booking

#### Scenario: Proposed cancellation
- **WHEN** the poll imports a cancellation for a known booking and Anna switched on „Änderung oder Stornierung zu prüfen“
- **THEN** Anna gets a notification that says a cancellation is proposed

#### Scenario: Viewer
- **WHEN** Bert is `VIEWER` of "Haus Waldeck" and a booking for it awaits review
- **THEN** Bert gets no notification, because he cannot confirm it

#### Scenario: Older mails
- **WHEN** Anna fetches older mails from 2025-01-01 and they create 40 bookings „zu prüfen“
- **THEN** no notification is sent

#### Scenario: Mail text
- **WHEN** Anna switched on „E-Mail“ for „Neue Buchung zu prüfen“ and a booking is imported
- **THEN** the mail names the property, the period, the agency, the guest, the number of guests and the expected rent

### Requirement: Arrival and departure digest
Every day at 18:00 (Europe/Berlin), the system SHALL raise the notifications „Anreisen morgen“ and „Abreisen morgen“ in the group „Ferienvermietung“, each as one notification per user listing the guest bookings that arrive or depart on the following day on units the user has a role on. Cancelled bookings, bookings „zu prüfen“ and owner uses MUST be left out. A user without such a booking MUST NOT get the notification.

#### Scenario: Two arrivals
- **WHEN** bookings on "Whg. 1" and "Whg. 2" arrive tomorrow and Anna, with a role on both, switched on Pushover for „Anreisen morgen“
- **THEN** at 18:00 Anna gets one notification „Anreisen morgen“ listing both units

#### Scenario: Nothing tomorrow
- **WHEN** no booking of Anna's units arrives tomorrow
- **THEN** Anna gets no „Anreisen morgen“

#### Scenario: Unconfirmed and cancelled bookings
- **WHEN** tomorrow's only arrivals are a booking „zu prüfen“ and a cancelled booking
- **THEN** no digest is sent

#### Scenario: Only own units
- **WHEN** Carla has a role on "Whg. 2" only, and bookings on "Whg. 1" and "Whg. 2" depart tomorrow
- **THEN** Carla's „Abreisen morgen“ lists only "Whg. 2"

#### Scenario: Guest names only by mail
- **WHEN** Anna switched on both channels for „Anreisen morgen“
- **THEN** the mail lists unit, guest and number of guests per arrival, and the Pushover notification lists only the units

### Requirement: Digest once per day
Each digest MUST be sent at most once per day. If Logis was not running at 18:00, the digests SHALL be sent when Logis runs again on the same day; a missed day MUST NOT be made up for on a later day.

#### Scenario: Restart in the evening
- **WHEN** Logis is restarted at 18:30 after the digests were sent at 18:00
- **THEN** no digest is sent a second time

#### Scenario: Down at 18:00
- **WHEN** Logis was stopped at 17:50 and is started again at 19:10 on the same day
- **THEN** the digests for the following day are sent after the start

#### Scenario: Down over midnight
- **WHEN** Logis was stopped at 17:50 and is started again the next morning
- **THEN** the missed digests are not sent
