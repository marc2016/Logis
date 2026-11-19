# Tasks

## 1. Baseline

- [ ] 1.1 Add the duration test from design D4 (`MimeRewriterPerformanceTest`, invented values only), run it against the current code, and note the measured time in this task line. It is expected to exceed the bound; mark it `@Disabled` only for this measurement, not in the commit.

## 2. Single-pass replacement

- [ ] 2.1 Add the `KnownValues` trie index to `Pseudonymizer` (design D1):
  - pairs as today: values, name parts of at least 3 characters, HTML-escaped forms when `html`;
  - replacements from the existing `known` map;
  - a leftmost-longest scan with the `\p{L}\p{N}` word boundary on code points.

  Verify: all existing `PseudonymizerTest` cases pass unchanged.
- [ ] 2.2 Add `PseudonymizerTest` cases. Verify by `./mvnw test -Dtest=PseudonymizerTest`. The cases:
  - full name wins over its part;
  - an inserted replacement („Gast 1“) is not replaced again when „Gast“ is a known value;
  - overlapping values: the one that starts first wins;
  - values with umlauts at the start and end of the text;
  - a value next to a letter stays.
- [ ] 2.3 Build the plain and the HTML index once per mail in `MimeRewriter.rewrite` and pass them through `Context` to subject, address names and text parts (design D2). Verify: `MimeRewriterTest` and `MailExportTest` pass unchanged.
- [ ] 2.4 Remove the per-call `pairs`/`replaceWord` path that is no longer used. Verify: `./mvnw test -Dtest='Pseudonymizer*,MimeRewriter*,MailExport*'` is green, and the duration test from 1.1 passes within its bound. Note the new time next to the baseline in 1.1.

## 3. Verification

- [ ] 3.1 Run the full backend suite `cd backend && ./mvnw test`, stopping a running e2e stack first. It must be green.
- [ ] 3.2 Run the existing e2e test of the admin mail export against a stack with the new backend image. It must be green; the UI is unchanged, so no new e2e test or `e2e/COVERAGE.md` entry is needed.

## Workflow follow-up

- Archive the change with `/opsx:archive` and sync the `mail-export` spec.
