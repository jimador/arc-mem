## Why

Duplicate detection via LLM is expensive and produces noisy results on obvious dupes (exact matches, minor formatting differences). A fast normalized-string pre-check catches ~80% of duplicates before hitting the model, reducing cost, API calls, and demo clutter.

## What Changes

- Add `NormalizedStringDuplicateDetector` that normalizes text (lowercase, collapse whitespace, strip punctuation) and detects exact matches
- Modify `DuplicateDetector` to use composite pattern: fast-path first, LLM fallback only for novel candidates
- Make the strategy configurable so either detector can be toggled or combined
- No breaking changes; existing behavior preserved with dual-strategy approach

## Capabilities

### New Capabilities
- `normalized-string-dedup`: Fast-path duplicate detection using string normalization before LLM invocation

### Modified Capabilities
- `extraction`: Duplicate detection workflow now includes fast-path pre-check (behavior change in extraction flow, not spec change)

## Impact

- **Files**: `extract/DuplicateDetector.java`, new `extract/NormalizedStringDuplicateDetector.java`, new `extract/CompositeDuplicateDetector.java` (optional SPI)
- **APIs**: `DuplicateDetector.isDuplicate()` behavior unchanged; internal strategy selection added
- **Config**: Optional `anchor.dedup-strategy` property to select FAST_ONLY, LLM_ONLY, or FAST_THEN_LLM
- **Affected**: Extraction and anchor promotion flow
- **Performance**: Reduces LLM calls for dedup by ~80%, faster extraction

## Constitutional Alignment

- RFC 2119 keywords: MUST NOT add LLM calls for obvious dupes; SHOULD cache normalized forms
- Single-module Maven project: changes contained to `extract/` package
- Neo4j persistence unchanged
