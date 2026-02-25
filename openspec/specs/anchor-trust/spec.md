## MODIFIED Requirements

### Requirement: Trust ceiling enforcement on promotion

**Modifies**: `AnchorPromoter` promotion pipeline.

The `TrustScore.authorityCeiling` value SHALL be enforced when determining the initial authority for a newly promoted anchor. If the trust pipeline evaluates a proposition and returns an authority ceiling below the authority that would otherwise be assigned, the lower ceiling takes precedence.

Enforcement occurs in `AnchorPromoter` because the engine's `promote()` method receives only a proposition ID and initial rank -- it does not have access to the `TrustScore`. The promoter, which already calls the trust pipeline, is the natural enforcement point.

#### Scenario: Trust ceiling limits initial authority

- **GIVEN** a proposition with trust score 0.45 and authority ceiling PROVISIONAL
- **WHEN** the promotion pipeline runs and reinforcement count would normally assign UNRELIABLE
- **THEN** the anchor is promoted at PROVISIONAL authority (ceiling enforced)

#### Scenario: Trust ceiling does not limit when above assigned authority

- **GIVEN** a proposition with trust score 0.85 and authority ceiling RELIABLE
- **WHEN** the promotion pipeline runs and initial authority would be PROVISIONAL (default for new anchors)
- **THEN** the anchor is promoted at PROVISIONAL authority (ceiling is above assigned level, no restriction)

### Requirement: Trust ceiling enforcement on re-evaluation

**Modifies**: Trust re-evaluation flow.

When an anchor's trust is re-evaluated (via the triggers defined in "Trust re-evaluation trigger" below), and the new `TrustScore.authorityCeiling` is below the anchor's current authority, the anchor SHALL be demoted to match the ceiling (via `AnchorEngine.demote()` with reason `TRUST_DEGRADATION` — see anchor-lifecycle spec: "DemotionReason enum" and "Demote anchor" for the demotion mechanics).

CANON anchors are exempt from automatic trust-based demotion (invariant A3b). If a CANON anchor's trust ceiling drops below CANON, a pending decanonization request is created via the `CanonizationGate` instead of immediate demotion.

#### Scenario: Trust re-evaluation triggers demotion

- **GIVEN** an anchor at RELIABLE authority with a previously evaluated trust score of 0.80
- **WHEN** trust is re-evaluated and the new score is 0.35 with ceiling PROVISIONAL
- **THEN** the anchor is demoted to PROVISIONAL with reason TRUST_DEGRADATION

#### Scenario: Trust re-evaluation does not demote CANON

- **GIVEN** an anchor at CANON authority
- **WHEN** trust is re-evaluated and the ceiling drops below CANON
- **THEN** the anchor remains at CANON and a pending decanonization request is created via `CanonizationGate`

### Requirement: Trust re-evaluation trigger

Trust re-evaluation SHALL be triggered by the following events:

- **Conflict resolution**: When conflict detection finds contradictions involving an anchor, the anchor's trust is re-evaluated after resolution
- **Reinforcement milestone**: When an anchor's reinforcement count crosses an authority threshold (e.g., 3x for UNRELIABLE, 7x for RELIABLE), trust is re-evaluated to confirm the authority transition
- **Explicit request**: Via `AnchorEngine.reEvaluateTrust(String anchorId)` (new method — see anchor-lifecycle spec: "AnchorEngine facade documentation" for the method contract)

Trust re-evaluation is NOT triggered by:
- Rank decay (decay-based demotion uses rank thresholds directly, not trust scores)
- Every reinforcement (only at milestone thresholds)
- Periodic timers (no background scheduling in current design)

#### Scenario: Conflict triggers trust re-evaluation

- **GIVEN** an anchor "A1" at RELIABLE authority
- **WHEN** conflict detection finds a contradiction involving "A1" and the conflict is resolved with KEEP_EXISTING
- **THEN** anchor "A1"'s trust is re-evaluated and if the new ceiling is below RELIABLE, the anchor is demoted

#### Scenario: Reinforcement milestone triggers trust re-evaluation

- **GIVEN** an anchor "A1" at UNRELIABLE authority with 6 reinforcements
- **WHEN** the 7th reinforcement occurs (RELIABLE threshold)
- **THEN** trust is re-evaluated before upgrading authority to confirm the ceiling allows RELIABLE

### Requirement: DICE importance integration

**Modifies**: `Anchor` record and priority decisions.

The `Anchor` record SHALL include a `diceImportance` field (double, 0.0-1.0) populated from the DICE proposition's `importance` field. This field is used alongside rank for priority decisions:

- A high-importance anchor (importance > 0.7) SHOULD receive a rank boost during priority calculations to reduce its likelihood of eviction
- A low-importance anchor (importance < 0.3) MAY be evicted earlier than its raw rank would suggest

The importance value is read from `PropositionNode` during `AnchorEngine.toAnchor()` conversion. If the DICE proposition does not carry importance data (currently hardcoded to 0.0 in `PropositionView.toDice()`), the default value of 0.0 is used, which has no effect on existing behavior.

#### Scenario: High-importance anchor resists eviction

