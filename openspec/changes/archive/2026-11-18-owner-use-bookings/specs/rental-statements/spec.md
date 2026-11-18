# Spec Delta

## ADDED Requirements

### Requirement: Owner use is not settled
An owner use MUST NOT be assigned to a statement, and statements MUST NOT offer owner uses as open bookings.

#### Scenario: Assign an owner use
- **WHEN** Anna tries to assign an owner use to a statement
- **THEN** the system rejects this with the message „Eine Eigenbelegung wird nicht abgerechnet“
