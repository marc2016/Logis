# Tasks

## 1. Baseline

- [x] 1.1 Add the duration test from design D4 (`MimeRewriterPerformanceTest`, invented values only), run it against the current code, and note the measured time in this task line. It is expected to exceed the bound; mark it `@Disabled` only for this measurement, not in the commit. Baseline, run with 10 instead of 600 mails because of the run time: 44.6 s for 10 mails against 4,004 values, about 4.5 s per mail, so about 45 minutes for 600.

## 2. Single-pass replacement

- [x] 2.1 Add the `KnownValues` trie index to `Pseudonymizer` (design D1):
  - pairs as today: values, name parts of at least 3 characters, HTML-escaped forms when `html`;
  - replacements from the existing `known` map;
  - a leftmost-longest scan with the `\p{L}\p{N}` word boundary on code points.

  Verify: all existing `PseudonymizerTest` cases pass unchanged.
- [x] 2.2 Add `PseudonymizerTest` cases. Verify by `./mvnw test -Dtest=PseudonymizerTest`. The cases:
  - full name wins over its part;
  - an inserted replacement („Gast 1“) is not replaced again when „Gast“ is a known value;
  - overlapping values: the one that starts first wins;
  - values with umlauts at the start and end of the text;
  - a value next to a letter stays.
- [x] 2.3 Build the plain and the HTML index once per mail in `MimeRewriter.rewrite` and pass them through `Context` to subject, address names and text parts (design D2). Verify: `MimeRewriterTest` and `MailExportTest` pass unchanged.
- [x] 2.4 Remove the per-call `pairs`/`replaceWord` path that is no longer used. Verify: `./mvnw test -Dtest='Pseudonymizer*,MimeRewriter*,MailExport*'` is green, and the duration test from 1.1 passes within its bound. Note the new time next to the baseline in 1.1. After: 9.3 to 9.8 s for 600 mails; about 4.8 s of that without any known values (MIME parsing and the fixed patterns).

## 3. Verification

- [x] 3.1 Run the full backend suite `cd backend && ./mvnw test`, stopping a running e2e stack first. It must be green.
- [x] 3.2 Run the existing e2e test of the admin mail export against a stack with the new backend image. It must be green; the UI is unchanged, so no new e2e test or `e2e/COVERAGE.md` entry is needed.

## Workflow follow-up

- Archive the change with `/opsx:archive` and sync the `mail-export` spec.