- **GIVEN** an anchor with rank 300 and diceImportance 0.9
- **WHEN** budget eviction selects candidates by effective priority
- **THEN** the anchor's effective priority is boosted above a rank-300 anchor with importance 0.0

#### Scenario: Default importance has no effect

- **GIVEN** an anchor with diceImportance 0.0 (default)
- **WHEN** priority is calculated
- **THEN** effective priority equals the raw rank (backward-compatible)

### Requirement: DICE decay alignment

**Modifies**: `ExponentialDecayPolicy` and `Anchor` record.

The `Anchor` record SHALL include a `diceDecay` field (double, >= 0.0) populated from the DICE proposition's `decay` field. The `ExponentialDecayPolicy` SHALL use this value to modulate the half-life:

- `diceDecay = 1.0` (standard): Base half-life applies unchanged — this is the **default** for backward compatibility
- `diceDecay = 0.0` (permanent): No rank decay applied (effectively infinite half-life)
- `diceDecay > 1.0` (ephemeral): Faster decay rate (shorter half-life)
- Formula: `effectiveHalfLife = baseHalfLife / max(diceDecay, 0.01)`

#### Scenario: Permanent proposition does not decay

- **GIVEN** an anchor with diceDecay 0.0
- **WHEN** the decay policy runs
- **THEN** the anchor's rank is unchanged

#### Scenario: Ephemeral proposition decays quickly

- **GIVEN** an anchor with diceDecay 1.0 and base half-life of 24 hours
- **WHEN** 24 hours have passed since last reinforcement
- **THEN** the anchor's rank decays by approximately half

#### Scenario: Default decay behavior preserved

- **GIVEN** an anchor with diceDecay 1.0 (default for anchors without DICE decay data)
- **WHEN** the decay policy runs with a 24-hour base half-life
- **THEN** the anchor decays at the same rate as the current implementation (effective half-life = 24 hours)

### Requirement: Domain profile weight validation

**Modifies**: `DomainProfile` construction.

`DomainProfile` signal weights SHALL be validated to sum to 1.0 (within a floating-point tolerance of 0.001). If the weights do not sum to 1.0, construction SHALL throw `IllegalArgumentException`.

A `@VisibleForTesting`-annotated factory method SHALL be provided for test profiles that bypass validation.

**Note**: This validation applies at construction time. At runtime, `TrustEvaluator` redistributes weights from absent signals to present signals. This redistribution does not violate the construction invariant — it is a runtime adjustment that preserves proportional weighting when some signals are unavailable.

#### Scenario: Valid weights accepted

- **GIVEN** domain profile weights that sum to 1.0
- **WHEN** a `DomainProfile` is constructed
- **THEN** construction succeeds

#### Scenario: Invalid weights rejected

- **GIVEN** domain profile weights that sum to 0.8
- **WHEN** a `DomainProfile` is constructed
- **THEN** construction throws `IllegalArgumentException` with a message indicating the weight sum

#### Scenario: Test profiles bypass validation

- **GIVEN** a test needs a profile with arbitrary weights
- **WHEN** the `forTesting()` factory method is used
- **THEN** weight validation is skipped

### Requirement: Anchor record extended with DICE fields

**Modifies**: `Anchor` record.

The `Anchor` record SHALL be extended with two new fields:
- `diceImportance` (double, default 0.0) — from DICE proposition's importance field
- `diceDecay` (double, default 1.0) — from DICE proposition's decay field. Default of 1.0 preserves existing decay behavior.

`AnchorEngine.toAnchor()` SHALL populate these fields from `PropositionNode`. The `Anchor.withoutTrust()` factory method SHALL be updated to accept the new fields (or use sensible defaults when DICE data is unavailable).

#### Scenario: Anchor created with DICE fields

- **GIVEN** a PropositionNode with importance 0.7 and decay 0.3
- **WHEN** `toAnchor()` converts it to an Anchor record
- **THEN** the Anchor has diceImportance=0.7 and diceDecay=0.3

#### Scenario: Anchor created without DICE fields uses defaults

- **GIVEN** a PropositionNode without importance or decay fields set
- **WHEN** `toAnchor()` converts it to an Anchor record
- **THEN** the Anchor has diceImportance=0.0 and diceDecay=1.0 (preserving existing decay behavior)

## Added Requirements (initial-community-review-readiness)

### Requirement: Trust re-evaluation trigger (audit records)

Trust re-evaluation SHALL occur when an anchor is reinforced, when contradictory evidence is introduced, and when trust-profile context changes. Every re-evaluation SHALL persist a trust decision record containing prior trust score, new trust score, trigger reason, and applied profile.

#### Scenario: Reinforcement triggers trust re-evaluation with audit record
- **GIVEN** an existing anchor that receives reinforcement
- **WHEN** reinforcement processing completes
- **THEN** trust SHALL be re-evaluated
- **AND** an audit record SHALL capture prior score, new score, trigger reason, and profile

#### Scenario: Profile change triggers trust re-evaluation
- **GIVEN** an anchor evaluated under profile A
- **WHEN** the profile changes to profile B for the same context
- **THEN** trust SHALL be re-evaluated under profile B

## Invariants (initial-community-review-readiness)

- **ATR1**: Trust decisions used in claim-grade evidence SHALL be auditable and reproducible.
