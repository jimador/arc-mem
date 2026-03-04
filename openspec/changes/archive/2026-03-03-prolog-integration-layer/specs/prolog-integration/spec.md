## ADDED Requirements

### Requirement: AnchorPrologProjector projection foundation

The system SHALL provide an `AnchorPrologProjector` service in `dev.dunnam.diceanchors.anchor` that projects a list of active anchors to a Prolog theory string consumable by DICE `PrologEngine`.

The projector MUST produce two fact families:

1. **Metadata facts** (`anchor/5`): `anchor(Id, AuthorityOrdinal, Rank, Pinned, ReinforcementCount)` where AuthorityOrdinal is 0=PROVISIONAL, 1=UNRELIABLE, 2=RELIABLE, 3=CANON, and Pinned is `true` or `false`.
2. **Entity triple facts** (`claim/4`): `claim(AnchorId, Subject, Predicate, Object)` extracted from anchor text via heuristic SVO decomposition.

The projector MUST NOT invoke LLM calls during projection. All text decomposition SHALL use heuristic extraction only.

The projector SHALL load Prolog rules from classpath resources via DICE `PrologRuleLoader` and concatenate them with projected facts to form the complete theory string.

The projector SHALL return a `PrologEngine` instance created via `PrologEngine.Companion.fromTheory()` with the assembled theory.

#### Scenario: Project anchors to Prolog facts

- **GIVEN** a list of 3 active anchors with varying authority levels
- **WHEN** `AnchorPrologProjector.project(anchors)` is called
- **THEN** the returned `PrologEngine` SHALL contain 3 `anchor/5` facts
- **AND** each anchor's text SHALL produce at least one `claim/4` fact
- **AND** no LLM calls SHALL be made

#### Scenario: Empty anchor list produces valid engine

- **GIVEN** an empty list of anchors
- **WHEN** `AnchorPrologProjector.project(anchors)` is called
- **THEN** the returned `PrologEngine` SHALL contain zero `anchor/5` facts
- **AND** the engine SHALL still load the base rule set

#### Scenario: Heuristic SVO decomposition

- **GIVEN** an anchor with text "Baron Krell is alive"
- **WHEN** projected via `AnchorPrologProjector`
- **THEN** a `claim/4` fact SHALL be produced with subject containing `baron_krell`, predicate containing `is`, and object containing `alive`

---

### Requirement: Prolog contradiction rules

The system SHALL define Prolog rules in a classpath resource (`prolog/anchor-rules.pl`) for detecting contradictions among projected anchor facts.

Rules MUST be layered:
1. **Negation layer**: `contradicts(A, B)` when two claims share the same subject and predicate but have negated objects. Negation pairs (alive/dead, present/absent, true/false, open/closed) MUST be defined as Prolog facts, not hardcoded in Java.
2. **Incompatible states layer**: `contradicts(A, B)` when two claims share the same subject and predicate but have incompatible objects (e.g., different locations for the same entity).

Rules MUST be symmetric: if `contradicts(A, B)` holds, `contradicts(B, A)` MUST also hold.

Rules MUST exclude self-contradiction: `contradicts(A, A)` MUST NOT be derivable.

#### Scenario: Negation contradiction detected

- **GIVEN** anchor A with claim `claim(a, baron_krell, is, alive)` and anchor B with claim `claim(b, baron_krell, is, dead)`
- **WHEN** the Prolog engine queries `contradicts(a, b)`
- **THEN** the query SHALL succeed

#### Scenario: No contradiction for compatible claims

- **GIVEN** anchor A with claim `claim(a, baron_krell, is, alive)` and anchor B with claim `claim(b, baron_krell, leads, siege)`
- **WHEN** the Prolog engine queries `contradicts(a, b)`
- **THEN** the query SHALL fail (no contradiction)

#### Scenario: Self-contradiction excluded

- **GIVEN** anchor A with claim `claim(a, baron_krell, is, alive)`
- **WHEN** the Prolog engine queries `contradicts(a, a)`
- **THEN** the query SHALL fail

