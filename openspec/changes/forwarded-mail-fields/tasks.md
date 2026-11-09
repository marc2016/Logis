# Tasks

## 1. Fix

- [x] 1.1 Add the test mail `fewo-forward-apple-plain.eml` and `fewo-forward-apple-html.eml` with invented guest data (D2) and a parser test that expects all confirmation fields, the guest name and the address. Verify: the test fails before the fix (fields are empty).
- [x] 1.2 Use one tolerant separator for the Fewo & Meer fields and the address (D1). Verify: the new test and all existing `MailParsersTest` cases pass.
- [x] 1.3 Check the DS rule and the other Fewo & Meer patterns for the same weakness; add a test where a gap is found. Verify: findings noted in this task, tests green. Findings: only the Fewo & Meer table fields and address relied on a tab or two spaces right after the label; the short-notice fields, the DS rule and all subjects use „Label:“ with flexible spacing on lines that are already trimmed, so no further gap.
- [x] 1.4 Add an import test: the forwarded mail creates the booking, and a mail stored as not recognised is recognised after „Erneut verarbeiten“. Verify: `cd backend && ./mvnw test` green apart from tests that need Docker.
