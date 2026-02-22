## 1. Configuration Properties

- [x] 1.1 Add `ConflictConfig` record to `DiceAnchorsProperties` with `negationOverlapThreshold` (0.5), `llmConfidence` (0.9), `replaceThreshold` (0.8), `demoteThreshold` (0.6) and nested `TierModifierConfig` with `hotDefenseModifier` (0.1), `warmDefenseModifier` (0.0), `coldDefenseModifier` (-0.1)
- [x] 1.2 Add `dice-anchors.conflict.*` defaults to `application.yml`
- [x] 1.3 Add conflict config validation to `AnchorConfiguration.validateConfiguration()`: thresholds in (0.0, 1.0], replace > demote, tier modifiers in [-0.5, 0.5]

## 2. Threshold Externalization

- [x] 2.1 Modify `NegationConflictDetector` constructor to accept overlap threshold from `ConflictConfig`; replace hardcoded 0.5 with injected value
- [x] 2.2 Modify `LlmConflictDetector` constructor to accept confidence from `ConflictConfig`; replace hardcoded `LLM_CONFLICT_CONFIDENCE = 0.9` with injected value
- [x] 2.3 Update `AnchorConfiguration` bean wiring to pass `ConflictConfig` fields to detector constructors
- [x] 2.4 Update `CompositeConflictDetector` construction if it needs config passthrough

## 3. Tier-Aware Resolution

- [x] 3.1 Modify `AuthorityConflictResolver` constructor to accept `ConflictConfig` (resolution thresholds + tier modifiers)
- [x] 3.2 Implement tier-aware effective threshold computation: `effectiveThreshold = baseThreshold + tierModifier(anchor.memoryTier())`
- [x] 3.3 Replace hardcoded 0.8/0.6 thresholds in resolution matrix with configured + tier-adjusted values
- [x] 3.4 Ensure CANON immunity is preserved regardless of tier modifier (short-circuit before tier computation)
- [x] 3.5 Handle absent tier-modifiers config: default to zero-bias for backward compatibility when `TierModifierConfig` is null
- [x] 3.6 Update `AnchorConfiguration` bean wiring to pass `ConflictConfig` to `AuthorityConflictResolver`

## 4. OTEL Observability

- [x] 4.1 Add conflict span attributes to `SimulationTurnExecutor`: `conflict.detected_count`, `conflict.resolved_count`, `conflict.resolution_outcomes`
- [x] 4.2 Add `@Observed(name = "conflict.resolution")` to `AuthorityConflictResolver.resolve()` with low-cardinality keys: `conflict.existing_authority`, `conflict.incoming_confidence_band`, `conflict.existing_tier`, `conflict.resolution`
- [x] 4.3 Add `confidenceBand()` helper (LOW < 0.4, MEDIUM 0.4-0.8, HIGH > 0.8) for OTEL key value

## 5. Test Updates

- [x] 5.1 Update existing `NegationConflictDetectorTest` tests to inject threshold via constructor
- [x] 5.2 Update existing `LlmConflictDetectorTest` tests to inject confidence via constructor
- [x] 5.3 Update existing `AuthorityConflictResolverTest` tests to inject config via constructor
- [x] 5.4 Add tests for tier-aware resolution: HOT defensive bias, COLD permissive bias, WARM baseline, CANON immune
- [x] 5.5 Add tests for conflict config validation: invalid thresholds, inverted replace/demote, invalid tier modifiers, valid config passes
- [x] 5.6 Update any other test files that construct detectors/resolvers directly (fix compilation)

## 6. Build Verification

- [x] 6.1 Run full test suite — all tests pass
- [x] 6.2 Verify backward compatibility: no configuration changes produce identical behavior to pre-change baseline