---

### Requirement: LOGICAL conflict detection strategy

`CompositeConflictDetector` SHALL delegate the `LOGICAL` strategy branch to a `PrologConflictDetector` instead of throwing `UnsupportedOperationException`.

`PrologConflictDetector` MUST implement `ConflictDetector` with the following behavior:

1. Project all existing anchors plus a synthetic anchor for the incoming text via `AnchorPrologProjector`
2. Query the `PrologEngine` for `contradicts(IncomingId, X)` to find conflicts
3. Convert query results to `ConflictDetector.Conflict` records with `DetectionQuality.FULL` and `ConflictType.CONTRADICTION`
4. Return an empty list on any Prolog failure (never throw)
5. Log projection and query timing at INFO level

`ConflictStrategy` SHALL gain a `LOGICAL` value. `AnchorConfiguration.conflictDetector()` SHALL wire a `CompositeConflictDetector` with `ConflictDetectionStrategy.LOGICAL` when `LOGICAL` is selected.

All Prolog queries MUST complete in < 100ms for anchor sets up to budget max (default 20).

#### Scenario: LOGICAL strategy detects negation contradiction

- **GIVEN** `ConflictDetectionStrategy.LOGICAL` is active
- **AND** an existing anchor states "Baron Krell is alive"
- **WHEN** incoming text "Baron Krell is dead" is checked for conflicts
- **THEN** a `Conflict` record SHALL be returned with the existing anchor
- **AND** no LLM call SHALL be made

#### Scenario: LOGICAL strategy returns empty on no contradiction

- **GIVEN** `ConflictDetectionStrategy.LOGICAL` is active
- **AND** existing anchors contain no contradictions with the incoming text
- **WHEN** conflict detection is performed
- **THEN** the result SHALL be an empty list

#### Scenario: LOGICAL strategy handles projection failure gracefully

- **GIVEN** `ConflictDetectionStrategy.LOGICAL` is active
- **WHEN** the Prolog projection or query fails
- **THEN** the result SHALL be an empty list (never throw)
- **AND** the failure SHALL be logged at WARN level

#### Scenario: Query latency within performance gate

- **GIVEN** 20 active anchors
- **WHEN** LOGICAL conflict detection is performed
- **THEN** the total query time SHALL be < 100ms

---

### Requirement: Prolog audit pre-filter

The system SHALL provide a `PrologAuditPreFilter` service that pre-filters active anchors during the proactive maintenance audit step.

When enabled (`dice-anchors.maintenance.proactive.prologPreFilterEnabled=true`), the pre-filter SHALL:

1. Project all active anchors via `AnchorPrologProjector`
2. Query for all contradiction pairs: `contradicts(A, B)` via `queryAll()`
3. Return the set of anchor IDs appearing in any contradiction pair
4. Flagged anchors SHALL receive an audit score of 0.0, skipping LLM evaluation

The pre-filter MUST be toggleable via `DiceAnchorsProperties.ProactiveConfig`. Default: `false` (disabled).

The pre-filter MUST complete in < 100ms for anchor sets up to budget max (default 20).

#### Scenario: Pre-filter flags contradicting anchors

- **GIVEN** 10 active anchors with one contradicting pair
- **AND** `prologPreFilterEnabled=true`
- **WHEN** the audit step executes
- **THEN** the two contradicting anchors SHALL be flagged with audit score 0.0
- **AND** the remaining 8 anchors SHALL proceed to normal heuristic scoring

#### Scenario: Pre-filter disabled by default

- **GIVEN** default configuration
- **WHEN** the audit step executes
- **THEN** the Prolog pre-filter SHALL NOT be invoked
- **AND** all anchors SHALL proceed to normal heuristic scoring

#### Scenario: Pre-filter failure is non-fatal

