# Tasks

## 1. Images

- [ ] 1.1 Let the build stages of `backend/Dockerfile` and `frontend/Dockerfile` run on `$BUILDPLATFORM` (D2). Verify: both images build for `linux/amd64` and `linux/arm64` with `docker buildx build --platform linux/amd64,linux/arm64` where Docker is available, otherwise review the Dockerfiles against D2.
- [ ] 1.2 Add `.github/workflows/images.yml` with the test jobs and the image job according to D1–D3. Verify: `actionlint` (or a YAML check) reports no errors, and the job graph publishes nothing for pull requests and nothing when a test job fails.
- [ ] 1.3 Point `compose.yml` to the ghcr images and document pull, version pinning and the one-time package visibility in `README.md` and `.env.example` (D4). Verify: `docker compose config` resolves the image names with and without `LOGIS_VERSION`, or the file is reviewed where Docker is missing.

## 2. Acceptance

- [ ] 2.1 After the user has pushed: the workflow run on `main` is green and both packages show `latest` and `sha-…` for amd64 and arm64. Verify: the run and the package pages on GitHub.
