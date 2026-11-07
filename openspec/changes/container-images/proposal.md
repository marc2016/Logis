# Proposal

## Why

The images are built on the mini PC itself (`docker compose up -d --build`), which is slow and only works with the source code there. Built, tested images on GitHub let the mini PC update with a plain pull, and a version can be rolled out or rolled back on purpose.

## What Changes

- A GitHub Actions workflow runs the backend and frontend tests on every push to `main` and on every version tag `v*`. Only when all tests pass, it builds the backend and frontend images for amd64 and arm64 and publishes them to the GitHub Container Registry as `ghcr.io/marc2016/logis-backend` and `ghcr.io/marc2016/logis-frontend`.
- Tags: a push to `main` publishes `latest` and `sha-<short commit>`; a tag `v1.2.0` publishes `1.2.0` and `1.2`.
- The images are public. They contain no secrets; all secrets keep coming from the environment at runtime.
- `compose.yml` refers to the published images, with `LOGIS_VERSION` choosing the tag. Updating the mini PC becomes `docker compose pull && docker compose up -d`. Building locally with `--build` keeps working.
- The build stages of both Dockerfiles run on the platform of the build machine, so the arm64 image is built without emulating Maven and npm.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `deployment`: Tested container images are published to a registry and the stack runs from them.

## Impact

- **New:** `.github/workflows/images.yml`.
- **Changed:** `backend/Dockerfile`, `frontend/Dockerfile` (build stage platform), `compose.yml` (image names), `README.md` and `.env.example` (update instructions).
- **Not changed:** application code.
- **One-time manual steps** (outside the repository): push the commits so that the workflow runs; after the first run, set both packages to public in the GitHub package settings.
- **Assumptions** (proposed during exploration, not explicitly confirmed; easy to change):
  - Pull requests run the tests but publish nothing.
  - The workflow uses the built-in `GITHUB_TOKEN`; no additional secret is needed.
