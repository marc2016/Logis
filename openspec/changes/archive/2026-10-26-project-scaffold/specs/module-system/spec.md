# Spec Delta

## Purpose

Logis consists of a core and domain modules (e.g. vacation rental). This capability defines how modules are enabled and disabled and how the system exposes the active modules.

## ADDED Requirements

### Requirement: Enable modules via configuration
Every domain module MUST be possible to enable and disable through a configuration setting, without changing code. A module without an explicit setting SHALL count as enabled.

#### Scenario: Module is active by default
- **WHEN** the application starts without a setting for the module `vacationrental`
- **THEN** the module `vacationrental` is active

#### Scenario: Module is disabled
- **WHEN** the application starts with the setting that `vacationrental` is disabled
- **THEN** the module `vacationrental` is not active

### Requirement: Retrieve active modules
The system SHALL provide signed-in users with a list of the active modules. Each entry MUST contain at least a stable module ID. Disabled modules MUST NOT appear in the list.

#### Scenario: List contains active modules
- **WHEN** a signed-in user retrieves the module list and `vacationrental` is active
- **THEN** the response contains an entry with the ID `vacationrental`

#### Scenario: Disabled module is missing from the list
- **WHEN** a signed-in user retrieves the module list and `vacationrental` is disabled
- **THEN** the response contains no entry with the ID `vacationrental`

#### Scenario: List without sign-in
- **WHEN** a client that is not signed in retrieves the module list
- **THEN** the system responds with HTTP 401

### Requirement: Endpoints of disabled modules are unreachable
The API endpoints of a disabled module MUST NOT be reachable. Calls SHALL be answered with HTTP 404.

#### Scenario: Call to a disabled module
- **WHEN** a signed-in user calls an endpoint under `/api/vacationrental/` and `vacationrental` is disabled
- **THEN** the system responds with HTTP 404

#### Scenario: Call to an active module
- **WHEN** a signed-in user calls the status endpoint of `vacationrental` and the module is active
- **THEN** the system responds with HTTP 200

### Requirement: Data of disabled modules is kept
When a module is disabled, the system MUST leave its stored data unchanged. After the module is enabled again, the previous data SHALL be available again.

#### Scenario: Disable and enable again
- **WHEN** a module with stored data is disabled, the application is restarted, and the module is then enabled again
- **THEN** the module's previously stored data is present and unchanged
