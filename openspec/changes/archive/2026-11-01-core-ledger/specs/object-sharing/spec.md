# Spec Delta

## Purpose

Domain data in Logis belongs to individual users and is shared deliberately. This capability governs sharing individual objects (accounts, to start with) with existing users at graded levels of access, and it applies to all modules.

## ADDED Requirements

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
An `OWNER` SHALL be able to invite existing, active users to an object as `VIEWER`, `EDITOR` or `OWNER`. The share MUST take effect immediately. Invitations to nonexistent or disabled users MUST be rejected. If a user already has a share, the system MUST NOT create a duplicate and MUST change the role instead.

#### Scenario: Invite
- **WHEN** Anna, as `OWNER`, invites Bert to their account as `EDITOR`
- **THEN** Bert sees the account on their next request and may enter transactions in it

#### Scenario: Disabled user
- **WHEN** Anna tries to invite a disabled user
- **THEN** the system rejects this with the message „Benutzer nicht gefunden“

### Requirement: Change and revoke shares
An `OWNER` SHALL be able to change the role of an existing share and to revoke shares. Every user SHALL be able to give up their own share. An object MUST always keep at least one `OWNER`.

#### Scenario: Revoke a share
- **WHEN** Anna revokes Bert's share
- **THEN** the account is no longer visible to Bert on their next request

#### Scenario: Last owner
- **WHEN** Anna, as the only `OWNER`, tries to change their own role to `VIEWER` or give up their share
- **THEN** the system rejects this with the message „Es muss mindestens einen Owner geben“

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
