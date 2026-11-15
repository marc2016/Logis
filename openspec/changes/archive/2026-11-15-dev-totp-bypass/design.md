# Design

## Context

- `AuthFlowService.checkPassword` returns the next stage after a correct password: `TOTP_REQUIRED` when the user has a secret, otherwise `TOTP_ENROLLMENT_REQUIRED`.
- `AuthController.login` starts the session with that stage.
- `completeSecondFactor` resets the failure counter. It then returns `PASSWORD_CHANGE_REQUIRED` or `AUTHENTICATED`, and records the login time.
- The sign-in page already handles any stage returned by `/login`, including `AUTHENTICATED`.
- `./start.sh` runs the backend with the profile `dev` (`application-dev.yml`). Integration tests use `test`, and the e2e and production stacks use no extra profile.

## Goals / Non-Goals

**Goals:**
- Password-only sign-in locally, with no frontend change.
- The flag cannot take effect in production by accident.

**Non-Goals:**
- Skipping the password itself or auto-login.
- Changing the stored TOTP data.
- Per-user exemptions.

## Decisions

### D1. Flag in `AuthProperties`
- `logis.auth.dev-totp-bypass`, a boolean that defaults to `false`.
- It is set to `true` only in `application-dev.yml`. It is not mapped to an environment variable in `application.yml`, so no `.env` entry can switch it on in production.
- *Alternative:* a `@Profile("dev")` bean that replaces the flow. Rejected because a duplicated flow is more code to keep in sync than one guarded branch.

### D2. Bypass in `checkPassword`
- When the flag is on, `checkPassword` returns `completeSecondFactor(user)` instead of a TOTP stage.
- This reuses the existing path: the failure counter is reset, the start password still forces `PASSWORD_CHANGE_REQUIRED`, and the login time is recorded.
- The rest is unchanged: unknown users, wrong passwords, disabled users and lockout behave as before.

### D3. Startup guard
- A small component (`DevTotpBypassGuard`) runs at startup.
- If the flag is on and `Environment.matchesProfiles("dev")` is false, it throws an `IllegalStateException`. The message is "logis.auth.dev-totp-bypass is only allowed with the dev profile", and the application does not start.
- If the flag is on in `dev`, it logs a warning: "Two-factor sign-in is disabled (dev)".
- The check is a static method taking the flag and the `Environment`, so it can be unit-tested with a `MockEnvironment`.

## Risks / Trade-offs

- [Someone adds the flag to a production config] → The guard stops the startup. The flag is not wired to an environment variable either.
- [The two-factor UI is no longer exercised locally] → The e2e test `auth.spec.ts` covers setup, code sign-in and recovery codes on every CI run.
