# Proposal

## Why

Properties and units now exist, but the holiday homes are still a blank spot: there is no record of who stays when, and no record of what each stay earns. The rental is handled by a service provider. The provider sends a booking confirmation for every new stay (booking number, period, guests, rent, guest contact), and later a statement per unit that lists the settled bookings with their deductions and the payout. This change turns the placeholder module `vacationrental` into the place where both are recorded. It follows the roadmap step after `realestate-minimal`.

Guiding decisions from exploration:

- **Purpose:** occupancy and income per unit. Taxes such as the tourist tax, VAT and the registration form are out of scope.
- **Two phases per booking:** A booking is entered ahead of time from the provider's confirmation (planned). It becomes settled once it is assigned to the provider's statement.
- **The statement is the money frame:** One statement belongs to one unit and one provider. Each booking line carries gross, commission and cleaning, and the payout is the sum of the net amounts. One statement corresponds to one payment on the account, so matching against transactions can come with `core-bank-import`. This change stores the amounts only and does not link transactions.
- **Cancellations** keep the booking with the status cancelled. A cancelled booking frees the calendar and can still appear on a statement with a cancellation payout.
- **Guest data includes contact details** (address, phone, email). Only users with at least `EDITOR` on the unit see them, and they are deleted by hand. There is no automatic retention period.
- **Rights come from the unit:** bookings and statements use the effective role on the unit from `core.realestate`, with no shares of their own.
- **The property is the financial overview:** Income from bookings, deductions such as commission and cleaning, and other costs such as repairs all appear on the property, together with the result. There are no bank accounts in Logis yet, so costs are recorded directly on the property or unit, without an account. Once bank accounts exist, each cost and each statement can be linked to its bank transaction (later change), so nothing is counted twice.

## What Changes

- **Bookings** on active units with usage `VACATION_RENTAL`:
  - Fields: arrival, departure, external booking number (unique per unit), optional channel, adults and children, optional expected rent, and the guest (name, address, phone, email).
  - Active bookings of the same unit must not overlap. Arrival on another booking's departure day is allowed.
  - Bookings can be cancelled and deleted. The guest data of a booking can be deleted on its own.
  - Documents (PDF or image, e.g. the provider's confirmation) can be attached to a booking, downloaded and deleted.
  - A booking list with filters and sortable columns.
- **Statements** per unit:
  - Fields: provider name (free text, with suggestions from earlier statements of the unit), period and statement date.
  - Bookings of the unit are assigned to a statement with gross, commission and cleaning. The payout is computed.
  - Removing a booking from a statement makes it planned again.
- **Occupancy:**
  - A monthly calendar per property with one row per vacation rental unit.
  - A yearly overview per unit with booked nights, occupancy rate, settled net and planned rent per month. Nights of stays across a month boundary are split by night. Money counts on the statement date, the same as in the property finances.
- **Property finances (core, all properties):**
  - Costs are recorded on a property or one of its units: date, amount, category, counterparty, note and receipts (documents).
  - The property page gets a „Finanzen“ section: a yearly table per month with income, expenses and result, and a sortable list of all items, with „+ Kosten“ at the top right.
  - Modules contribute items. `vacationrental` contributes per settled statement line: the gross as income, and commission and cleaning as expenses.
- **Documents (core):** One shared file storage for receipts on costs and documents on bookings.
- **Real estate core:**
  - Modules can report that they still reference a unit. A property is then not deletable, as is already the case with attributed transactions.
  - `UnitAccess` returns display data for units (name, property, usage, archived).
- **Interface:** The module group „Ferienvermietung“ gets „Buchungen“ (grid with filters and sorting), „Kalender“ (month calendar), „Abrechnungen“ and „Auswertung“, replacing the placeholder page. „Buchungen“ and „Kalender“ both have a button at the top right that opens the same booking dialog, including document upload.

## Capabilities

### New Capabilities

- `bookings`: Bookings on vacation rental units, with planned/settled/cancelled status, the overlap rule, the external booking number, and guest data with restricted visibility and manual deletion.
- `rental-statements`: Statements of a rental service provider per unit: booking lines with gross, commission, cleaning and net, the computed payout, and the effect on booking status.
- `occupancy`: The occupancy calendar per property and the yearly overview per unit (nights, occupancy rate, settled and planned net income per month).
- `property-finances`: Costs recorded on properties and units with receipts, the items modules contribute, and the yearly income/expense/result overview on the property.

### Modified Capabilities

- `properties`: A property is also not deletable while costs or module data such as bookings still refer to it or its units.

## Impact

- **Backend:**
  - Module `app.logis.modules.vacationrental` with bookings, statements, the calendar and overview queries, and controllers under `/api/vacationrental/…`.
  - `core.realestate` gets costs, the finance overview, and public interfaces for unit references and finance contributions, plus unit summaries in `UnitAccess`.
  - A new core package `core.documents` provides file storage for any owner type.
  - Flyway migrations in the schemas `core` and `vacationrental`.
- **Frontend:** `src/modules/vacationrental/` gets pages, dialogs and query hooks. The placeholder `BelegungPage` is replaced. `PropertyDetailPage` gets the „Finanzen“ section and the cost dialog.
- **Privacy:** This is the first place where Logis stores personal data of third parties (guests). Visibility is restricted and deletion is manual. Booking documents usually contain guest data too, and they deliberately stay when guest data is deleted. They have to be deleted one by one.
- **Files:** This is the first file upload in Logis. Files are stored in the database, so the existing backup covers them.
- **Assumptions** (proposed during exploration, not explicitly confirmed; easy to change):
  - Only active units with usage `VACATION_RENTAL` accept new bookings. Existing bookings stay when a unit is archived or its usage changes.
  - The channel is an optional value from a fixed list: Airbnb, Booking.com, Direkt, Sonstige.
  - Net per booking = gross − commission − cleaning. Other deductions on a statement are out of scope for now.
  - Deleting guest data removes the name and all contact details and keeps the booking with its period, numbers and amounts.
  - The booking number is optional, because direct bookings may not have one.
  - Children are recorded separately from adults. Infants get no special treatment.
  - Documents: PDF, JPEG, PNG, WebP and HEIC, at most 10 MB each, any number per booking. They are visible only to users with at least `EDITOR`, like the contact details.
  - The booking list is sorted by arrival, newest first, by default.
  - Property finances count settled money only: statement lines on the statement date, costs on their date. Planned bookings are not included.
  - Costs are entered as positive amounts and need at least `EDITOR` on their target. Costs on the property itself are visible only to users with a role on the property.
  - Transactions attributed to a property (from `realestate-minimal`) stay listed separately and do not count in the result until the later linking change.
- **Decision on accounts:** An account stays a bank account of a legal entity, and nothing ties it to a property. Costs do not need an account. They are linked to bank transactions later.
