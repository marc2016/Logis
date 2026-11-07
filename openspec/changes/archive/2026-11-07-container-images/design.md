# Design

## Context

There is no CI yet. Both Dockerfiles are multi-stage (Maven/JDK 21 resp. Node 24 build stage, JRE resp. nginx runtime stage) and all base images exist for amd64 and arm64. `compose.yml` builds locally and tags `logis-backend:${LOGIS_VERSION:-latest}`. Backend tests use Testcontainers (Postgres, GreenMail) unless `LOGIS_TEST_DB_URL` is set; GitHub-hosted runners have Docker, so the full suite runs there. Motivation is in proposal.md.

## Goals / Non-Goals

**Goals:**
- Tested multi-arch images on every push to `main` and every version tag.
- Updating the mini PC by pulling, with a chosen version.

**Non-Goals:**
- Automatic deployment to the mini PC (watchtower, webhooks).
- Releases with notes, changelogs or version bumps in `pom.xml` / `package.json`.
- Image signing and SBOMs (possible later with `docker/build-push-action` attestations).

## Decisions

### D1: One workflow with test and image jobs

`.github/workflows/images.yml`, triggered on `push` to `main`, on tags `v*` and on `pull_request`:

```
test-backend  (temurin 21, ./mvnw -B verify, Testcontainers)  --+
test-frontend (node 24, npm ci, lint, test, build)            --+--> images (matrix: backend, frontend)
                                                                      only on push, not on pull_request
```

`images` needs both test jobs. Permissions: `contents: read`, and `packages: write` only for the image job. Login with the built-in `GITHUB_TOKEN`.

### D2: Multi-arch build without emulating the build stage

`docker/setup-qemu-action` + `docker/setup-buildx-action`, `platforms: linux/amd64,linux/arm64`. The build stages get `FROM --platform=$BUILDPLATFORM`: the jar and the static frontend files are platform-independent, so Maven and npm run natively on the amd64 runner and only the runtime stages (base image pull, a `useradd`, file copies) run per platform. GitHub Actions cache (`cache-from/to: type=gha`) per image.

- *Alternative:* emulate everything with QEMU. Works, but a Maven build under emulation takes many minutes.

### D3: Tags with docker/metadata-action

```
type=raw,value=edge,enable={{is_default_branch}}
type=sha,prefix=sha-
type=semver,pattern={{version}}
type=semver,pattern={{major}}.{{minor}}
type=raw,value=latest,enable=${{ startsWith(github.ref, 'refs/tags/v') }}
```

Images: `ghcr.io/marc2016/logis-backend`, `ghcr.io/marc2016/logis-frontend` (lowercase owner). OCI labels link the image to the repository.

### D4: compose.yml

`image: ghcr.io/marc2016/logis-backend:${LOGIS_VERSION:-latest}` (same for frontend), keeping `build:` so that `docker compose up -d --build` still builds locally. `README.md` and `.env.example` describe `docker compose pull && docker compose up -d` and pinning `LOGIS_VERSION`.

## Risks / Trade-offs

- [Public images] → They contain only code that is also needed to run logis; secrets come from `.env`. The repository's visibility is unchanged.
- [GHCR packages start private] → One-time manual switch to public, documented in the README.
- [Long CI run] → Maven and npm caches (`actions/setup-java` / `setup-node` cache) and the buildx GHA cache.
- [`latest` missing before the first release] → README: use `LOGIS_VERSION=edge` or push `v0.1.0` first.
- [`edge` moves with every push] → Only for trying out; the default `latest` moves only with a version tag, and `LOGIS_VERSION` can pin a version.
