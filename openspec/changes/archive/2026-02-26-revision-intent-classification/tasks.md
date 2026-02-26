## 1. ConflictType enum and Conflict record extension

- [x] 1.1 Create `ConflictType` enum (`REVISION`, `CONTRADICTION`, `WORLD_PROGRESSION`) in `anchor/` package
- [x] 1.2 Add `@Nullable ConflictType conflictType` field to `ConflictDetector.Conflict` record; preserve existing 4-arg and 5-arg constructors with null default; add 6-arg constructor
- [x] 1.3 Write unit tests for `Conflict` record constructor backward compatibility and field access

## 2. Prompt template extension

- [x] 2.1 Extend `dice/conflict-detection.jinja` with `conflictType`, `reasoning` fields, class definitions, 6 few-shot examples, untrusted-data framing, and `anchor_authority` template variable
- [x] 2.2 Extend `dice/batch-conflict-detection.jinja` with per-anchor-match `conflictType` and `reasoning` in the result schema
- [x] 2.3 Extend `BatchConflictResult` record to parse per-anchor-match `conflictType` and `reasoning`

## 3. LlmConflictDetector parse and classification

- [x] 3.1 Update `LlmConflictDetector.evaluatePair()` to pass `anchor.authority()` as a template variable
- [x] 3.2 Update `LlmConflictDetector.parseResponse()` to read `conflictType` and `reasoning` from JSON; default absent/null to `CONTRADICTION`
- [x] 3.3 Update `LlmConflictDetector.parseBatchConflictResponse()` to read per-anchor-match `conflictType`
- [x] 3.4 Write unit tests for `parseResponse()` with all ConflictType values, missing field, and malformed JSON
- [x] 3.5 Write unit tests for batch parse with mixed conflict types and fallback

## 4. RevisionAwareConflictResolver

- [x] 4.1 Create `RevisionAwareConflictResolver` implementing `ConflictResolver` with ConflictType dispatch and authority-gated eligibility logic
- [x] 4.2 Write unit tests: REVISION accepted for PROVISIONAL, REVISION accepted for UNRELIABLE (above threshold), REVISION rejected for CANON, REVISION rejected for RELIABLE (default config), REVISION accepted for RELIABLE (revisable=true + confidence), WORLD_PROGRESSION → COEXIST, null conflictType → delegate, below-threshold REVISION → delegate
- [x] 4.3 Write unit tests: OTEL span attributes set for conflict type, reasoning, and eligibility decision

## 5. Configuration and wiring

- [x] 5.1 Add `RevisionConfig` record to `DiceAnchorsProperties` with `enabled`, `reliableRevisable`, `confidenceThreshold` fields
- [x] 5.2 Wire `RevisionAwareConflictResolver` as `@Primary` `ConflictResolver` bean conditional on `anchor.revision.enabled = true`; fall back to `AuthorityConflictResolver` when disabled
- [x] 5.3 Write unit tests for configuration defaults and conditional bean selection

## 6. SupersessionReason extension

- [x] 6.1 Add `USER_REVISION` to `SupersessionReason` enum
- [x] 6.2 Add `REVISION` to `ArchiveReason` enum and map it in `SupersessionReason.fromArchiveReason()`
- [x] 6.3 Write unit tests for `fromArchiveReason(ArchiveReason.REVISION)` mapping

## 7. Observability

- [x] 7.1 Add OTEL span attributes (`conflict.type`, `conflict.type.reasoning`) in `RevisionAwareConflictResolver`
- [x] 7.2 Create `TrustAuditRecord` with `triggerReason = "revision"` when revision supersession is triggered
- [x] 7.3 Write unit tests for observability attribute presence

## 8. Integration verification

- [x] 8.1 Manual verification with the R00 failure scenario (Anakin wizard → bard revision)
- [x] 8.2 Verify existing conflict detection tests still pass (backward compatibility)
