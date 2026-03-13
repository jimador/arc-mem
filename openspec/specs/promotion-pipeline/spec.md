## ADDED Requirements

### REQ-PRECHECK: Conflict pre-check gate using ConflictIndex

The `UnitPromoter` pipeline SHALL include a conflict pre-check gate positioned after the confidence gate and before the dedup gate. The pre-check gate SHALL use `ConflictIndex.getConflicts()` for O(1) lookup to determine whether an incoming proposition conflicts with any existing memory unit at RELIABLE or CANON authority.

Propositions that conflict with at least one RELIABLE+ memory unit in the index SHALL be filtered from the pipeline before the dedup gate. The pre-check MUST NOT invoke any LLM calls.

#### Scenario: Pre-check filters proposition conflicting with RELIABLE memory unit

- **GIVEN** an active memory unit "A" with authority RELIABLE in the `ConflictIndex`
- **AND** an incoming proposition "P" that has an indexed conflict with memory unit "A"
- **WHEN** "P" enters the promotion pipeline
- **THEN** the pre-check gate SHALL filter "P" before the dedup gate
- **AND** no LLM call SHALL be made for "P" at the dedup or trust gates

#### Scenario: Pre-check filters proposition conflicting with CANON memory unit

- **GIVEN** an active memory unit "A" with authority CANON in the `ConflictIndex`
- **AND** an incoming proposition "P" that has an indexed conflict with memory unit "A"
- **WHEN** "P" enters the promotion pipeline
- **THEN** the pre-check gate SHALL filter "P" before the dedup gate

#### Scenario: Pre-check passes proposition conflicting with PROVISIONAL memory unit

- **GIVEN** an active memory unit "A" with authority PROVISIONAL in the `ConflictIndex`
- **AND** an incoming proposition "P" that has an indexed conflict with memory unit "A"
- **WHEN** "P" enters the promotion pipeline
- **THEN** the pre-check gate SHALL NOT filter "P"
- **AND** "P" SHALL proceed to the dedup gate

#### Scenario: Pre-check passes proposition conflicting with UNRELIABLE memory unit

- **GIVEN** an active memory unit "A" with authority UNRELIABLE in the `ConflictIndex`
- **AND** an incoming proposition "P" that has an indexed conflict with memory unit "A"
- **WHEN** "P" enters the promotion pipeline
- **THEN** the pre-check gate SHALL NOT filter "P"
- **AND** "P" SHALL proceed to the dedup gate

#### Scenario: Pre-check passes proposition with no indexed conflicts

- **GIVEN** an incoming proposition "P" with no entries in the `ConflictIndex`
- **WHEN** "P" enters the promotion pipeline
- **THEN** the pre-check gate SHALL NOT filter "P"
- **AND** "P" SHALL proceed to the dedup gate

---

### REQ-FALLBACK: Graceful fallback when index is unavailable or empty

When the `ConflictIndex` is not available (not injected) or is empty (size == 0), the pre-check gate SHALL be skipped entirely. The pipeline SHALL operate in its original gate order: confidence -> dedup -> conflict -> trust -> promote.

The fallback MUST be transparent -- no behavioral difference from the pre-F06 pipeline when the index is absent.

#### Scenario: Index not injected (Optional.empty)

- **GIVEN** `UnitPromoter` constructed without a `ConflictIndex` (Optional.empty)
- **WHEN** a proposition enters the promotion pipeline
- **THEN** the pre-check gate SHALL be skipped
- **AND** the pipeline SHALL operate as confidence -> dedup -> conflict -> trust -> promote

#### Scenario: Index injected but empty (cold start)

- **GIVEN** a `ConflictIndex` with size == 0
- **WHEN** a proposition enters the promotion pipeline
- **THEN** the pre-check gate SHALL be skipped
- **AND** the pipeline SHALL operate in its original gate order

#### Scenario: Index becomes populated after cold start

- **GIVEN** a `ConflictIndex` that was initially empty
- **WHEN** conflicts are recorded in the index during a promotion cycle
- **AND** a new proposition enters the pipeline in a subsequent cycle
- **THEN** the pre-check gate SHALL be active and use the populated index

---

### REQ-TRANSPARENT: Pre-check does not change promotion outcomes for non-conflicting propositions

The conflict pre-check gate SHALL NOT change the set of propositions that ultimately get promoted for candidates that do not conflict with RELIABLE+ memory units. The optimization MUST be transparent to callers for non-conflicting inputs.

Specifically: for any proposition "P" that would have been promoted by the pre-F06 pipeline, if "P" does not conflict with any RELIABLE+ memory unit in the index, "P" SHALL still be promoted by the post-F06 pipeline.

#### Scenario: Non-conflicting proposition promoted identically

- **GIVEN** a proposition "P" with no conflicts against any active memory unit
- **WHEN** "P" is processed through the post-F06 pipeline
- **THEN** "P" SHALL be promoted with the same rank and authority ceiling as the pre-F06 pipeline would have assigned

#### Scenario: Proposition conflicting with PROVISIONAL-only memory units processed identically

- **GIVEN** a proposition "P" that conflicts only with PROVISIONAL memory units (no RELIABLE+ conflicts)
- **WHEN** "P" is processed through the post-F06 pipeline
- **THEN** "P" SHALL reach the full conflict gate and be processed identically to the pre-F06 pipeline

---

### REQ-LOGGING: Structured logging for pre-check rejections

