## Purpose

Rent due and paid per tenancy of the long-term rental: the amount due per month from the rent history, payments recorded with their transactions, their allocation to months, and arrears.

## ADDED Requirements

### Requirement: Rent due per month
The rent due of a tenancy for a month SHALL be the total of the rent entry valid on the first day of that month (cold rent, advance payment for utilities and further items), or of the entry valid on the start date in the month the tenancy starts. In the first and the last month of a tenancy it SHALL be prorated by the tenancy's days in that month, rounded to cents. Rent SHALL be due for every month from the start up to the current month and MUST NOT be due after the end.

#### Scenario: Full months
- **WHEN** a tenancy started on 1 March 2027 with a total rent of 800,00 and it is 10 May 2027
- **THEN** March, April and May are due with 800,00 each

#### Scenario: Start in the middle of the month
- **WHEN** the tenancy starts on 16 April 2027 with a total rent of 900,00
- **THEN** April is due with 450,00

#### Scenario: Rent changes
- **WHEN** a new rent entry of 850,00 is valid from 1 June 2027
- **THEN** May is due with 800,00 and June with 850,00

### Requirement: Record a payment
A user with at least `EDITOR` on the unit SHALL be able to record a payment on a tenancy with date, amount, account and an optional note. The amount MUST be greater than zero, and the user MUST have at least `EDITOR` on the account. Recording SHALL create a transaction in that account with the payment's date and amount, the category „Mieteinnahmen“ if it is active, the unit as target, the tenants' names as counterparty and „Miete“ with the unit as purpose. Changing a payment SHALL change its transaction; deleting a payment SHALL delete its transaction.

#### Scenario: Payment with its transaction
- **WHEN** Anna records a payment of 800,00 on 3 April 2027 into the account „Giro“ for the tenancy of Erika Musterfrau in „Whg. 1“
- **THEN** the tenancy lists the payment, and „Giro“ has a transaction of +800,00 on 3 April 2027 with the category „Mieteinnahmen“, the target „Whg. 1“ and the counterparty „Erika Musterfrau“

#### Scenario: Account without rights
- **WHEN** Bert, as `EDITOR` of the unit, chooses an account he may only read
- **THEN** the payment is rejected and nothing is recorded

#### Scenario: Delete a payment
- **WHEN** Anna deletes the payment
- **THEN** its transaction is gone from „Giro“

### Requirement: Allocation and arrears
Payments SHALL settle the months in order: each payment, in order of its date, covers the oldest month that is not fully paid. The rent account of a tenancy SHALL show per month the amount due, the amount paid and the amount open, and in total the arrears (due minus paid up to the current month) or a credit if more was paid. A month SHALL be overdue when it is not fully paid after its third working day, counting Monday to Friday without holidays, in German time.

#### Scenario: One month behind
- **WHEN** March and April are due with 800,00 each and one payment of 800,00 was made
- **THEN** March is paid, April is open with 800,00, and the arrears are 800,00

#### Scenario: Partial payment
- **WHEN** the payments add up to 1.200,00 for March and April
- **THEN** March is paid and April is open with 400,00

#### Scenario: Credit
- **WHEN** 900,00 was paid and 800,00 is due
- **THEN** the rent account shows a credit of 100,00 and no arrears

#### Scenario: Not overdue yet
- **WHEN** May is open and it is Tuesday, 4 May 2027, the second working day
- **THEN** May is open but not overdue

#### Scenario: Overdue
- **WHEN** May is open and it is Thursday, 6 May 2027, after the third working day
- **THEN** May is overdue

### Requirement: Arrears in the list of tenancies
The list „Mietverhältnisse“ SHALL show for each tenancy its arrears, marked when a month is overdue, and nothing when it is fully paid.

#### Scenario: List with arrears
- **WHEN** one tenancy is 800,00 behind with an overdue month and another is fully paid
- **THEN** the first shows „800,00 € Rückstand“ marked as overdue and the second shows no arrears

### Requirement: Access to payments
Payments and the rent account MUST be visible exactly to users with an effective role on the tenancy's unit. `VIEWER`s SHALL see the rent account and the payments' date and amount, but MUST NOT see the account and MUST NOT record, change or delete payments.

#### Scenario: Viewer
- **WHEN** Dieter, as `VIEWER` of the unit, opens the tenancy
- **THEN** he sees the rent account and the payments without account names and without the action „Zahlung erfassen“

### Requirement: Tenancies with payments
A tenancy with payments MUST NOT be deleted; the attempt SHALL be rejected with „Das Mietverhältnis hat Zahlungen und kann nicht gelöscht werden“. When the unit of a tenancy is changed, the targets of its payment transactions SHALL change to the new unit.

#### Scenario: Delete refused
- **WHEN** Anna deletes a tenancy with one payment
- **THEN** the tenancy stays, and the message „Das Mietverhältnis hat Zahlungen und kann nicht gelöscht werden“ appears
