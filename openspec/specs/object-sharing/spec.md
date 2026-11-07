# object-sharing Specification

## Purpose

Domain data in Logis belongs to individual users and is shared deliberately. This capability governs sharing individual objects (accounts, to start with) with existing users at graded levels of access, and it applies to all modules.

## Requirements

### Requirement: Roles per object
Every share MUST give a user exactly one of the roles `OWNER`, `EDITOR` or `VIEWER` for an object:
- `VIEWER` SHALL be allowed to read the object and its subordinate data.
- `EDITOR` SHALL additionally be allowed to create, change and delete subordinate data, e.g. the transactions of an account.
- `OWNER` SHALL additionally be allowed to change and archive the object itself and to manage its shares.

Without a share, an object MUST be invisible to a user. It MUST then be treated as nonexistent, i.e. answered with HTTP 404 instead of 403.

#### Scenario: Without a share
- **WHEN** user Bert retrieves an account that is not shared with them
- **THEN** the system responds with HTTP 404, and the account appears in none of their lists

#### Scenario: Viewer may not write
- **WHEN** Bert, as `VIEWER` of an account, tries to create a transaction in it
- **THEN** the system responds with HTTP 403

#### Scenario: Editor may not manage shares
- **WHEN** Bert, as `EDITOR` of an account, tries to invite another user
- **THEN** the system responds with HTTP 403

### Requirement: Admin without automatic data access
The system role `ADMIN` MUST NOT grant access to domain objects. An admin only sees objects they own or that are shared with them.

#### Scenario: Admin and someone else's account
- **WHEN** an admin retrieves an account that is not shared with them
- **THEN** the system responds with HTTP 404

### Requirement: Creator is owner
Whoever creates a shareable object MUST automatically become its `OWNER`.

#### Scenario: Create an account
- **WHEN** Anna creates an account
- **THEN** Anna is `OWNER` of that account

### Requirement: Invite users
An `OWNER` SHALL be able to invite existing, active users to an object as `VIEWER`, `EDITOR` or `OWNER`. For a child object such as a unit, the `OWNER`s of its parent object SHALL invite instead, and only the roles `VIEWER` and `EDITOR` MUST be allowed. The share MUST take effect immediately. Invitations to nonexistent or disabled users MUST be rejected. If a user already has a share, the system MUST NOT create a duplicate and MUST change the role instead.

#### Scenario: Invite
- **WHEN** Anna, as `OWNER`, invites Bert to their account as `EDITOR`
- **THEN** Bert sees the account on their next request and may enter transactions in it

#### Scenario: Disabled user
- **WHEN** Anna tries to invite a disabled user
- **THEN** the system rejects this with the message „Benutzer nicht gefunden“

#### Scenario: Invite to a unit
- **WHEN** Anna, as `OWNER` of a property, invites Carla to its unit "Whg. 2" as `EDITOR`
- **THEN** Carla sees the property as a frame and the unit "Whg. 2" on their next request

#### Scenario: Owner role on a unit
- **WHEN** Anna tries to invite Carla to a unit as `OWNER`
- **THEN** the system rejects this with the message „Für Einheiten gibt es nur die Rollen Lesen und Bearbeiten“

#### Scenario: Unit editor may not invite
- **WHEN** Carla, as `EDITOR` of a unit without a share on the property, tries to invite another user to the unit
- **THEN** the system responds with HTTP 403

### Requirement: Change and revoke shares
An `OWNER` SHALL be able to change the role of an existing share and to revoke shares. For a child object such as a unit, the `OWNER`s of its parent object SHALL do this instead. Every user SHALL be able to give up their own share. An object that can have owners MUST always keep at least one `OWNER`; child objects have no owners of their own.

#### Scenario: Revoke a share
- **WHEN** Anna revokes Bert's share
- **THEN** the account is no longer visible to Bert on their next request

#### Scenario: Last owner
- **WHEN** Anna, as the only `OWNER`, tries to change their own role to `VIEWER` or give up their share
- **THEN** the system rejects this with the message „Es muss mindestens einen Owner geben“

#### Scenario: Revoke a unit share
- **WHEN** Anna, as `OWNER` of the property, revokes Carla's share on the unit "Whg. 2"
- **THEN** the property and the unit are no longer visible to Carla on their next request, unless Carla has another share on the property or one of its units

### Requirement: View shares
Every user with access to an object SHALL be able to see who has which role (display name, username, role).

#### Scenario: List of shares
- **WHEN** Bert, as `VIEWER`, opens the shares of an account
- **THEN** they see everyone with access and their role, but no buttons for changing anything

### Requirement: User search for inviting
Signed-in users SHALL be able to search active users by username or display name in order to invite them. The search MUST require at least 2 characters, return at most 10 hits and output only ID, username and display name per hit. Disabled users MUST NOT appear.

#### Scenario: Search
- **WHEN** Anna searches for "be"
- **THEN** they receive active users whose username or display name contains "be", without role, status or other details

#### Scenario: Search too short
- **WHEN** Anna searches for "b"
- **THEN** they receive an empty list

### Requirement: Effective role on child objects
The effective role of a user on a child object such as a unit MUST be the higher of their role on the parent object and their role on the child object. Every access to data belonging to a child object MUST be checked against this effective role. A role on a child object MUST NOT grant any rights on the parent object or on its other child objects. Modules SHALL be able to query the effective role on a unit and the set of units on which a user has at least a given effective role.

#### Scenario: Property role covers all units
- **WHEN** Dieter is `VIEWER` of a property and has no unit shares
- **THEN** Dieter's effective role on every unit of the property is `VIEWER`

#### Scenario: Higher role on one unit
- **WHEN** Dieter is `VIEWER` of a property and `EDITOR` of its unit "Whg. 1"
- **THEN** Dieter's effective role is `EDITOR` on "Whg. 1" and `VIEWER` on the other units

#### Scenario: Unit role does not spread
- **WHEN** Carla is `EDITOR` of the unit "Whg. 2" only
- **THEN** Carla has no role on the property and no role on "Whg. 1"
