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

## Workflow

- New features as an OpenSpec change: propose → apply → archive.
- One branch per change.

## Language

- Only UI text is German.
- Everything else is English: code, comments, specs, docs, commit messages.

## Git

- No `Co-Authored-By` line in commit messages.
- Keep commit messages short: one line, no body.
