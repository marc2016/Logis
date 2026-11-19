## ADDED Requirements

### Requirement: Own e-mail address
Under „Mein Konto“, a fully signed-in user SHALL see their e-mail address and SHALL be able to change it. The change MUST be confirmed with a 6-digit code that Logis sends to the new address. The address MUST only change once the code is entered. A code MUST be valid for 30 minutes and for 5 attempts, and a new request MUST replace an earlier code.

#### Scenario: Change the address
- **WHEN** Anna enters „anna.neu@example.org“ and then the code from the mail sent to that address
- **THEN** „anna.neu@example.org“ is their address

#### Scenario: Wrong code
- **WHEN** Anna enters a wrong code
- **THEN** the address stays unchanged, and they see „Der Code ist ungültig oder abgelaufen.“

#### Scenario: Too many attempts
- **WHEN** Anna has entered a wrong code 5 times
- **THEN** the code is no longer accepted, even if correct, and Anna has to request a new one

#### Scenario: Expired code
- **WHEN** Anna enters the code 31 minutes after requesting it
- **THEN** the address stays unchanged, and they see „Der Code ist ungültig oder abgelaufen.“

### Requirement: Notice to the old address
After a user changed their own address, Logis SHALL send the notice „Deine E-Mail-Adresse in Logis wurde geändert.“ to the previous address, if there was one, naming the new address only partially (for example „a…@example.org“). A failure to send this notice MUST NOT undo the change.

#### Scenario: Notice sent
- **WHEN** Anna changes their address from „anna@example.org“ to „anna.neu@example.org“
- **THEN** „anna@example.org“ receives the notice, which does not contain the full new address

### Requirement: Address change needs outgoing mail
If no outgoing mail server is configured, users MUST NOT be able to change their own address. The card SHALL show „Der E-Mail-Versand ist nicht eingerichtet. Bitte wende dich an einen Admin.“ instead of the change form.

#### Scenario: No outgoing mail server
- **WHEN** no outgoing mail server is configured and Anna opens „Mein Konto“
- **THEN** they see their address and the notice, but no way to change it

### Requirement: Notice about a missing address
A signed-in user without an e-mail address SHALL see the notice „Bitte hinterlege eine E-Mail-Adresse unter Mein Konto.“ in the app, with a link to „Mein Konto“, until they have an address.

#### Scenario: Existing user without an address
- **WHEN** Anna, created before e-mail addresses existed, signs in
- **THEN** they see the notice with the link to „Mein Konto“

#### Scenario: Address added
- **WHEN** Anna has confirmed an address
- **THEN** the notice no longer appears
