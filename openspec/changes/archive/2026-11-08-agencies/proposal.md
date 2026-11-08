# Proposal

## Why

A booking has two fields for the same thing: a fixed channel (Airbnb, Booking.com, direct, other) and a free-text agency („Vermittler“). The channel is never set by the mail import, its values do not match the agencies actually used (Fewo & Meer, Holidu, VVV Nordhorn, direct), and it cannot be extended. The agency is free text, so spelling varies, and the long names from mails such as „VVV-Stadt- und Citymarketing Nordhorn e.V.“ cannot be shortened without breaking the mail matching.

## What Changes

- New shared, managed list of agencies. Every signed-in user can create, rename, archive, merge and delete unused agencies. Every user sees the same list.
- Each agency has mail names. The mail import finds an agency by the name a mail gives. Renaming keeps the old name as a mail name, and merging moves all mail names, bookings and unit mappings to the remaining agency.
- A name that no agency knows yet creates a new agency automatically during the mail import.
- A booking references an agency from the list instead of free text. The booking dialog offers a selection with the option to create a new agency. Archived agencies are not offered for new choices but stay on existing bookings.
- **BREAKING**: The channel is removed from the booking dialog, the booking list and the API. The database column stays unused, so no data is lost.
- Unit mappings of the mail import reference the agency instead of its name. They stay per user.
- Database: the existing agency names become agencies with themselves as mail name. Bookings without agency but with a channel get the agency of that channel („Airbnb“, „Booking.com“, „Direkt“, „Sonstige“).

## Capabilities

### New Capabilities

- `agencies`: shared list of agencies with mail names: create, rename, archive, merge, delete, and how names from mails are resolved.

### Modified Capabilities

- `bookings`: a booking references an agency from the list and has no channel any more; the booking list filters and sorts by agency.
- `booking-mails`: agencies of recognised mails are resolved through the list, and unit mappings belong to an agency.

## Impact

- Backend, module `vacationrental`: new `Agency` entity, repository, service and controller; `Booking`, `BookingService`, `BookingController`, `BookingRepository`, `UnitMapping`, `UnitMappingRepository`, `ImportEngine`, `BookingMailHandler`; `Channel` is removed.
- Database: new tables `vacationrental.agencies` and `vacationrental.agency_aliases`, new columns `bookings.agency_id` and `unit_mappings.agency_id`, data migration from the text columns. This is a structural change of two existing tables with a complete migration path.
- Frontend, module `vacationrental`: new page „Vermittler“, agency selection in `BookingDialog`, booking list and filters, API types; channel removed.
- Rental statements keep their free-text provider. Linking them to agencies is out of scope.
- Order: `intermediary-booking-mails` (implemented, not committed) should be committed first, because this change replaces its hard-coded agency name handling for the matching. `owner-blocks` should follow this change and adds its migration after this one.
