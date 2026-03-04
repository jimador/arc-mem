## Why

Even with authority-tiered compliance directives (F03), LLM compliance with anchor constraints remains probabilistic. The model can and does contradict established facts, especially under adversarial pressure. Post-generation validation catches violations after they occur but cannot prevent them. Google AI's STATIC research demonstrated that constrained decoding achieves 100% constraint compliance at 0.25% of total inference time. The gap between probabilistic and deterministic compliance is the single largest reliability concern for anchor-based systems.

Logit bias — available today via the OpenAI API — provides a middle ground: nudging token probabilities at generation time to favor anchor-consistent output. This is not full constrained decoding (which requires local model infrastructure), but it shifts compliance from purely probabilistic toward semi-deterministic for entity names and key terms.

The sleeping-llm research (Paper 4) adds a complementary insight: pathway separation. Raw-completion and chat-template access are representationally independent, meaning the injection format for anchor constraints materially affects compliance. The optimal format is an empirical question that this feature enables investigating.

## What Changes

- Add `AnchorConstraintIndex` that translates anchor propositions into token-level constraint sets, with authority-tiered strength and translation coverage tracking
- Add `LogitBiasEnforcer` implementing `ComplianceEnforcer` (from F03): translates CANON/RELIABLE anchor constraints into OpenAI-compatible logit bias parameters
- Define `ConstrainedDecodingEnforcer` interface for future token-level constrained decoding (implementation deferred)
- Add `HybridComplianceEnforcer` composing prompt injection + logit bias + post-generation validation as layered defense
- Add `ModelCapabilityDetector` for graceful degradation on models that do not support logit bias
- Add `enforcement-strategy` configuration property (default: PROMPT_ONLY — no behavioral change without opt-in)
- Add scenario-level enforcement strategy selection for A/B compliance benchmarking

## Capabilities

### New Capabilities
- `logit-bias-enforcement`: Token-level probability nudging for CANON/RELIABLE anchor constraints via OpenAI API
- `constraint-index`: Anchor-to-token constraint translation with coverage tracking
- `constrained-decoding-interface`: Contract definition for future deterministic decoding enforcement
- `hybrid-enforcement`: Layered composition of prompt, logit bias, and post-generation validation strategies

### Modified Capabilities
- `compliance-enforcement` (F03): New implementations added to the existing `ComplianceEnforcer` interface
- `simulation-scenarios`: Scenarios MAY specify enforcement strategy for A/B comparison

## Impact

- **Files (new)**: `assembly/AnchorConstraintIndex.java`, `assembly/AnchorConstraint.java`, `assembly/LogitBiasEnforcer.java`, `assembly/LogitBiasMap.java`, `assembly/ConstrainedDecodingEnforcer.java`, `assembly/ConstraintMask.java`, `assembly/HybridComplianceEnforcer.java`, `assembly/ModelCapabilityDetector.java`, `assembly/EnforcementStrategy.java`
- **Files (modified)**: `DiceAnchorsProperties.java` (enforcement config), `application.yml` (defaults)
- **Config**: New `dice-anchors.assembly.enforcement-strategy` property (enum: PROMPT_ONLY, LOGIT_BIAS, HYBRID)
- **Affected**: LLM call pipeline, compliance enforcement, simulation scoring
- **Behavior**: Default is PROMPT_ONLY — identical to current behavior. LOGIT_BIAS and HYBRID are opt-in.

## Constitutional Alignment

- **Article I (RFC 2119)**: All requirements use RFC 2119 keywords
- **Article III (Constructor injection)**: All new Spring beans use constructor injection
- **Article IV (Records)**: `AnchorConstraint`, `LogitBiasMap`, `ConstraintMask` are records
- **Article V (Anchor invariants)**: Constraint enforcement reads anchor state; it does not modify it. No anchor invariant changes.
- **Article VII (Test-first)**: Unit tests for constraint index, logit bias generation, hybrid composition

## Research References

- **Google AI STATIC**: Sparse matrix constrained decoding for deterministic structured output. 100% compliance at 0.25% inference time. Informs the `ConstrainedDecodingEnforcer` interface contract.
- **sleeping-llm Paper 4**: Pathway separation — raw-completion and chat-template are representationally independent. Informs injection format experimentation support.
- **sleeping-llm Paper 6**: Per-fact graduated consolidation. The concept of per-fact difficulty maps to per-anchor translation coverage — some anchors are harder to express as token constraints than others.
