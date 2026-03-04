# Constraint-Aware Decoding Specification

## ADDED Requirements

### Requirement: Anchor constraint index construction

The system SHALL provide an `AnchorConstraintIndex` that translates active anchor propositions into token-level constraint sets. The index MUST track translation coverage — the fraction of anchor semantics expressible as token constraints.

#### Scenario: Entity name extraction from anchor text
- **WHEN** `AnchorConstraintIndex.build()` receives an anchor with text "Baron Krell is a four-armed sahuagin mutant"
- **THEN** the resulting `AnchorConstraint` contains boost tokens including "Baron", "Krell", "sahuagin"
- **AND** translation coverage is > 0.0 (partial, not full semantic capture)

#### Scenario: Authority-tiered constraint classification
- **WHEN** `AnchorConstraintIndex.build()` receives anchors at different authority levels
- **THEN** CANON anchors produce `AnchorConstraint` entries
- **AND** RELIABLE anchors produce `AnchorConstraint` entries
- **AND** PROVISIONAL and UNRELIABLE anchors produce no constraint entries

#### Scenario: Empty anchor list
- **WHEN** `AnchorConstraintIndex.build()` receives an empty list
- **THEN** the index has zero constraints and total coverage of 0.0

#### Scenario: Coverage tracking
- **WHEN** the index is built from a mix of CANON and PROVISIONAL anchors
- **THEN** `getTotalCoverage()` reflects only the constrainable anchors (CANON/RELIABLE)
- **AND** per-anchor coverage values are in [0.0, 1.0]

### Requirement: Logit bias enforcement

The system SHALL implement `LogitBiasEnforcer` as a `ComplianceEnforcer` (F03 interface) that translates CANON and RELIABLE anchor constraints into OpenAI-compatible logit bias parameters.

#### Scenario: CANON anchor produces logit bias map
- **WHEN** `LogitBiasEnforcer.enforce()` receives a context with CANON anchors
- **THEN** the result includes a `LogitBiasMap` with token-to-bias mappings
- **AND** bias values for CANON constraints are at maximum strength (100)
- **AND** the `ComplianceResult` is produced via the F03 interface

#### Scenario: RELIABLE anchor produces softer bias
- **WHEN** `LogitBiasEnforcer.enforce()` receives a context with RELIABLE anchors
- **THEN** bias values for RELIABLE constraints are at moderate strength (< 100)

#### Scenario: OpenAI 300-token limit respected
- **WHEN** anchor constraints translate to more than 300 tokens
- **THEN** the enforcer prioritizes CANON constraints over RELIABLE constraints
- **AND** truncates at the 300-token limit
- **AND** logs a warning with the overflow count

#### Scenario: Unsupported model graceful degradation
- **WHEN** `LogitBiasEnforcer` detects the target model does not support logit bias
- **THEN** the enforcer falls back to producing a compliant result with zero bias
- **AND** logs a warning identifying the unsupported model

#### Scenario: ComplianceResult metadata
- **WHEN** logit bias enforcement completes
- **THEN** the `ComplianceResult` includes constraint count, coverage, and strategy metadata

### Requirement: Constrained decoding enforcer interface

The system SHALL define a `ConstrainedDecodingEnforcer` interface extending `ComplianceEnforcer` for future token-level constrained decoding. Implementation is deferred; only the interface contract is required.

#### Scenario: Interface compiles with clear contract
- **WHEN** `ConstrainedDecodingEnforcer` is defined
- **THEN** it extends `ComplianceEnforcer`
- **AND** declares `computeConstraintMask(AnchorConstraintIndex, int vocabSize)` returning `ConstraintMask`

#### Scenario: Stub implementation produces valid result
- **WHEN** a no-op stub implementation is created for testing
- **THEN** it returns an unconstrained mask (all tokens allowed)
- **AND** produces a valid `ComplianceResult` via the F03 interface

#### Scenario: ConstraintMask record definition
- **WHEN** `ConstraintMask` is defined
- **THEN** it contains `allowedTokens` (boolean array), `constraintCount` (int), and `vocabularySize` (int)

### Requirement: Hybrid enforcement composition

The system SHALL implement `HybridComplianceEnforcer` that composes multiple enforcement layers in sequence: prompt injection, logit bias, and post-generation validation.

