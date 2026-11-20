## ADDED Requirements

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
