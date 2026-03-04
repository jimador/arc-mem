# Implementation Tasks

## 1. AnchorConstraintIndex + AnchorConstraint Record

**Files** (3 source + 1 test):
1. `src/main/java/dev/dunnam/diceanchors/assembly/AnchorConstraint.java` — new record
2. `src/main/java/dev/dunnam/diceanchors/assembly/AnchorConstraintIndex.java` — new class
3. `src/main/java/dev/dunnam/diceanchors/assembly/EnforcementStrategy.java` — new enum
4. `src/test/java/dev/dunnam/diceanchors/assembly/AnchorConstraintIndexTest.java` — new test

**Work**:
- [x] 1.1 Define `AnchorConstraint` record: `(String anchorId, Authority authority, Set<String> boostTokens, Set<String> suppressTokens, double translationCoverage)`
- [x] 1.2 Define `EnforcementStrategy` enum: `PROMPT_ONLY`, `LOGIT_BIAS`, `HYBRID`
- [x] 1.3 Implement `AnchorConstraintIndex` with `build(List<Anchor>)` static factory
- [x] 1.4 Implement entity name extraction: split text on whitespace/punctuation, keep capitalized words, filter stop words
- [x] 1.5 Compute per-anchor translation coverage: `boostTokens.size() / totalTokens`
- [x] 1.6 Filter by authority: only CANON and RELIABLE produce constraints (per L4)
- [x] 1.7 Expose `getConstraints() -> List<AnchorConstraint>`, `getTotalCoverage() -> double`
- [x] 1.8 Unit tests: entity extraction from sample anchors, coverage calculation, authority filtering, empty input

**Verification**: `./mvnw test`

## 2. LogitBiasEnforcer + LogitBiasMap

**Files** (4 source + 1 test):
1. `src/main/java/dev/dunnam/diceanchors/assembly/LogitBiasMap.java` — new record
2. `src/main/java/dev/dunnam/diceanchors/assembly/LogitBiasEnforcer.java` — new class implementing `ComplianceEnforcer`
3. `src/main/java/dev/dunnam/diceanchors/assembly/ModelCapabilityDetector.java` — new class
4. `src/main/java/dev/dunnam/diceanchors/DiceAnchorsProperties.java` — add enforcement strategy to AssemblyConfig
5. `src/test/java/dev/dunnam/diceanchors/assembly/LogitBiasEnforcerTest.java` — new test

**Work**:
- [x] 2.1 Define `LogitBiasMap` record: `(Map<String, Integer> tokenBiases, int constraintCount, double coverage, int overflowCount)` with constants `MAX_TOKENS=300`, `CANON_BIAS=100`, `RELIABLE_BIAS=50`
- [x] 2.2 Implement `ModelCapabilityDetector` with `supportsLogitBias(String modelId)` — pattern-match known OpenAI models, default false for unknown
- [x] 2.3 Implement `LogitBiasEnforcer` implementing `ComplianceEnforcer`:
  - Consume `AnchorConstraintIndex` to produce `LogitBiasMap`
  - Apply authority-tiered bias: CANON tokens get bias 100, RELIABLE tokens get bias 50
  - Respect 300-token limit with CANON-first priority ordering
  - Log overflow when limit exceeded
  - Produce `ComplianceResult` with enforcement metadata
- [x] 2.4 Add `enforcementStrategy` field to `DiceAnchorsProperties.AssemblyConfig` (default: PROMPT_ONLY)
- [x] 2.5 Unit tests: bias map generation from anchor constraints, authority tiering (CANON > RELIABLE), 300-token overflow handling, unsupported model degradation, `ComplianceResult` correctness

**Verification**: `./mvnw test`

## 3. ConstrainedDecodingEnforcer Interface + ConstraintMask

**Files** (3 source + 1 test):
1. `src/main/java/dev/dunnam/diceanchors/assembly/ConstrainedDecodingEnforcer.java` — new interface
2. `src/main/java/dev/dunnam/diceanchors/assembly/ConstraintMask.java` — new record
3. `src/main/java/dev/dunnam/diceanchors/assembly/NoOpConstrainedDecodingEnforcer.java` — stub implementation
4. `src/test/java/dev/dunnam/diceanchors/assembly/ConstrainedDecodingEnforcerTest.java` — contract test

**Work**:
- [x] 3.1 Define `ConstrainedDecodingEnforcer` interface extending `ComplianceEnforcer`
  - Method: `computeConstraintMask(AnchorConstraintIndex index, int vocabSize) -> ConstraintMask`
  - Javadoc: at each decoding step, disallowed tokens have logits set to negative infinity (STATIC architecture)
