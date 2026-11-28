## ADDED Requirements

### Requirement: Transactions managed by a module
A module SHALL be able to create transactions that it manages, such as the transaction of a rent payment. Such a transaction SHALL be listed like any other and show where it comes from („Mietzahlung“). It MUST NOT be changed, moved, attributed or deleted in the finances, also not by bulk actions; the attempt SHALL be rejected with „Diese Transaktion gehört zu einer Mietzahlung und wird dort geändert“. Existing transactions MUST NOT become managed.

#### Scenario: Rent payment in the account
- **WHEN** Anna opens a transaction created by a rent payment
- **THEN** it shows „Mietzahlung“ with a link to the tenancy, and its fields cannot be edited

#### Scenario: Bulk delete with a managed transaction
- **WHEN** Anna deletes three selected transactions, one of them from a rent payment
- **THEN** the two others are deleted and the summary reports the rent payment's transaction as not deleted
