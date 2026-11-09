# Tasks

## 1. Fix

- [ ] 1.1 Add the test mail `fewo-forward-apple-spacing.eml` with invented guest data (D2) and a parser test that expects all confirmation fields, the guest name and the address. Verify: the test fails before the fix (fields are empty).
- [ ] 1.2 Use one tolerant separator for the Fewo & Meer fields and the address (D1). Verify: the new test and all existing `MailParsersTest` cases pass.
- [ ] 1.3 Check the DS rule and the other Fewo & Meer patterns for the same weakness; add a test where a gap is found. Verify: findings noted in this task, tests green.
- [ ] 1.4 Add an import test: the forwarded mail creates the booking, and a mail stored as not recognised is recognised after „Erneut verarbeiten“. Verify: `cd backend && ./mvnw test` green apart from tests that need Docker.
