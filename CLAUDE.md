# CLAUDE.md

## Tech stack

- **Backend:** Spring, Spring Boot
- **Frontend:** React, shadcn

## Commands

- Start (development): `./start.sh`, stop the database with `./start.sh stop`
- Backend tests: `cd backend && ./mvnw test`
- Frontend: `cd frontend && npm test`, `npm run lint`, `npm run build`

## Architecture

- Modular monolith with Spring Modulith.
- Each module has its own DB schema (`core`, `vacationrental`, …).
- Every domain query goes through the per-object sharing model.
- The system role `ADMIN` grants no access to domain data.

## Data and migrations

- Real data is in use. Existing data must survive every change.
- Never edit an applied Flyway migration. Every schema or data change gets a new migration (`V<yyyyMMdd>.<HHmm>__<module>_<topic>.sql` in the module's folder), including data migration for existing rows.
- Warn before fundamental changes (dropping or restructuring tables, changing ownership or relations, data that cannot be migrated cleanly) and get confirmation first. Such changes are still possible if needed, but only knowingly and with a migration path.

## Test data

- No real data in git: no guest, tenant or owner names, addresses, phone numbers, emails, booking or customer numbers, amounts or postal codes from real mails or bookings. This covers test mails, tests, specs, docs and commit messages.
- Real samples (e.g. mails the user pastes) are only used for their structure. Test files get invented values (names like „Erika Musterfrau“, `example.org` addresses, made-up booking numbers).
- Before committing anything derived from a real sample, check that none of its values remain.

## Workflow

- New features as an OpenSpec change: propose → apply → archive.
- One branch per change.

## Language

- Only UI text is German.
- Everything else is English: code, comments, specs, docs, commit messages.

## Git

- No `Co-Authored-By` line in commit messages.
- Keep commit messages short: one line, no body.