- [x] 3.2 Define `ConstraintMask` record: `(boolean[] allowedTokens, int constraintCount, int vocabularySize)`
- [x] 3.3 Implement `NoOpConstrainedDecodingEnforcer`: returns unconstrained mask (all true), compliant result
- [x] 3.4 Contract tests: interface compiles, stub produces valid mask, stub returns compliant `ComplianceResult`

**Verification**: `./mvnw test`

## 4. HybridComplianceEnforcer

**Files** (2 source + 1 config + 1 test):
1. `src/main/java/dev/dunnam/diceanchors/assembly/HybridComplianceEnforcer.java` — new class
2. `src/main/java/dev/dunnam/diceanchors/assembly/ComplianceEnforcerFactory.java` — new factory for strategy-based wiring
3. `src/main/resources/application.yml` — add enforcement strategy default
4. `src/test/java/dev/dunnam/diceanchors/assembly/HybridComplianceEnforcer.java` — new test

**Work**:
- [x] 4.1 Implement `HybridComplianceEnforcer` implementing `ComplianceEnforcer`:
  - Accept ordered list of `ComplianceEnforcer` layers via constructor
  - Execute layers in order, accumulate results
  - `compliant` = all layers compliant
  - `violations` = union of all layer violations
  - `suggestedAction` = most severe across layers (REJECT > RETRY > ACCEPT)
  - `validationDuration` = sum of all layer durations
- [x] 4.2 Implement `ComplianceEnforcerFactory` Spring `@Configuration`:
  - Read `enforcementStrategy` from `DiceAnchorsProperties`
  - PROMPT_ONLY: return `PromptInjectionEnforcer`
  - LOGIT_BIAS: return `LogitBiasEnforcer`
  - HYBRID: return `HybridComplianceEnforcer` composing all three layers
- [x] 4.3 Update `application.yml` with `dice-anchors.assembly.enforcement-strategy: PROMPT_ONLY`
- [x] 4.4 Unit tests: layer execution order, result aggregation, graceful degradation, factory strategy selection

**Verification**: `./mvnw test`

## 5. Scenario Integration + Compliance Metrics

**Files** (3 source + 1 scenario + 1 test):
1. `src/main/java/dev/dunnam/diceanchors/sim/engine/ScenarioLoader.java` — read `enforcementStrategy` from YAML
2. `src/main/java/dev/dunnam/diceanchors/sim/engine/SimulationTurnExecutor.java` — apply per-scenario enforcement
3. `src/main/java/dev/dunnam/diceanchors/sim/engine/ScoringService.java` — add compliance rate metric
4. `src/main/resources/simulations/adversarial-contradictory.yml` — add example `enforcementStrategy` field
5. `src/test/java/dev/dunnam/diceanchors/sim/engine/ScenarioLoaderTest.java` — test YAML parsing

**Work**:
- [x] 5.1 Add `enforcementStrategy` field to scenario YAML schema (optional, default: PROMPT_ONLY)
- [x] 5.2 `ScenarioLoader` parses the field and passes to simulation context
- [x] 5.3 `SimulationTurnExecutor` selects enforcement strategy based on scenario config
- [x] 5.4 `ScoringService` computes compliance rate: constraint-respecting responses / total responses
- [x] 5.5 Add structured logging per enforcement: strategy, constraint count, coverage, per-layer outcome
- [x] 5.6 Update one scenario YAML as an example (add `enforcementStrategy: PROMPT_ONLY` comment showing usage)
- [x] 5.7 Unit tests: YAML parsing with and without field, default fallback, scoring with enforcement metadata

**Verification**: `./mvnw test`

## Definition of Done

- All tests pass (`./mvnw test`)
- Default strategy (PROMPT_ONLY) produces identical behavior to pre-feature
- `LogitBiasEnforcer` produces valid logit bias maps from CANON/RELIABLE anchors
- Authority-tiered bias strength: CANON (100) > RELIABLE (50) > lower (none)
- OpenAI 300-token limit is respected with priority truncation
- `ConstrainedDecodingEnforcer` interface compiles with documented contract
- `HybridComplianceEnforcer` composes layers with correct result aggregation
- Unsupported models degrade gracefully (no exceptions, warning logged)
- `AnchorConstraintIndex` tracks translation coverage
- Enforcement strategy is configurable via properties and per-scenario YAML
- Compliance rate metric computable in simulation scoring