Pre-check rejections SHALL be logged at INFO level with structured fields for auditability. Each rejection log entry MUST include:

1. The rejected proposition ID
2. The conflicting memory unit ID from the index entry
3. The conflicting memory unit's authority level
4. The conflict confidence from the index entry

Pre-check pass-through (no conflicts found) SHALL be logged at DEBUG level.

The promotion funnel summary log MUST include a `post-precheck` count alongside existing gate counts.

#### Scenario: Rejection logged with memory unit details

- **GIVEN** a proposition "P" filtered by the pre-check gate due to a conflict with memory unit "A" (RELIABLE, confidence 0.9)
- **WHEN** the pre-check filters "P"
- **THEN** an INFO log entry SHALL be emitted containing: proposition ID, memory unit "A" ID, authority RELIABLE, confidence 0.9

#### Scenario: Funnel summary includes pre-check count

- **GIVEN** a promotion cycle with 10 candidates, 8 post-confidence, 6 post-precheck
- **WHEN** the promotion funnel summary is logged
- **THEN** the log entry SHALL include `post-precheck=6` in the funnel summary

---

### REQ-THRESHOLD: Authority threshold is RELIABLE

The pre-check gate SHALL only filter propositions that conflict with memory units at RELIABLE or CANON authority. Conflicts with PROVISIONAL or UNRELIABLE memory units SHALL NOT trigger pre-check filtering.

The threshold MUST be hardcoded to RELIABLE (not configurable). PROVISIONAL and UNRELIABLE memory units are not stable enough to gate new propositions; filtering against low-authority memory units would be overly conservative and could starve the memory unit pool.

#### Scenario: RELIABLE memory unit triggers pre-check filter

- **GIVEN** a `ConflictEntry` with authority RELIABLE
- **WHEN** the pre-check evaluates the entry
- **THEN** the entry SHALL trigger filtering of the conflicting proposition

#### Scenario: CANON memory unit triggers pre-check filter

- **GIVEN** a `ConflictEntry` with authority CANON
- **WHEN** the pre-check evaluates the entry
- **THEN** the entry SHALL trigger filtering of the conflicting proposition

#### Scenario: UNRELIABLE memory unit does not trigger pre-check filter

- **GIVEN** a `ConflictEntry` with authority UNRELIABLE
- **WHEN** the pre-check evaluates the entry
- **THEN** the entry SHALL NOT trigger filtering

#### Scenario: PROVISIONAL memory unit does not trigger pre-check filter

- **GIVEN** a `ConflictEntry` with authority PROVISIONAL
- **WHEN** the pre-check evaluates the entry
- **THEN** the entry SHALL NOT trigger filtering

---

### REQ-BATCH: Pre-check applies to both sequential and batch paths

The conflict pre-check gate SHALL apply to both `evaluateAndPromoteWithOutcome` (sequential) and `batchEvaluateAndPromoteWithOutcome` (batch) code paths. The pre-check behavior MUST be identical in both paths: same authority threshold, same filtering logic, same logging.

#### Scenario: Sequential path includes pre-check

- **GIVEN** a proposition processed via `evaluateAndPromoteWithOutcome`
- **WHEN** the proposition conflicts with a RELIABLE+ memory unit in the index
- **THEN** the proposition SHALL be filtered at the pre-check gate before dedup

#### Scenario: Batch path includes pre-check

- **GIVEN** propositions processed via `batchEvaluateAndPromoteWithOutcome`
- **WHEN** a proposition in the batch conflicts with a RELIABLE+ memory unit in the index
- **THEN** the proposition SHALL be filtered at the pre-check gate before the batch dedup call

---

### REQ-INJECTION: ConflictIndex injected as Optional

`UnitPromoter` SHALL accept `ConflictIndex` as an `Optional<ConflictIndex>` constructor parameter. This ensures backward compatibility: when no `ConflictIndex` bean is available (F05 not implemented or strategy is not INDEXED), the promoter operates without the pre-check gate.

The `Optional` injection pattern MUST be used instead of `@Nullable` to align with the project's preference for explicit optionality over null semantics.

#### Scenario: Constructor accepts Optional.empty

- **GIVEN** no `ConflictIndex` bean in the application context
- **WHEN** `UnitPromoter` is constructed
- **THEN** construction SHALL succeed with an empty Optional

#### Scenario: Constructor accepts populated Optional

- **GIVEN** a `ConflictIndex` bean in the application context
- **WHEN** `UnitPromoter` is constructed
- **THEN** construction SHALL succeed with the index wrapped in Optional

---

## Invariants

- **PP1**: The pre-check gate MUST NOT invoke LLM calls. It uses only `ConflictIndex` O(1) lookups.
- **PP2**: The pre-check gate MUST NOT modify memory unit state (rank, authority, pinned status). It is read-only.
- **PP3**: The pre-check authority threshold MUST be RELIABLE (level >= 2). PROVISIONAL and UNRELIABLE conflicts do not trigger pre-check filtering.
- **PP4**: When the `ConflictIndex` is absent or empty, the pipeline MUST behave identically to the pre-F06 pipeline. The pre-check is additive, not substitutive.
- **PP5**: The full LLM conflict detection gate (gate 3) MUST be retained. The pre-check is a fast filter, not a replacement for full conflict detection.
- **PP6**: All ARC-Mem invariants (A1-A4 per Article V of the constitution) MUST be preserved. The pre-check filters candidates; it does not modify ranks, authorities, promotion rules, or budget enforcement.
