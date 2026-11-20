# Tasks

## 1. Date field

- [ ] 1.1 Add `required` to `DateField` and set the custom validity for an invalid or too late date (design D1). Verify with `DateField` tests: an empty required field and an invalid date block a form submit; a valid date submits.

## 2. Forms

- [ ] 2.1 Switch „Anreise“ and „Abreise“ in `BookingForm` to `DateField` with `required`, keeping ids and the `Field` wrapper (design D2). Verify:
  - `BookingPage`, `BookingReview` and `VacationRental` tests pass with German dates;
  - the source passage still shows on focus.
- [ ] 2.2 Switch `StatementDialog` (3 fields), `CostDialog`, `TransactionDialog`, `TransferDialog` and the mailbox's „Stichtag“ to `DateField` with `required`. Verify:
  - the existing tests of these dialogs pass;
  - a new test shows that an invalid „Abrechnungsdatum“ does not save;
  - `grep 'type="date"'` in `frontend/src` finds nothing.

## 3. Verification

- [ ] 3.1 Add `germanDate` to `e2e/support/data.ts` and use it in `booking-calendar`, `owner-use` and `sharing`. Add the booking dialog to `layout.spec.ts`. Verify: these specs and `finances`, `mail-import`, `inbox-fetch` are green.
- [ ] 3.2 Run `cd frontend && npm test && npm run lint && npm run build`; all must be green.

## Workflow follow-up

- Archive with `/opsx:archive` and sync the `app-shell` spec.
