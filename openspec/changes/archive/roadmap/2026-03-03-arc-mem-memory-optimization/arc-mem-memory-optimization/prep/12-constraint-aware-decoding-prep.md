# Prep: Constraint-Aware Decoding

**Feature**: F12 (`constraint-aware-decoding`)
**Wave**: 4
**Priority**: MAY
**Depends on**: F03 (compliance enforcement interface)
**Research rec**: J

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHALL`, `SHOULD`, `SHOULD NOT`, `MAY`, and their negations). Non-normative guidance uses plain language.

---

## Locked Decisions

These decisions are final and MUST NOT be revisited during implementation.

### L1: ComplianceEnforcer Interface (from F03)

All enforcement strategies MUST implement the `ComplianceEnforcer` interface established in F03: `enforce(ComplianceContext) -> ComplianceResult`. No modifications to this interface are in scope. This feature adds implementations, not interface changes.

### L2: Logit Bias as Near-Term Strategy

`LogitBiasEnforcer` is the primary deliverable. It MUST translate CANON context unit constraints into logit bias parameters compatible with the OpenAI API. This is implementable now with existing API infrastructure and provides immediate compliance improvement.

### L3: Constrained Decoding as Future Strategy

`ConstrainedDecodingEnforcer` is an interface definition only. The contract MUST be specified, but concrete implementation is deferred until local model infrastructure (access to logit distributions, custom decoding loops) exists. No implementation is required or expected for initial delivery.

### L4: Authority-Tiered Constraint Strength

Constraint strength MUST vary by authority level:
- **CANON**: Hard constraints. Maximum logit bias values. These facts MUST NOT be contradicted.
- **RELIABLE**: Soft constraints. Moderate logit bias values. These facts SHOULD NOT be contradicted.
- **PROVISIONAL/UNRELIABLE**: No logit bias applied. Compliance relies on prompt injection only.

### L5: Default Strategy is PROMPT_ONLY

The default enforcement strategy MUST be PROMPT_ONLY (current behavior). Logit bias and hybrid strategies are opt-in via configuration. No behavioral change without explicit opt-in.

### L6: Hybrid Composition

HYBRID mode composes three enforcement layers:
1. Prompt injection (probabilistic, always active)
2. Logit bias (nudging, applied before generation)
3. Post-generation validation (F03, checks output after generation)

All three layers produce or consume `ComplianceResult` via the F03 interface.

---

## Open Questions

These decisions require further investigation or prototyping before implementation.

### O1: Context Unit-to-Token Constraint Translation Approach

**Question**: How are semantic context unit propositions translated into token-level constraints?

**Context**: An context unit like "The dragon's lair is in the Northern Mountains" needs to become a set of tokens to boost/suppress. This is the hardest design problem in F12.

**Candidates**:
- **Keyword extraction**: Extract key entities and terms from context unit text ("dragon", "lair", "Northern", "Mountains"). Boost these tokens. Suppress their antonyms if identifiable. Pros: simple, deterministic. Cons: loses relational meaning; "Northern" boosted doesn't prevent "Southern Mountains".
- **Entity name biasing**: Focus only on proper nouns and named entities. Boost entity names that appear in context units. Pros: high precision, low risk of false enforcement. Cons: low coverage -- misses adjectival/relational constraints entirely.
- **Negation-aware extraction**: Extract context unit terms AND their negations. Boost context unit terms, suppress negation patterns ("not Northern", "isn't in the Mountains"). Pros: better coverage than keyword-only. Cons: requires negation detection, still limited to surface-level tokens.
- **LLM-assisted constraint generation**: Use a small/fast LLM call to generate constraint token sets from context unit text. Pros: semantic understanding. Cons: per-context unit LLM call adds latency and cost; may itself hallucinate constraints.

**Recommendation**: Entity name biasing is RECOMMENDED as the initial approach due to high precision and low false-enforcement risk. Keyword extraction MAY be added as a secondary signal. LLM-assisted generation SHOULD be evaluated but is not required for initial delivery.

### O2: Logit Bias API Compatibility Scope

**Question**: Which model APIs support logit bias, and what are their limitations?

**Known constraints**:
- **OpenAI API**: `logit_bias` parameter accepts a JSON object mapping token IDs to bias values (-100 to 100). Max 300 tokens per request.
- **Azure OpenAI**: Same as OpenAI.
- **Anthropic (Claude)**: Does not support logit bias. Prompt injection only.
- **Local models (Ollama, vLLM)**: Support varies. vLLM supports logit bias; Ollama may not.

**Design implication**: `LogitBiasEnforcer` MUST detect model capability and degrade gracefully. When the target model does not support logit bias, the enforcer MUST fall back to PROMPT_ONLY and log a warning. The enforcer MUST NOT fail hard on unsupported models.

### O3: Local Model Infrastructure Requirements

**Question**: What infrastructure is needed for `ConstrainedDecodingEnforcer` to become implementable?

**Requirements** (all deferred):
- Access to raw logit distribution at each decoding step
- Ability to mask/modify logits before sampling
- Custom decoding loop (not just API call + response)
- Constraint index loaded into accelerator memory

**Candidates**: vLLM with custom samplers, Hugging Face Transformers with `LogitsProcessor`, TensorRT-LLM with custom plugins.

**Decision**: This is documented for future reference. No infrastructure work is in scope for F12. The interface is defined to be implementation-ready when infrastructure exists.

### O4: Semantic Constraint Complexity Bounds

**Question**: Which context unit constraints can be meaningfully expressed as token-level biases vs. which require full constrained decoding?

**Analysis**:
- **Expressible as logit bias**: Entity names ("Northern Mountains" exists), simple attributes ("the dragon is red" -- boost "red"), quantitative facts ("three towers" -- boost "three").
- **NOT expressible as logit bias**: Relational constraints ("X is the enemy of Y" -- no single token captures this), temporal constraints ("before the battle" -- context-dependent), conditional constraints ("if the door is locked, then...").
- **Coverage estimate**: Logit bias can meaningfully address ~30-40% of typical context unit constraints (primarily entity names and simple attributes). The remaining 60-70% rely on prompt injection.

**Decision**: `UnitConstraintIndex` MUST track `translationCoverage` -- the fraction of active context units whose constraints were expressible as token-level biases. This metric informs whether logit bias alone provides sufficient improvement or whether constrained decoding is needed.

---

## Small-Model Task Constraints

All implementation tasks MUST adhere to these constraints to remain small-model friendly.

- **Max 5 files per task**: Each implementation task MUST touch at most 5 source files (excluding test files). (Elevated from standard 4 due to cross-cutting nature of constraint enforcement.)
- **Verification**: Every task MUST be verified with `./mvnw test` before completion.
- **Incremental delivery**: Each task MUST leave the codebase in a compilable, test-passing state.
- **No speculative implementation**: Tasks implement what is specified, nothing more.

---

## Implementation Tasks

### Task 1: UnitConstraintIndex

**Files** (3):
1. `src/main/java/dev/dunnam/arcmem/assembly/UnitConstraintIndex.java` -- new class
2. `src/main/java/dev/dunnam/arcmem/assembly/UnitConstraint.java` -- new record
3. `src/test/java/dev/dunnam/arcmem/assembly/UnitConstraintIndexTest.java` -- new test

**Work**:
1. Define `UnitConstraint` record: `(String unitId, Authority authority, Set<String> boostTokens, Set<String> suppressTokens, double translationCoverage)`.
2. Implement `UnitConstraintIndex.build(List<Context Unit>) -> UnitConstraintIndex`.
3. Implement entity name extraction from context unit text (initial approach per O1).
4. Compute per-context unit translation coverage (fraction of context unit meaning captured by token constraints).
5. Expose `getConstraints() -> List<UnitConstraint>`, `getTotalCoverage() -> double`.
6. Unit tests: constraint extraction from sample context unit texts, coverage calculation, authority-based constraint strength.

**Verification**: `./mvnw test`

### Task 2: LogitBiasEnforcer

**Files** (5):
1. `src/main/java/dev/dunnam/arcmem/assembly/LogitBiasEnforcer.java` -- new class implementing `ComplianceEnforcer`
2. `src/main/java/dev/dunnam/arcmem/assembly/LogitBiasMap.java` -- new record for bias representation
3. `src/main/java/dev/dunnam/arcmem/assembly/UnitConstraintIndex.java` -- consume constraints
4. `src/main/java/dev/dunnam/arcmem/ArcMemProperties.java` -- add enforcement strategy config
5. `src/test/java/dev/dunnam/arcmem/assembly/LogitBiasEnforcerTest.java` -- new test

**Work**:
1. Implement `LogitBiasEnforcer` consuming `UnitConstraintIndex` to produce logit bias maps.
2. Define `LogitBiasMap` record: `(Map<Integer, Double> tokenBiases, int constraintCount, double coverage)`.
3. Authority-tiered bias strength: CANON = high bias value, RELIABLE = moderate bias value.
4. Respect OpenAI 300-token limit: priority-order by authority (CANON first), truncate if exceeded.
5. Produce `ComplianceResult` with constraint application metadata.
6. Add `compliance.enforcement-strategy` property to `ArcMemProperties` (default: PROMPT_ONLY).
7. Unit tests: bias map generation, authority tiering, token limit enforcement, `ComplianceResult` correctness.

**Verification**: `./mvnw test`

### Task 3: ConstrainedDecodingEnforcer Interface

**Files** (3):
1. `src/main/java/dev/dunnam/arcmem/assembly/ConstrainedDecodingEnforcer.java` -- new interface
2. `src/main/java/dev/dunnam/arcmem/assembly/ConstraintMask.java` -- new record for constraint representation
3. `src/test/java/dev/dunnam/arcmem/assembly/ConstrainedDecodingEnforcerTest.java` -- contract test (mock implementation)

**Work**:
1. Define `ConstrainedDecodingEnforcer` interface extending `ComplianceEnforcer`.
2. Add method: `computeConstraintMask(UnitConstraintIndex, int vocabSize) -> ConstraintMask`.
3. Define `ConstraintMask` record: `(boolean[] allowedTokens, int constraintCount)`.
4. Document the contract: at each decoding step, disallowed tokens have logits set to negative infinity.
5. Create a no-op/stub implementation for testing that always returns an unconstrained mask.
6. Contract tests verify the interface compiles and the stub implementation produces valid `ComplianceResult`.

**Verification**: `./mvnw test`

### Task 4: Hybrid Enforcement Composition and API Capability Detection

**Files** (5):
1. `src/main/java/dev/dunnam/arcmem/assembly/HybridComplianceEnforcer.java` -- new class composing enforcement layers
2. `src/main/java/dev/dunnam/arcmem/assembly/ModelCapabilityDetector.java` -- new class for API feature detection
3. `src/main/java/dev/dunnam/arcmem/assembly/LogitBiasEnforcer.java` -- integrate capability detection
4. `src/main/resources/application.yml` -- add enforcement strategy defaults
5. `src/test/java/dev/dunnam/arcmem/assembly/HybridComplianceEnforcerTest.java` -- new test

**Work**:
1. Implement `HybridComplianceEnforcer` composing prompt injection + logit bias + post-generation validation.
2. Implement `ModelCapabilityDetector` to determine if target model supports logit bias. Degrade gracefully to PROMPT_ONLY when unsupported.
3. Hybrid enforcement layers execute in order; each layer's `ComplianceResult` is accumulated into a combined result.
4. Log enforcement strategy, capability detection result, and per-layer compliance outcome.
5. Unit tests: hybrid composition, graceful degradation on unsupported models, combined `ComplianceResult` aggregation.

**Verification**: `./mvnw test`

### Task 5: Scenario Integration and Compliance Benchmarking

**Files** (5):
1. `src/main/java/dev/dunnam/arcmem/sim/engine/ScenarioLoader.java` -- read `enforcementStrategy` from YAML
2. `src/main/java/dev/dunnam/arcmem/sim/engine/SimulationTurnExecutor.java` -- apply per-scenario enforcement
3. `src/main/java/dev/dunnam/arcmem/sim/engine/ScoringService.java` -- add compliance rate metric
4. `src/main/resources/simulations/` -- update one scenario as example
5. `src/test/java/dev/dunnam/arcmem/sim/engine/ScenarioLoaderTest.java` -- test YAML parsing

**Work**:
1. Add `enforcementStrategy` field to scenario YAML schema (optional, default: PROMPT_ONLY).
2. `ScenarioLoader` parses the field and passes to simulation context.
3. `ScoringService` computes and records compliance rate per enforcement strategy.
4. Add structured logging: strategy used, constraint count, coverage, per-layer outcome.
5. Unit test: YAML parsing, default fallback, scoring with enforcement metadata.

**Verification**: `./mvnw test`

---

## Implementation Gates

Each gate MUST be satisfied before proceeding to subsequent tasks. Gates are verified by running `./mvnw test` and inspecting test results.

### Gate 1: LogitBiasEnforcer Defined (after Task 1 + Task 2)

- `UnitConstraintIndex` extracts meaningful tokens from context unit text.
- `LogitBiasEnforcer` produces valid logit bias maps from context unit constraints.
- Authority-tiered bias strength is implemented (CANON > RELIABLE).
- OpenAI 300-token limit is respected with priority ordering.
- `ComplianceResult` is correctly produced.

### Gate 2: Compliance Improvement Measurable (after Task 4 + Task 5)

- Hybrid enforcement composes all three layers correctly.
- Model capability detection degrades gracefully on unsupported models.
- Compliance rate metric is computable in simulation scoring.
- A/B comparison between PROMPT_ONLY and LOGIT_BIAS strategies is possible via scenario YAML.

### Gate 3: ConstrainedDecodingEnforcer Interface Defined (after Task 3)

- `ConstrainedDecodingEnforcer` interface compiles with clear contract documentation.
- `ConstraintMask` record is defined.
- Stub implementation passes contract tests.
- Interface is implementation-ready for when local model infrastructure exists.

### Gate 4: Full Integration (after Task 5)

- Scenario YAML can specify enforcement strategy.
- Default strategy (PROMPT_ONLY) produces identical behavior to pre-feature.
- Observability logging emits strategy, constraint count, coverage, and per-layer outcomes.
- All existing scenarios pass without modification.
