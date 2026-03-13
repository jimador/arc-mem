# ARC-Mem Trust Specification (Delta)

## MODIFIED Requirements

### Requirement: Domain profile weight validation

**Modifies**: `DomainProfile` construction and built-in profiles.

`DomainProfile` signal weights SHALL be validated to sum to 1.0 (within a floating-point tolerance of 0.001). If the weights do not sum to 1.0, construction SHALL throw `IllegalArgumentException`.

A `@VisibleForTesting`-annotated factory method SHALL be provided for test profiles that bypass validation.

**Note**: This validation applies at construction time. At runtime, `TrustEvaluator` redistributes weights from absent signals to present signals. This redistribution does not violate the construction invariant -- it is a runtime adjustment that preserves proportional weighting when some signals are unavailable.

**Change**: The three built-in profiles (NARRATIVE, SECURE, BALANCED) SHALL be updated to include weights for two new quality signals (`novelty` and `importance`). When quality scoring is disabled, these signals return `OptionalDouble.empty()` and their weight is redistributed by `TrustEvaluator`, preserving existing behavior.

Updated profile weight allocations:

**NARRATIVE** (source-heavy, permissive):
- `sourceAuthority`: 0.35
- `extractionConfidence`: 0.25
- `graphConsistency`: 0.15
- `corroboration`: 0.15
- `novelty`: 0.05
- `importance`: 0.05

**SECURE** (graph-heavy, strict):
- `sourceAuthority`: 0.18
- `extractionConfidence`: 0.18
- `graphConsistency`: 0.36
- `corroboration`: 0.18
- `novelty`: 0.05
- `importance`: 0.05

**BALANCED** (equal weights, moderate):
- `sourceAuthority`: 0.22
- `extractionConfidence`: 0.22
- `graphConsistency`: 0.22
- `corroboration`: 0.22
- `novelty`: 0.06
- `importance`: 0.06

#### Scenario: NARRATIVE profile with quality scoring disabled

- **GIVEN** the NARRATIVE profile with quality signal weights of 0.05 each
- **WHEN** quality scoring is disabled and both signals return empty
- **THEN** `TrustEvaluator` SHALL redistribute the 0.10 absent weight proportionally among the four present signals
- **AND** trust scores SHALL be equivalent to the prior NARRATIVE profile behavior

#### Scenario: NARRATIVE profile with quality scoring enabled

- **GIVEN** the NARRATIVE profile with quality signal weights of 0.05 each
- **WHEN** quality scoring is enabled and both signals return values
- **THEN** novelty and importance SHALL contribute 5% each to the composite score
- **AND** existing signals SHALL contribute at their configured weights

#### Scenario: Profile weight sum remains valid

- **GIVEN** updated NARRATIVE profile weights (0.35 + 0.25 + 0.15 + 0.15 + 0.05 + 0.05)
- **WHEN** the profile is constructed
- **THEN** the weight sum SHALL be 1.0 and construction SHALL succeed

### Requirement: TrustConfiguration conditional quality signal registration

**Modifies**: `TrustConfiguration` Spring configuration.

`TrustConfiguration` SHALL register `NoveltySignal` and `ImportanceSignal` beans conditionally. The beans SHALL always be registered (to participate in the signal list), but their `evaluate()` method SHALL check the quality scoring enabled flag and return `OptionalDouble.empty()` when disabled.

This approach avoids conditional bean registration complexity while leveraging the existing absent-signal weight redistribution in `TrustEvaluator`.

#### Scenario: Quality signal beans registered

- **GIVEN** the application context starts with quality scoring configuration present
- **WHEN** Spring collects `List<TrustSignal>` for `TrustPipeline`
- **THEN** the list SHALL include `NoveltySignal` and `ImportanceSignal` alongside existing signals

#### Scenario: Quality signals produce empty when disabled

- **GIVEN** quality scoring is disabled (`arc-mem.unit.quality-scoring.enabled=false`)
- **WHEN** `TrustPipeline` evaluates signals
- **THEN** `NoveltySignal` and `ImportanceSignal` SHALL return `OptionalDouble.empty()`
- **AND** `TrustEvaluator` SHALL redistribute their weights to present signals

## Invariants

- **AT-QS1**: Updated profile weights MUST sum to 1.0 (within tolerance 0.001).
- **AT-QS2**: When quality scoring is disabled, trust evaluation results MUST be equivalent to prior behavior (absent-signal redistribution preserves proportional weighting).