#### Scenario: All three layers execute in order
- **WHEN** `HybridComplianceEnforcer.enforce()` is called
- **THEN** prompt injection executes first (always active)
- **AND** logit bias executes second (if model supports it)
- **AND** post-generation validation executes third (if configured)
- **AND** results are accumulated into a combined `ComplianceResult`

#### Scenario: Graceful degradation when logit bias unsupported
- **WHEN** the target model does not support logit bias
- **THEN** the hybrid enforcer skips the logit bias layer
- **AND** logs the capability detection result
- **AND** the remaining layers execute normally

#### Scenario: Combined result aggregation
- **WHEN** multiple layers produce compliance results
- **THEN** the combined result is compliant only if all layers are compliant
- **AND** violations from all layers are merged
- **AND** the most severe suggested action is used

### Requirement: Model capability detection

The system SHALL implement `ModelCapabilityDetector` to determine whether the target model supports logit bias parameters.

#### Scenario: OpenAI models detected as capable
- **WHEN** the model identifier matches known OpenAI patterns (gpt-4*, gpt-3.5*, o1*, o3*)
- **THEN** `supportsLogitBias()` returns true

#### Scenario: Unknown model degrades gracefully
- **WHEN** the model identifier is not recognized
- **THEN** `supportsLogitBias()` returns false
- **AND** a debug-level log identifies the unrecognized model

### Requirement: Enforcement strategy configuration

The system SHALL support configurable enforcement strategy via `dice-anchors.assembly.enforcement-strategy` with values PROMPT_ONLY, LOGIT_BIAS, and HYBRID.

#### Scenario: Default is PROMPT_ONLY
- **WHEN** the enforcement strategy property is not specified
- **THEN** the system defaults to PROMPT_ONLY
- **AND** behavior is identical to pre-feature operation

#### Scenario: LOGIT_BIAS strategy applies bias only
- **WHEN** `enforcement-strategy` is set to LOGIT_BIAS
- **THEN** logit bias enforcement is active
- **AND** post-generation validation is NOT active

#### Scenario: HYBRID strategy applies all layers
- **WHEN** `enforcement-strategy` is set to HYBRID
- **THEN** prompt injection, logit bias, and post-generation validation are all active

### Requirement: Authority-tiered constraint strength

Constraint strength MUST vary by authority level. CANON MUST use hard constraints (maximum bias values). RELIABLE MUST use soft constraints (moderate bias values). PROVISIONAL and UNRELIABLE MUST NOT have logit bias applied.

#### Scenario: CANON gets maximum bias
- **WHEN** a CANON anchor constraint is translated to logit bias
- **THEN** boost tokens receive bias value 100

#### Scenario: RELIABLE gets moderate bias
- **WHEN** a RELIABLE anchor constraint is translated to logit bias
- **THEN** boost tokens receive bias value 50

#### Scenario: Lower authorities get no bias
- **WHEN** PROVISIONAL or UNRELIABLE anchor constraints are processed
- **THEN** no logit bias entries are generated for those anchors

### Requirement: Scenario-level enforcement strategy

Simulation scenarios MAY specify an enforcement strategy for A/B compliance comparison.

#### Scenario: YAML declares enforcement strategy
- **WHEN** a scenario YAML includes `enforcementStrategy: LOGIT_BIAS`
- **THEN** the scenario runs with logit bias enforcement active

#### Scenario: Missing field defaults to PROMPT_ONLY
- **WHEN** a scenario YAML does not include `enforcementStrategy`
- **THEN** the scenario uses the application default (PROMPT_ONLY)

#### Scenario: Compliance rate metric computed
- **WHEN** a scenario completes with enforcement active
- **THEN** `ScoringService` computes and records compliance rate per strategy

## Invariants

- **I1**: Default enforcement strategy MUST be PROMPT_ONLY — no behavioral change without explicit opt-in
- **I2**: All enforcement strategies MUST produce `ComplianceResult` via the F03 `ComplianceEnforcer` interface
- **I3**: CANON constraint bias MUST be strictly stronger than RELIABLE constraint bias
- **I4**: OpenAI logit bias MUST NOT exceed 300 tokens; overflow is handled by authority-priority truncation
- **I5**: Constraint enforcement reads anchor state; it MUST NOT modify anchor rank, authority, or any other anchor field
- **I6**: `ConstrainedDecodingEnforcer` is interface-only; concrete implementation MUST NOT be provided until local model infrastructure exists
- **I7**: Unsupported models MUST degrade gracefully to PROMPT_ONLY; the enforcer MUST NOT throw on unsupported models
