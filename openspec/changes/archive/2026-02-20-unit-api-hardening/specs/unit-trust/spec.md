## MODIFIED Requirements

### Requirement: Trust ceiling enforcement on promotion

**Modifies**: `UnitPromoter` promotion pipeline.

The `TrustScore.authorityCeiling` value SHALL be enforced when determining the initial authority for a newly promoted context unit. If the trust pipeline evaluates a proposition and returns an authority ceiling below the authority that would otherwise be assigned, the lower ceiling takes precedence.

Enforcement occurs in `UnitPromoter` because the engine's `promote()` method receives only a proposition ID and initial rank -- it does not have access to the `TrustScore`. The promoter, which already calls the trust pipeline, is the natural enforcement point.

#### Scenario: Trust ceiling limits initial authority

- **GIVEN** a proposition with trust score 0.45 and authority ceiling PROVISIONAL
- **WHEN** the promotion pipeline runs and reinforcement count would normally assign UNRELIABLE
- **THEN** the context unit is promoted at PROVISIONAL authority (ceiling enforced)

#### Scenario: Trust ceiling does not limit when above assigned authority

- **GIVEN** a proposition with trust score 0.85 and authority ceiling RELIABLE
- **WHEN** the promotion pipeline runs and initial authority would be PROVISIONAL (default for new context units)
- **THEN** the context unit is promoted at PROVISIONAL authority (ceiling is above assigned level, no restriction)

### Requirement: Trust ceiling enforcement on re-evaluation

**Modifies**: Trust re-evaluation flow.

When an context unit's trust is re-evaluated (via the triggers defined in "Trust re-evaluation trigger" below), and the new `TrustScore.authorityCeiling` is below the context unit's current authority, the context unit SHALL be demoted to match the ceiling (via `ArcMemEngine.demote()` with reason `TRUST_DEGRADATION` — see unit-lifecycle spec: "DemotionReason enum" and "Demote context unit" for the demotion mechanics).

CANON context units are exempt from automatic trust-based demotion (invariant A3b). If a CANON context unit's trust ceiling drops below CANON, a pending decanonization request is created via the `CanonizationGate` instead of immediate demotion.

#### Scenario: Trust re-evaluation triggers demotion

- **GIVEN** an context unit at RELIABLE authority with a previously evaluated trust score of 0.80
- **WHEN** trust is re-evaluated and the new score is 0.35 with ceiling PROVISIONAL
- **THEN** the context unit is demoted to PROVISIONAL with reason TRUST_DEGRADATION

#### Scenario: Trust re-evaluation does not demote CANON

- **GIVEN** an context unit at CANON authority
- **WHEN** trust is re-evaluated and the ceiling drops below CANON
- **THEN** the context unit remains at CANON and a pending decanonization request is created via `CanonizationGate`

### Requirement: Trust re-evaluation trigger

Trust re-evaluation SHALL be triggered by the following events:

- **Conflict resolution**: When conflict detection finds contradictions involving an context unit, the context unit's trust is re-evaluated after resolution
- **Reinforcement milestone**: When an context unit's reinforcement count crosses an authority threshold (e.g., 3x for UNRELIABLE, 7x for RELIABLE), trust is re-evaluated to confirm the authority transition
- **Explicit request**: Via `ArcMemEngine.reEvaluateTrust(String unitId)` (new method — see unit-lifecycle spec: "ArcMemEngine facade documentation" for the method contract)

Trust re-evaluation is NOT triggered by:
- Rank decay (decay-based demotion uses rank thresholds directly, not trust scores)
- Every reinforcement (only at milestone thresholds)
- Periodic timers (no background scheduling in current design)

#### Scenario: Conflict triggers trust re-evaluation

- **GIVEN** an context unit "A1" at RELIABLE authority
- **WHEN** conflict detection finds a contradiction involving "A1" and the conflict is resolved with KEEP_EXISTING
- **THEN** context unit "A1"'s trust is re-evaluated and if the new ceiling is below RELIABLE, the context unit is demoted

#### Scenario: Reinforcement milestone triggers trust re-evaluation