- **GIVEN** `prologPreFilterEnabled=true`
- **WHEN** the Prolog projection or query fails
- **THEN** the audit step SHALL proceed with normal heuristic scoring for all anchors
- **AND** the failure SHALL be logged at WARN level

---

### Requirement: Prolog invariant enforcer

The system SHALL provide a `PrologInvariantEnforcer` implementing `ComplianceEnforcer` in `dev.dunnam.diceanchors.assembly`.

The enforcer SHALL:

1. Filter active anchors by the provided `CompliancePolicy` authority tiers
2. Project enforced anchors and the response text via `AnchorPrologProjector`
3. Query the Prolog engine for contradictions between the response and enforced anchors
4. Convert Prolog contradiction results to `ComplianceViolation` records
5. Return a `ComplianceResult` with violations and appropriate `ComplianceAction`

`ComplianceAction` mapping:
- No violations: `ACCEPT`
- Violations against CANON or RELIABLE anchors: `REJECT`
- Violations against UNRELIABLE or PROVISIONAL anchors: `RETRY`

The enforcer MUST be thread-safe. The enforcer MUST return `ComplianceResult.compliant()` on any Prolog failure (fail-open).

#### Scenario: Enforcer detects contradiction with CANON anchor

- **GIVEN** a CANON anchor "Baron Krell is alive"
- **AND** response text "Baron Krell is dead"
- **WHEN** `PrologInvariantEnforcer.enforce()` is called
- **THEN** the result SHALL contain a `ComplianceViolation` for the CANON anchor
- **AND** `suggestedAction` SHALL be `REJECT`

#### Scenario: Enforcer returns compliant for non-contradicting response

- **GIVEN** enforced anchors with no contradiction to the response
- **WHEN** `PrologInvariantEnforcer.enforce()` is called
- **THEN** the result SHALL have `compliant=true` and `suggestedAction=ACCEPT`

#### Scenario: Enforcer respects CompliancePolicy filter

- **GIVEN** `CompliancePolicy.canonOnly()` and both CANON and PROVISIONAL anchors
- **WHEN** enforcement is performed
- **THEN** only CANON anchors SHALL be checked
- **AND** PROVISIONAL anchors SHALL be excluded from Prolog projection

---

### Requirement: A/B strategy selection

`ConflictStrategy` SHALL include a `LOGICAL` value enabling selection of Prolog-backed conflict detection via configuration.

When `dice-anchors.conflict-detection.strategy=LOGICAL`, `AnchorConfiguration` SHALL wire a `CompositeConflictDetector` using `ConflictDetectionStrategy.LOGICAL` backed by `PrologConflictDetector`.

Scenario YAML MAY override the conflict detection strategy per simulation run to support A/B comparison of Prolog vs. non-Prolog approaches.

#### Scenario: LOGICAL strategy selectable via configuration

- **GIVEN** `dice-anchors.conflict-detection.strategy=LOGICAL`
- **WHEN** the application starts
- **THEN** `CompositeConflictDetector` SHALL use the `LOGICAL` strategy
- **AND** conflict detection SHALL delegate to `PrologConflictDetector`

---

## Invariants

- **PI1**: `AnchorPrologProjector` SHALL NOT invoke LLM calls. All projection is heuristic/deterministic.
- **PI2**: All Prolog implementations MUST return safe defaults on failure (empty list for detectors, compliant result for enforcers). MUST NOT throw.
- **PI3**: Prolog is a pre-filter, not a replacement. The LLM fallback path MUST remain available for all three integration points.
- **PI4**: `PrologEngine` instances are per-invocation. No shared mutable Prolog state across calls.
- **PI5**: Prolog rules MUST be defined in resource files, not hardcoded in Java. Java code MUST NOT contain Prolog clause strings beyond the dynamically generated fact assertions.
- **PI6**: All Prolog queries MUST complete in < 100ms for anchor sets up to budget max (default 20).
- **PI7**: Anchor state (rank, authority, pinned) MUST NOT be modified by any Prolog operation. Prolog is read-only with respect to anchor state.
