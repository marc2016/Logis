# Spec Delta

## ADDED Requirements

### Requirement: Owner use in the feed
The calendar feed SHALL contain owner uses under the same rules as guest bookings, titled „Eigenbelegung“ with and without guest data, and MUST NOT mark them with „?“.

#### Scenario: Owner use in the cleaning calendar
- **WHEN** the cleaning service's calendar app fetches the link „Reinigung“ while an owner use runs from 2027-06-09 to 2027-06-14
- **THEN** the feed contains an all-day event „Eigenbelegung“ from 9 through 14 June
