# Spec Delta

## MODIFIED Requirements

### Requirement: Forwarded mails
Forwarded booking mails MUST be recognised like the originals, whether forwarded inline (with „WG:“ or „Fwd:“ and quoted text) or as an attachment. Changes that forwarding makes to the spacing between a field label and its value (spaces next to tabs, several spaces) MUST NOT prevent recognition.

#### Scenario: Forwarded by the user
- **WHEN** Anna forwards the confirmation of booking 275505 to the mailbox with the subject "WG: Neue Buchung für Haus Seeufer - Nordhorn"
- **THEN** it is read as a confirmation of Fewo & Meer like the original

#### Scenario: Forwarded as attachment
- **WHEN** the Holidu confirmation arrives as an attachment of a forwarded mail
- **THEN** the attached original is read, and the inbox shows its subject

#### Scenario: Forwarded with changed spacing
- **WHEN** Anna forwards the confirmation of booking 274907 and the forwarded text reads „Buchungsnummer ⇥274907“ and „Anreise ⇥ 01.11.2026“, with a space before and after the tab
- **THEN** it is read as a confirmation of Fewo & Meer with booking number 274907, arrival 2026-11-01, the guest's name and address, like the original