- **GIVEN** an context unit "A1" at UNRELIABLE authority with 6 reinforcements
- **WHEN** the 7th reinforcement occurs (RELIABLE threshold)
- **THEN** trust is re-evaluated before upgrading authority to confirm the ceiling allows RELIABLE

### Requirement: DICE importance integration

**Modifies**: `Context Unit` record and priority decisions.

The `Context Unit` record SHALL include a `diceImportance` field (double, 0.0-1.0) populated from the DICE proposition's `importance` field. This field is used alongside rank for priority decisions:

- A high-importance context unit (importance > 0.7) SHOULD receive a rank boost during priority calculations to reduce its likelihood of eviction
- A low-importance context unit (importance < 0.3) MAY be evicted earlier than its raw rank would suggest

The importance value is read from `PropositionNode` during `ArcMemEngine.toUnit()` conversion. If the DICE proposition does not carry importance data (currently hardcoded to 0.0 in `PropositionView.toDice()`), the default value of 0.0 is used, which has no effect on existing behavior.

#### Scenario: High-importance context unit resists eviction

- **GIVEN** an context unit with rank 300 and diceImportance 0.9
- **WHEN** budget eviction selects candidates by effective priority
- **THEN** the context unit's effective priority is boosted above a rank-300 context unit with importance 0.0

#### Scenario: Default importance has no effect

- **GIVEN** an context unit with diceImportance 0.0 (default)
- **WHEN** priority is calculated
- **THEN** effective priority equals the raw rank (backward-compatible)

### Requirement: DICE decay alignment

**Modifies**: `ExponentialDecayPolicy` and `Context Unit` record.

The `Context Unit` record SHALL include a `diceDecay` field (double, >= 0.0) populated from the DICE proposition's `decay` field. The `ExponentialDecayPolicy` SHALL use this value to modulate the half-life:

- `diceDecay = 1.0` (standard): Base half-life applies unchanged — this is the **default** for backward compatibility
- `diceDecay = 0.0` (permanent): No rank decay applied (effectively infinite half-life)
- `diceDecay > 1.0` (ephemeral): Faster decay rate (shorter half-life)
- Formula: `effectiveHalfLife = baseHalfLife / max(diceDecay, 0.01)`

#### Scenario: Permanent proposition does not decay

- **GIVEN** an context unit with diceDecay 0.0
- **WHEN** the decay policy runs
- **THEN** the context unit's rank is unchanged

#### Scenario: Ephemeral proposition decays quickly

- **GIVEN** an context unit with diceDecay 1.0 and base half-life of 24 hours
- **WHEN** 24 hours have passed since last reinforcement
- **THEN** the context unit's rank decays by approximately half

#### Scenario: Default decay behavior preserved

- **GIVEN** an context unit with diceDecay 1.0 (default for context units without DICE decay data)
- **WHEN** the decay policy runs with a 24-hour base half-life
- **THEN** the context unit decays at the same rate as the current implementation (effective half-life = 24 hours)

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

### Requirement: Context Unit record extended with DICE fields

**Modifies**: `Context Unit` record.

The `Context Unit` record SHALL be extended with two new fields:
- `diceImportance` (double, default 0.0) — from DICE proposition's importance field
- `diceDecay` (double, default 1.0) — from DICE proposition's decay field. Default of 1.0 preserves existing decay behavior.

`ArcMemEngine.toUnit()` SHALL populate these fields from `PropositionNode`. The `Context Unit.withoutTrust()` factory method SHALL be updated to accept the new fields (or use sensible defaults when DICE data is unavailable).

#### Scenario: Context Unit created with DICE fields

- **GIVEN** a PropositionNode with importance 0.7 and decay 0.3
- **WHEN** `toUnit()` converts it to an Context Unit record
- **THEN** the Context Unit has diceImportance=0.7 and diceDecay=0.3

#### Scenario: Context Unit created without DICE fields uses defaults

- **GIVEN** a PropositionNode without importance or decay fields set
- **WHEN** `toUnit()` converts it to an Context Unit record
- **THEN** the Context Unit has diceImportance=0.0 and diceDecay=1.0 (preserving existing decay behavior)
