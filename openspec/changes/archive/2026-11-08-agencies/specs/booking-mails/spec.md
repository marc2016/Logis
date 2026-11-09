# Spec Delta

## MODIFIED Requirements

### Requirement: Unit mapping per agency
The system SHALL map the property name of a mail to a unit per agency, where the agency is the one resolved from the mail through the shared list of agencies. The first time a name is unknown, the mail MUST stay in the inbox as „Einheit zuordnen“. Once the user assigns a unit, the mapping MUST be remembered, and that mail and later mails with this name MUST be processed with it. Mappings MUST stay per mailbox owner, MUST follow their agency when it is renamed or merged, and MUST be found through any of the agency's mail names. Only units on which the mailbox owner has at least `EDITOR` MUST be offered.

#### Scenario: Two names for one house
- **WHEN** Anna has assigned "Haus Seeufer - Nordhorn" (Fewo & Meer) and "Ferienhaus Seeufer" (Holidu) to the same unit
- **THEN** mails of both agencies create bookings on that unit without asking again

#### Scenario: Mapping survives renaming
- **WHEN** Anna has assigned "Ferienhaus Seeufer" for „VVV-Stadt- und Citymarketing Nordhorn e.V.“ and then renames that agency to „VVV Nordhorn“
- **THEN** the next VVV mail for "Ferienhaus Seeufer" creates a booking without asking for the unit again

## ADDED Requirements

### Requirement: Agency of imported bookings
A booking created from a mail SHALL reference the agency resolved from the mail's agency name. Matching a mail to an existing booking by booking number MUST compare agencies by identity, so a booking of an agency matches mails naming any of its mail names. A booking entered by hand without agency SHALL get the resolved agency of the first mail that matches it by number.

#### Scenario: Cancellation after renaming
- **WHEN** booking 250905 references „Fewo & Meer“, Anna has added the mail name „FEWO & MEER e.K.“, and a cancellation naming „FEWO & MEER e.K.“ with number 250905 arrives
- **THEN** booking 250905 shows a proposed cancellation „zu prüfen“
