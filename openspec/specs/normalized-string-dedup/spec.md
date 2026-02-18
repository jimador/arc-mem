# Normalized-String Dedup Specification

## ADDED Requirements

### Requirement: Fast-path duplicate detection via string normalization

The duplicate detection pipeline SHALL apply a fast, normalized-string pre-check before invoking the LLM dedup detector. Candidates matching a normalized anchor SHALL be marked duplicate without LLM invocation.

#### Scenario: Exact match after normalization
- **WHEN** a candidate text "Dr. Smith is valid" is checked against existing anchor "dr. smith is valid"
- **THEN** the fast-path normalizes both to "dr smith is valid" and detects exact match
- **AND** returns `true` (duplicate) without calling the LLM

#### Scenario: Case and whitespace variation
- **WHEN** a candidate "The Earth  IS round" is checked against anchor "the earth is round"
- **THEN** normalization collapses case and whitespace, detects match, returns `true`
- **AND** no LLM call is made

#### Scenario: Punctuation stripping
- **WHEN** a candidate "Mars' atmosphere; thin." matches "mars atmosphere thin" after normalization
- **THEN** fast-path detects match and returns `true`
- **AND** avoids LLM invocation

### Requirement: Normalization algorithm

The fast-path normalizer SHALL transform candidate and anchor texts as follows:
1. Convert to lowercase
2. Collapse consecutive whitespace to single spaces and trim
3. Remove all non-alphanumeric characters except spaces
4. Perform exact string equality check on normalized forms

#### Scenario: Normalization algorithm correctness
- **WHEN** normalization is applied to "Hello, World!  123"
- **THEN** result is "hello world 123"

#### Scenario: Semantic meaning preserved
- **WHEN** normalization is applied to "Dr. Smith" and "dr smith"
- **THEN** both normalize to "dr smith" and are detected as equivalent

### Requirement: Fallback to LLM for novel candidates

If the fast-path does not detect a duplicate, the detector SHALL invoke the LLM dedup detector as fallback, unless the dedup strategy is FAST_ONLY.

#### Scenario: LLM fallback for novel candidate
- **WHEN** a candidate "Einstein's theory" does not match any normalized anchor
- **THEN** the fast-path returns `false`
- **AND** (if strategy is FAST_THEN_LLM) the LLM detector is invoked for semantic dedup

#### Scenario: No fallback in FAST_ONLY mode
- **WHEN** dedup strategy is FAST_ONLY and fast-path returns `false`
- **THEN** the detector returns `false` immediately without invoking LLM

### Requirement: Configurable dedup strategy

The duplicate detector behavior SHALL be controlled via configuration property `anchor.dedup-strategy`. The property MUST accept three values:
- `FAST_ONLY`: Use normalized-string detector only
- `LLM_ONLY`: Use LLM detector only (legacy behavior)
- `FAST_THEN_LLM`: Use normalized-string first, LLM as fallback (recommended)

#### Scenario: Configuration selects strategy
- **WHEN** application.yml sets `anchor.dedup-strategy: FAST_THEN_LLM`
- **THEN** DuplicateDetector uses fast-path first
- **AND** invokes LLM only for candidates not matched by fast-path

#### Scenario: Invalid strategy value
- **WHEN** application.yml sets `anchor.dedup-strategy: INVALID`
- **THEN** application startup fails with clear error message

### Requirement: Backward-compatible API contract

The `DuplicateDetector.isDuplicate(contextId, candidateText)` method SHALL maintain its existing signature and return type. Callers MUST observe no change in API behavior or signature.

#### Scenario: API signature unchanged
- **WHEN** calling `isDuplicate(contextId, candidateText)`
- **THEN** return type is `boolean`
- **AND** method accepts the same parameters as before

#### Scenario: Behavior compatible with existing callers
- **WHEN** a proposition is checked for duplication in a context
- **THEN** the result is `true` (duplicate) or `false` (novel)
- **AND** extraction flow handles result identically to previous behavior

## Invariants

- **I1**: Fast-path MUST NOT mutate anchor or candidate text
- **I2**: Fast-path MUST complete in O(n*m) time where n = anchor count, m = average text length
- **I3**: If fast-path returns `true`, LLM MUST NOT be invoked (FAST_ONLY and FAST_THEN_LLM modes)
- **I4**: LLM fallback MUST NOT be invoked if strategy is FAST_ONLY
