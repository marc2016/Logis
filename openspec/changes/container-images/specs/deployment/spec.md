# Spec Delta

## ADDED Requirements

### Requirement: Published container images
Every push to the main branch and every version tag SHALL produce backend and frontend images for amd64 and arm64 in a public container registry, but only after all backend and frontend tests have passed. Images of the main branch MUST be tagged `latest` and with the commit; images of a version tag `vX.Y.Z` MUST be tagged `X.Y.Z` and `X.Y`. The images MUST NOT contain secrets. The stack SHALL run from the published images, with the tag configurable through the environment.

#### Scenario: Push to main
- **WHEN** a commit is pushed to `main` and all tests pass
- **THEN** `logis-backend:latest` and `logis-frontend:latest` point to images built from that commit for amd64 and arm64

#### Scenario: Failing test
- **WHEN** a commit is pushed to `main` and a backend test fails
- **THEN** no image is published and `latest` stays at the previous build

#### Scenario: Release a version
- **WHEN** the tag `v1.2.0` is pushed
- **THEN** the images are also available as `1.2.0` and `1.2`

#### Scenario: Update the mini PC
- **WHEN** the operator sets `LOGIS_VERSION=1.2.0` and runs `docker compose pull` and `docker compose up -d`
- **THEN** the stack runs version 1.2.0 without building anything on the mini PC
