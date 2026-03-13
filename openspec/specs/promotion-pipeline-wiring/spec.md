# Promotion Pipeline Wiring Specification

## ADDED Requirements

### Requirement: Duplicate detection gate in promotion pipeline

The promotion pipeline SHALL invoke `DuplicateDetector.isDuplicate(contextId, candidateText)` for each proposition that passes the confidence gate. Propositions identified as duplicates MUST NOT be promoted.

#### Scenario: Duplicate proposition rejected
- **WHEN** a proposition with text matching an existing memory unit passes confidence gate
- **THEN** DuplicateDetector identifies it as duplicate
- **AND** the proposition is not promoted
- **AND** funnel log shows it was filtered at dedup stage

#### Scenario: Novel proposition proceeds
- **WHEN** a proposition passes confidence gate and is not a duplicate
- **THEN** it proceeds to conflict detection stage

### Requirement: Conflict resolution in promotion pipeline

The promotion pipeline SHALL invoke `ConflictResolver.resolve(conflict)` for each detected conflict instead of unconditionally skipping. The resolution decision (KEEP, REPLACE, COEXIST) SHALL determine whether the incoming proposition is promoted.

#### Scenario: KEEP resolution skips incoming
- **WHEN** a conflict is detected and resolver returns KEEP
- **THEN** the incoming proposition is not promoted
- **AND** the existing memory unit remains unchanged

#### Scenario: REPLACE resolution archives existing and promotes incoming
- **WHEN** a conflict is detected and resolver returns REPLACE
- **THEN** the existing conflicting memory unit is archived (rank set to 0, status ARCHIVED)
- **AND** the incoming proposition is promoted as a new memory unit

#### Scenario: COEXIST resolution allows both
- **WHEN** a conflict is detected and resolver returns COEXIST
- **THEN** the incoming proposition proceeds to trust evaluation
- **AND** the existing memory unit remains unchanged

#### Scenario: Multiple conflicts with mixed resolutions
- **WHEN** an incoming proposition conflicts with multiple memory units
- **AND** any resolution returns KEEP
- **THEN** the incoming proposition is not promoted (KEEP takes precedence)

### Requirement: Promotion pipeline ordering

The promotion pipeline SHALL execute gates in this order:
1. Confidence gate (< threshold → skip)
2. Duplicate detection (isDuplicate → skip)
3. Conflict detection + resolution (KEEP → skip, REPLACE → archive existing, COEXIST → continue)
4. Trust evaluation (ARCHIVE/REVIEW → skip, AUTO_PROMOTE → continue)
5. Promotion (engine.promote)

#### Scenario: Pipeline executes in order
- **WHEN** a proposition enters the promotion pipeline
- **THEN** gates execute in the specified order
- **AND** each gate short-circuits on rejection (subsequent gates not called)

### Requirement: Promotion funnel logging

The promotion pipeline SHALL log aggregate counts at each gate after processing a batch of propositions. The log MUST include: total extracted, passed confidence, passed dedup, passed conflict, passed trust, promoted.

#### Scenario: Funnel log emitted after batch
- **WHEN** a batch of propositions is processed through the pipeline
- **THEN** a single log line summarizes counts at each stage
- **AND** the format includes all gate names and counts

## Invariants

- **I1**: DuplicateDetector MUST be invoked before conflict detection (avoids wasting LLM calls on duplicates)
- **I2**: ConflictResolver decisions MUST be acted upon — KEEP/REPLACE/COEXIST MUST produce the specified side effects
- **I3**: REPLACE MUST archive existing memory unit before promoting incoming (no brief period of contradictory memory units)
- **I4**: Authority-upgrade-only invariant MUST be preserved — REPLACE does not downgrade authority of other memory units
- **I5**: Funnel logging MUST NOT affect promotion outcomes (observability only)
