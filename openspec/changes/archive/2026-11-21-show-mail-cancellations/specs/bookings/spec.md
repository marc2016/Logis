# Spec Delta

## ADDED Requirements

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
