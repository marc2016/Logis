# Ideas

Loose ideas for later. Each one becomes an OpenSpec change once it is picked up.

## Bookings and similar records as pages

- Open bookings (and similar records such as statements) as their own page with an address, instead of a modal dialog.
- Split view on that page: the booking's data on the left, its source on the right, such as the booking mail or a PDF document.

## Charts in the evaluation

- Charts on the „Auswertung“ page, which today shows only a table per month: for example occupancy and nights per month, and settled net next to planned rent. shadcn offers chart components based on Recharts.

## Notes and documents on a property

- Notes on a property.
- Upload general documents to a property that belong to no booking or cost, for example for an owners' meeting. Documents already exist for bookings and property costs (`core.documents` with an owner type), so a property owner type could reuse them.
