# Feature: Constraint-Aware Decoding

## Feature ID

`F12`

## Summary

Implement advanced compliance enforcement strategies using the `ComplianceEnforcer` interface (established in F03) -- specifically logit bias manipulation for API-based models and, when local model infrastructure is available, token-level constrained decoding. This moves anchor compliance from probabilistic (prompt injection + hope) toward deterministic (prevent the model from generating violating tokens). STATIC research achieved 100% constraint compliance at 0.25% of total inference time.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHALL`, `SHOULD`, `SHOULD NOT`, `MAY`, and their negations). Non-normative guidance uses plain language.

## Why This Feature

1. **Problem addressed**: Even with the best prompt injection, LLM compliance with anchor constraints is probabilistic. The model can and does contradict established facts, especially under adversarial pressure. Post-generation validation (F03) catches violations after they occur but cannot prevent them. Research (STATIC) demonstrates that constrained decoding achieves 100% constraint compliance at negligible inference cost. The gap between probabilistic and deterministic compliance is the single largest reliability concern for anchor-based systems.
2. **Value delivered**: Measurably improved compliance rates via logit bias (near-term, works with API-based models). A defined interface and strategy for constrained decoding (long-term, when local model infrastructure exists). Both strategies produce `ComplianceResult` via the F03 interface, enabling apples-to-apples comparison across enforcement approaches.
3. **Why now**: Wave 4. F03 defined the `ComplianceEnforcer` interface. F09 showed that prompt-level optimizations have limits. This feature addresses the fundamental limitation: probabilistic compliance. Logit bias is implementable now with OpenAI API; constrained decoding requires future infrastructure but the interface should be defined.

## Scope

### In Scope

1. `LogitBiasEnforcer` implementing `ComplianceEnforcer`: translates CANON anchor constraints into API-compatible logit biases for OpenAI-compatible models.
2. `ConstrainedDecodingEnforcer` interface definition: contract for token-level constrained decoding (implementation deferred until local model infrastructure exists).
3. `AnchorConstraintIndex`: translates anchor propositions into token-level constraint sets.
4. Authority-tiered constraint strength: CANON = hard constraints (MUST not contradict), RELIABLE = soft constraints (bias, not block).
5. Hybrid enforcement composition: prompt injection (probabilistic) + logit bias (nudging) + post-generation validation (F03) as a layered defense.
6. Compliance rate measurement: instrumentation to compare enforcement strategies.
7. Research finding integration: pathway separation (Paper 4) -- injection format experimentation support in `AnchorsLlmReference`.

### Out of Scope

1. Local model hosting infrastructure (this feature defines the interface; hosting is a separate concern).
2. Custom CUDA/TPU kernels for constrained decoding (STATIC's VNTK is hardware-specific).
3. Full semantic-to-token constraint translation (known hard problem; initial implementation uses simplified heuristics).
4. Changes to the `ComplianceEnforcer` interface itself (that is F03's scope; this feature adds implementations).
5. Real-time logit manipulation during streaming responses (logit bias is set before generation, not modified mid-stream).

## Dependencies

1. Feature dependencies: F03 (compliance enforcement interface -- `ComplianceEnforcer`, `ComplianceContext`, `ComplianceResult`).
2. Priority: MAY.
3. Wave: 4.
4. OpenSpec change slug: `constraint-aware-decoding`.
5. Research rec: J (full implementation of constraint-aware decoding).

## Research Requirements

1. **Anchor-to-token constraint translation**: Mapping semantic propositions ("The dragon's lair is in the Northern Mountains") to token-level constraints (boost/suppress specific tokens) is an open research problem. Initial implementation MAY use simplified heuristics (keyword extraction, entity name biasing).
2. **Logit bias effectiveness**: The degree to which logit bias improves compliance over prompt injection alone is an empirical question. Requires benchmark comparison across enforcement strategies.
3. **Pathway separation**: Paper 4 (sleeping-llm) discovered that raw-completion and chat-template access are representationally independent. The optimal injection format for maximizing anchor compliance is an open question requiring experimentation.
4. **Semantic constraint complexity bounds**: Determining which anchor constraints can be meaningfully expressed as token-level biases vs. which require full constrained decoding is not yet understood.

## Impacted Areas

1. **`assembly/` package (primary)**: New `LogitBiasEnforcer` implementing `ComplianceEnforcer`. New `AnchorConstraintIndex` for constraint translation. New `ConstrainedDecodingEnforcer` interface.
2. **`assembly/` package (refactor)**: `AnchorsLlmReference` MAY support injection format experimentation (system prompt vs. user message vs. structured template) based on pathway separation research.
3. **`anchor/` package**: No changes. Constraint enforcement reads anchor state; it does not modify it.
4. **`DiceAnchorsProperties`**: New `compliance.enforcement-strategy` config with values: PROMPT_ONLY (current), LOGIT_BIAS, HYBRID (prompt + logit bias + validation).
5. **`sim/engine/` package**: Scenarios can specify enforcement strategy for A/B comparison of compliance rates.

## Visibility Requirements

### UI Visibility

1. BenchmarkView SHOULD display compliance rate comparison across enforcement strategies.
2. RunInspectorView SHOULD display which enforcement strategy was active and whether logit bias was applied.

### Observability Visibility

1. Enforcement strategy MUST be logged per LLM call: `compliance.strategy` (PROMPT_ONLY, LOGIT_BIAS, HYBRID).
2. Logit bias application MUST be logged: `compliance.logit_bias.token_count`, `compliance.logit_bias.constraint_count`.
3. Compliance rate MUST be measurable: `compliance.rate` (constraint-respecting responses / total responses) per strategy.
4. Constraint translation SHOULD be logged: `compliance.constraints.anchor_count`, `compliance.constraints.token_count`, `compliance.constraints.translation_coverage` (fraction of anchors expressible as token constraints).

## Acceptance Criteria

1. `LogitBiasEnforcer` MUST translate CANON anchor constraints into API-compatible logit biases for OpenAI-compatible models.
2. `ConstrainedDecodingEnforcer` interface MUST be defined with clear contract, even if implementation requires future infrastructure.
3. Compliance rate with logit bias MUST measurably exceed prompt-injection-only baseline in simulation benchmarks.
4. Constraint translation MUST preserve anchor semantics (no false enforcement -- biasing tokens that change the intended meaning).
5. Performance overhead MUST be bounded: logit bias adds no generation latency (it is a parameter on the API call); constraint index construction adds pre-generation overhead that MUST be profiled.
6. All enforcement strategies MUST produce `ComplianceResult` via the F03 `ComplianceEnforcer` interface.
7. Enforcement strategy MUST be selectable per-context via configuration and per-scenario via YAML.
8. RELIABLE-authority anchors SHOULD use softer bias values than CANON-authority anchors.
9. Default enforcement strategy MUST be PROMPT_ONLY (no behavior change without explicit opt-in).

## Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **Semantic-to-token translation is lossy** | High | High | This is a known hard problem. Initial implementation uses simplified heuristics (keyword extraction, entity name biasing). Constraint translation coverage metric tracks what fraction of anchors are expressible. Untranslatable anchors fall back to prompt injection. |
| **Logit bias is too coarse for complex constraints** | High | Medium | Logit bias operates at individual token level; complex semantic constraints span multiple tokens. Mitigation: use logit bias for entity names and key terms only; rely on prompt injection for relationship and contextual constraints. Layered defense (hybrid mode) compensates. |
| **OpenAI API logit bias limits** | Medium | Medium | OpenAI's logit bias parameter accepts up to 300 tokens. If anchor constraint translation exceeds this, priority-order by authority (CANON first, RELIABLE second). Document API-specific limits. |
| **Constrained decoding infrastructure does not materialize** | Medium | Low | The interface is defined regardless. `LogitBiasEnforcer` provides immediate value. Constrained decoding is aspirational and explicitly deferred. |
| **False enforcement (bias changes intended meaning)** | Medium | High | Constraint translation MUST be validated: biased tokens are checked against anchor semantics. `ComplianceResult` includes `falseEnforcement` flag. Simulation benchmarks measure both compliance improvement and meaning preservation. |
| **Pathway separation experimentation disrupts existing prompt assembly** | Low | Medium | Injection format experiments are behind a feature flag in `AnchorsLlmReference`. Default format remains unchanged. Experiments are opt-in via configuration. |

## Proposal Seed

### Change Slug

`constraint-aware-decoding`

### Proposal Starter Inputs

1. **Problem statement**: Even with the best prompt injection, LLM compliance with anchor constraints is probabilistic. The model can and does contradict established facts, especially under adversarial pressure. Research (STATIC) demonstrates that constrained decoding achieves 100% constraint compliance with 0.25% of total inference time. The gap between probabilistic and deterministic compliance is the single largest reliability concern for anchor-based systems.
2. **Why now**: F03 defined the interface, F09 showed that prompt-level optimizations have limits. Logit bias is implementable now with OpenAI API. Constrained decoding requires future infrastructure but defining the interface ensures readiness.
3. **Constraints/non-goals**: Logit bias approach MUST work with OpenAI API. Constrained decoding MUST NOT be blocked on -- it is aspirational until local model infrastructure exists. Semantic-to-token constraint translation is a known hard problem; initial implementation MAY use simplified heuristics. No custom CUDA kernels.
4. **Visible outcomes**: Measurable compliance rate improvement in simulation benchmarks. Strategy comparison in BenchmarkView. Constraint translation coverage metric.

### Suggested Capability Areas

1. **Logit bias enforcement**: `LogitBiasEnforcer` translating CANON/RELIABLE anchor constraints into API logit bias parameters.
2. **Constraint index**: `AnchorConstraintIndex` mapping anchor propositions to token-level constraint sets with authority-tiered strength.
3. **Constrained decoding interface**: `ConstrainedDecodingEnforcer` contract for future local-model constrained decoding.
4. **Hybrid composition**: Layered enforcement combining prompt injection + logit bias + post-generation validation.

### Candidate Requirement Blocks

1. **REQ-LOGIT-BIAS**: The system SHALL translate CANON anchor constraints into OpenAI-compatible logit bias parameters.
2. **REQ-CONSTRAINED-INTERFACE**: The system SHALL define a `ConstrainedDecodingEnforcer` interface for token-level constrained decoding.
3. **REQ-COMPLIANCE-IMPROVE**: The logit bias strategy SHALL measurably improve compliance rates over prompt-injection-only baseline.
4. **REQ-AUTHORITY-TIERED**: Constraint strength SHALL vary by authority: CANON = hard constraint, RELIABLE = soft bias.
5. **REQ-COMPLIANCE-RESULT**: All enforcement strategies SHALL produce `ComplianceResult` via the F03 interface.

## Validation Plan

1. **Unit tests** MUST verify `LogitBiasEnforcer` produces valid logit bias maps from CANON anchor constraints.
2. **Unit tests** MUST verify authority-tiered bias strength (CANON bias > RELIABLE bias).
3. **Unit tests** MUST verify `AnchorConstraintIndex` extracts meaningful tokens from anchor proposition text.
4. **Unit tests** MUST verify constraint translation does not produce false enforcement (biased tokens must be semantically relevant).
5. **Unit tests** MUST verify `ComplianceResult` is correctly produced by `LogitBiasEnforcer`.
6. **Unit tests** SHOULD verify graceful degradation when anchor constraints exceed API logit bias limits (priority-order by authority).
7. **Integration test** SHOULD compare compliance rates across strategies (PROMPT_ONLY vs. LOGIT_BIAS vs. HYBRID) using simulation benchmarks.
8. **Benchmark**: Compliance rate improvement MUST be measured and documented.
9. **Regression**: Default strategy (PROMPT_ONLY) MUST produce identical results to pre-feature behavior.

## Known Limitations

1. **Semantic-to-token translation is inherently lossy**: Complex relational constraints ("The dragon lives in the Northern Mountains, not the Southern Desert") cannot be fully expressed as individual token biases. Coverage metric tracks the gap.
2. **OpenAI logit bias limit**: API accepts max 300 biased tokens per request. Large anchor sets with many constraints may exceed this limit. Priority ordering (CANON first) mitigates but does not eliminate.
3. **Constrained decoding is aspirational**: Full token-level constrained decoding (STATIC-style) requires local model hosting with access to logit distributions. This is not available in the current architecture. The interface is defined for future readiness.
4. **No mid-stream adjustment**: Logit bias is set before generation and cannot be modified during streaming. If the model begins a response that leads toward a constraint violation, the bias cannot intervene mid-sentence.
5. **Pathway separation not yet validated**: The hypothesis that injection format significantly affects compliance (from Paper 4) requires empirical validation in the dice-anchors context.

## Suggested Command

```
/opsx:new constraint-aware-decoding
```
