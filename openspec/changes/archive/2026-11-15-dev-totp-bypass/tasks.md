# Tasks

## 1. Backend

- [x] 1.1 Add `devTotpBypass` (default `false`) to `AuthProperties`, and let `AuthFlowService.checkPassword` complete the sign-in through `completeSecondFactor` when it is on (design D1, D2). Verify in a new `DevTotpBypassTest`, an integration test with the flag on and the guard satisfied, that:
  - a user with TOTP is signed in after the password and can call a protected endpoint;
  - a user who must change the start password gets `PASSWORD_CHANGE_REQUIRED`;
  - a wrong password is still rejected.
- [x] 1.2 Add `DevTotpBypassGuard`, which refuses to start without the `dev` profile and logs a warning in `dev` (design D3). Verify in a unit test with `MockEnvironment`: the flag on without `dev` throws, on with `dev` passes, off passes.
- [x] 1.3 Set `logis.auth.dev-totp-bypass: true` in `application-dev.yml`. Verify that `LoginFlowTest` (profile `test`) still requires the code.

## 2. Verification

- [x] 2.1 Run `cd backend && ./mvnw test` and verify that it passes. Then start `./start.sh` and verify that signing in as a user with TOTP needs only the password.
