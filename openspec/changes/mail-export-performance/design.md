# Design

## Context

`Pseudonymizer.apply(text, values, keepDomains, html)` works on one text at a time.

**Per call, today:**
1. It rebuilds the list of pairs (original, replacement):
   - one pair per value;
   - one pair per name part of at least 3 characters;
   - one pair per HTML-escaped form.
2. It sorts the pairs longest first.
3. It runs `replaceWord` for each pair: a new `Pattern` with the word boundaries `(?<![\p{L}\p{N}])…(?![\p{L}\p{N}])`, matched against the whole text.

**Calls per mail:** `MimeRewriter.rewrite` calls `apply` for:
- the subject;
- every personal name in the address headers;
- every text and HTML part, also in attached mails.

**Values per mail:** come from `MailHandler.SensitiveValues.of(mail)`. In `BookingMailHandler`, that is every guest value of every booking the owner can see, plus the values the recognition rules read from this mail. Thousands of values are normal.

See proposal.md (Why) for the cost.

## Goals / Non-Goals

**Goals:**
- Replace known values in one pass per text.
- Build the lookup structure once per mail.
- Keep every existing `PseudonymizerTest` and `MimeRewriterTest` case green without changing its expectations.

**Non-Goals:**
- Batching the per-mail `SELECT raw`, streaming progress to the UI, or changing the 2000-mail limit.
- Changing `MailHandler.SensitiveValues` or which values are collected.
- The IBAN, e-mail and phone patterns. They are three fixed precompiled patterns and already cheap.

## Decisions

### D1 — A trie over the pairs, scanned once per text

`Pseudonymizer` gets a small immutable `KnownValues` index:
- It is a character trie over all originals: values, name parts and, for HTML, escaped forms.
- Each trie end node stores the replacement string.

**The scan:**
- It walks the text from left to right.
- At a position where a word can start (the previous code point is not a letter or number), it follows the trie as far as it goes.
- It remembers the longest end node where the next code point is not a letter or number either.
- On a match, it appends the replacement and jumps past the match. Otherwise it copies one character.

This is leftmost-longest matching with the same word-boundary rule as today. Letters and numbers are tested with `Character.getType` for the same categories as `\p{L}` and `\p{N}`, so the boundary is the same as today's regex.

**Alternatives considered:**
- **One big regex alternation** `(?<!…)(?:v1|v2|…)(?!…)`, longest first. It needs one compile per mail instead of one per value and text. But Java's regex engine tries the alternatives one by one at each position, so the cost is still text length × values. Rejected.
- **Aho-Corasick.** It is also linear, but more code (failure links). We only need matches that start at word starts, so a plain trie walk from each word start is enough. Its cost is bounded by text length × longest value. Rejected as unnecessary.

### D2 — Built once per mail, in `MimeRewriter`

- `Pseudonymizer` exposes `KnownValues index(Collection<SensitiveValue> values)`.
- `MimeRewriter.rewrite` builds two indexes once per mail, one plain and one with HTML-escaped forms, and keeps them in its `Context`.
- `apply` and `address` take the index instead of the raw collection.
- The index assigns replacements through the existing `known` map, so the replacements stay stable across the export.

Building the index is linear in the total length of the values, about the size of a large mail. Doing it per mail keeps `MailHandler.SensitiveValues` unchanged. That interface returns owner values and mail values merged in one set.

**Alternatives considered:**
- **Split `SensitiveValues` into owner-wide and per-mail values**, and build the owner index once per export. That saves the per-mail build, but it changes a core interface and its implementation for a cost that is already small. Rejected for now.
- **Cache by set identity.** It does not work: `BookingMailHandler` returns a new set for every mail. Rejected.

### D3 — Order of pairs only decides ties

Today, the pairs are applied in sequence, longest first. With one pass, leftmost-longest decides instead.

**Same as today in the usual cases:**
- full name before its parts;
- the plain value before an escaped part.

**Different only when two different values overlap at different start positions**, e.g. „Anna Berg“ and „Berg Straße“ in „Anna Berg Straße“:
- Today the longer one wins wherever it starts.
- Now the one starting first wins.

Both are best effort. In both cases the remaining part is still replaced if it is a value or name part on its own.

**Already-inserted replacements** are no longer scanned again. Today, a later shorter value could match inside an earlier replacement („Gast“ in „Gast 1“). The new behaviour is clearer.

This rule is captured in the spec (Overlapping values).

### D4 — Duration test with invented data

A plain unit test (no Spring context) runs:
- `MimeRewriter` over 600 generated multipart mails, each with a text part and a ~40 KB HTML part;
- 4,000 generated `SensitiveValue`s.

It asserts completion within a generous bound, 20 seconds, so it is not flaky on CI. The point is to catch a return to the multiplicative cost, not to benchmark.

Before the change, the same test is run once to record the old time in the task notes. It is expected to take minutes.

All values in the test are invented, e.g. „Gast-Vorname 123“ and `example.org` addresses.

## Risks / Trade-offs

- **Boundary detection differs subtly from the regex** (surrogate pairs, combining marks). → Work on code points. Reuse the existing `wordBoundariesAreKept` and `htmlEscapedValues` tests, and add cases for umlauts and a value at the start and end of the text.
- **Overlap behaviour changes in rare cases** (D3). → It is documented in the spec, and covered by tests for overlap and for not replacing an inserted replacement.
- **Memory of the trie.** With 4,000 values of about 20 characters, there are at most about 80,000 nodes, and the trie is built per mail and dropped right away. → Acceptable. Children are stored as a small map per node.
