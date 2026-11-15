# Tasks

## 1. Frontend

- [x] 1.1 Add the `BackToSignIn` button, which signs out, invalidates the session query and shows the password step (design D1, D2). Use it in `TotpStep`, `EnrollmentStep` and `PasswordChangeStep`, and route `onRestart` through the same sign-out. Verify in `LoginPage.test.tsx`:
  - the code step and the setup step show „Zurück zur Anmeldung“;
  - clicking it posts `/api/auth/logout` and shows the password step;
  - a later `/me` with `ANONYMOUS` keeps the password step;
  - a failing logout still shows the password step.

## 2. Backend check

- [x] 2.1 Add a case to `LoginFlowTest`: after the password step, `POST /api/auth/logout` makes `GET /api/auth/me` answer `ANONYMOUS`, and the TOTP endpoint rejects the old session. Verify that the test passes.

## 3. Verification

- [x] 3.1 Run `cd frontend && npm test && npm run lint && npm run build` and the backend test from 2.1, and verify that all pass.
