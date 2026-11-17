# Tasks

## 1. Helpers

- [x] 1.1 Add `owner`, `account`, `share` and `statement` to `e2e/support/data.ts`, `adminPage` and `secondUser` to `e2e/support/fixtures.ts`, and an optional message id and departure to `fewoConfirmation` (design D2). Verify with `npx tsc --noEmit` in `e2e`.

## 2. Sign-in, account and admin

- [x] 2.1 `sign-in.spec.ts`: „Zurück zur Anmeldung“ from the code step leads to the empty password step, also after a reload; five wrong passwords lock the user, and the right one is then rejected with „Zu viele Fehlversuche, bitte später erneut versuchen“ (design D3). Verify that it type-checks.
- [x] 2.2 `account.spec.ts`: change the password and sign in with the new one; generate new recovery codes and sign in with one of them. Verify that it type-checks.
- [x] 2.3 `admin.spec.ts`:
  - user lifecycle: create a user, sign in as that user (2FA setup, own password), disable it and check that sign-in fails, enable it again, reset its 2FA and check that setup is asked again;
  - categories: create a category and archive it, and check that it is no longer offered for a transaction.

  Verify that it type-checks.

## 3. Finances, properties and sharing

- [x] 3.1 `finances.spec.ts`:
  - owner and two accounts, an income, an expense and a transfer of 300,00 €, with the balances checked;
  - filter by date and text, then assign a category and a property to several transactions with the multi-select.

  Verify that it type-checks.
- [x] 3.2 `properties.spec.ts`: a property with two units, a cost on one unit, and the property's finances showing it. Verify that it type-checks.
- [x] 3.3 `sharing.spec.ts`:
  - a property shared with a second user as viewer: that user sees the booking with the guest name, without contact data, and without edit buttons;
  - a unit shared with a third user as editor: that user sees only that unit and can record a booking there.

  Verify that it type-checks.

## 4. Vacation rental

- [x] 4.1 `booking-proposal.spec.ts`: a confirmation mail, then a change mail with a new departure. The booking page shows the old and the new value. Clicking the new value highlights it in the change mail, and applying it changes the departure. Verify that it type-checks.
- [x] 4.2 `agencies.spec.ts`: rename an agency (the old name stays as a mail name) and merge a second agency into it (its bookings move). Verify that it type-checks.
- [x] 4.3 `evaluation.spec.ts`: two stays and a statement in one month of 2030, and the Auswertung row of that month showing nights, occupancy, settled net and planned rent. Verify that it type-checks.
- [x] 4.4 `inbox-bulk.spec.ts`: two confirmation mails for an unknown property, both selected in the inbox and assigned to a unit in one go, both showing „Buchung angelegt“. Verify that it type-checks.

## 5. Coverage and rules

- [x] 5.1 Write `e2e/COVERAGE.md` with one row per capability from `openspec/specs/` (design D5), and add the e2e rule to `CLAUDE.md`. Verify that every capability directory has a row.

## 6. Verification

- [ ] 6.1 Run `npx tsc --noEmit` in `e2e`, push the branch, and verify that the e2e job passes in CI on desktop and phone. The suite cannot run locally next to the real stack.
