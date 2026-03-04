## Context

The `ComplianceEnforcer` interface (F03) provides two implementations today: `PromptInjectionEnforcer` (zero-cost, probabilistic) and `PostGenerationValidator` (one LLM call, post-hoc). Neither can prevent constraint violations during generation. This feature fills the gap with logit bias enforcement (nudges token probabilities before generation) and defines the interface for full constrained decoding (future, requires local model infrastructure).

The authority-tiered compliance policy (from F03) already classifies anchors by enforcement priority. This feature leverages that classification to determine which anchors produce logit bias constraints and at what strength.

### Research Foundation

**Google AI STATIC**: Demonstrated that sparse matrix constrained decoding achieves 100% constraint compliance at 0.25% of total inference time. The key insight: constraints can be encoded as vocabulary masks applied at each decoding step. Our `ConstrainedDecodingEnforcer` interface is modeled after this architecture, but implementation requires access to the raw logit distribution — unavailable through API-based models.

**sleeping-llm Paper 4 (Pathway Separation)**: Raw-completion and chat-template access are representationally independent. This means the injection format for anchor constraints materially affects compliance. The hybrid enforcement approach accounts for this by not relying solely on prompt injection.

**sleeping-llm Paper 6 (Per-Fact Consolidation)**: Individual facts have inherent consolidation difficulty. This maps directly to our `translationCoverage` metric — some anchor propositions translate cleanly to token constraints (entity names), while others (relational constraints, temporal clauses) cannot be expressed at the token level.

## Goals / Non-Goals

**Goals:**
- Translate CANON/RELIABLE anchor constraints into OpenAI logit bias parameters
- Define a clear interface for future constrained decoding
- Compose multiple enforcement layers (prompt + bias + validation) into a hybrid strategy
- Track constraint translation coverage to quantify the gap between logit bias and full constrained decoding
- Enable A/B compliance comparison via scenario-level strategy selection

**Non-Goals:**
- Implement constrained decoding (requires local model infrastructure)
- Modify the `ComplianceEnforcer` interface (F03 scope)
- Build a tokenizer (use simplified heuristic mapping)
- Support mid-stream logit adjustment during streaming
- Full semantic-to-token translation (known hard problem; entity name biasing only)

## Decisions

### 1. Entity Name Biasing as Initial Translation Approach (O1 Resolution)

Anchor-to-token constraint translation uses entity name biasing: extract proper nouns, named entities, and distinctive terms from anchor text. Boost these tokens in the logit bias map.

```java
public record AnchorConstraint(
        String anchorId,
        Authority authority,
        Set<String> boostTokens,
        Set<String> suppressTokens,
        double translationCoverage
) {}
```

`AnchorConstraintIndex` builds constraints via:
1. Split anchor text into tokens (whitespace + punctuation boundary)
2. Identify capitalized words as entity candidates
3. Filter stop words and common verbs
4. Remaining tokens become `boostTokens`
5. `translationCoverage` = fraction of anchor text tokens that became constraints

**Why entity name biasing**: High precision, low false-enforcement risk. Boosting "Baron Krell" tokens cannot change the intended meaning. More aggressive strategies (keyword extraction, negation-aware extraction) have higher false-enforcement risk and are deferred per O1.

**Alternative rejected**: LLM-assisted constraint generation (per-anchor LLM call adds latency; may hallucinate constraints). Deferred as a future enhancement.

### 2. Logit Bias API Design (O2 Resolution)

`LogitBiasMap` encapsulates the OpenAI-compatible bias representation:

```java
public record LogitBiasMap(
        Map<String, Integer> tokenBiases,
        int constraintCount,
        double coverage,
        int overflowCount
) {
    public static final int MAX_TOKENS = 300;
    public static final int CANON_BIAS = 100;
    public static final int RELIABLE_BIAS = 50;
}
```

Key design choices:
- **Token keys are strings** (not integer IDs): The actual tokenizer mapping is model-specific. `LogitBiasMap` stores human-readable tokens; the API integration layer maps to integer IDs. This keeps the core logic model-agnostic.
- **Authority-tiered bias values**: CANON = 100 (maximum), RELIABLE = 50 (moderate). These values are constants on the record, not configuration — the authority-to-strength mapping is a locked decision (L4).
- **Overflow handling**: When constraints exceed 300 tokens, CANON constraints take priority. `overflowCount` tracks how many tokens were dropped. A warning is logged.

**Why string keys**: Integer token IDs are model-specific (GPT-4 tokenizer differs from GPT-3.5). Keeping the core representation model-agnostic isolates the tokenizer concern to the API integration boundary.

### 3. ConstrainedDecodingEnforcer Interface (O3 Resolution)

Interface-only. Documents the contract for future implementation:

```java
public interface ConstrainedDecodingEnforcer extends ComplianceEnforcer {
    ConstraintMask computeConstraintMask(AnchorConstraintIndex index, int vocabSize);
}

public record ConstraintMask(
        boolean[] allowedTokens,
        int constraintCount,
        int vocabularySize
) {}
```

The contract: at each decoding step, tokens where `allowedTokens[i] == false` have logits set to negative infinity. This is the STATIC architecture adapted to the dice-anchors domain.

A `NoOpConstrainedDecodingEnforcer` stub is provided for testing — returns an unconstrained mask (all tokens allowed) and a compliant result.

**Why interface-only**: Local model infrastructure (vLLM custom samplers, HF LogitsProcessor) does not exist in the current architecture. Defining the interface now ensures readiness when infrastructure materializes, per L3.

### 4. Hybrid Enforcement Composition

`HybridComplianceEnforcer` chains three layers:

```
┌─────────────────────┐
│  Prompt Injection    │  Always active (zero cost)
│  (PromptInjection-   │  → ComplianceResult #1
│   Enforcer)          │
├─────────────────────┤
│  Logit Bias          │  Active if model supports it
│  (LogitBiasEnforcer) │  → ComplianceResult #2
├─────────────────────┤
│  Post-Gen Validation │  Active if HYBRID strategy
│  (PostGeneration-    │  → ComplianceResult #3
│   Validator)         │
└─────────────────────┘
        ↓
Combined ComplianceResult
```

Result aggregation:
- `compliant` = all layers compliant
- `violations` = union of all layer violations
- `suggestedAction` = most severe action across layers (REJECT > RETRY > ACCEPT)
- `validationDuration` = sum of all layer durations

`ModelCapabilityDetector` checks whether the target model supports logit bias. Known patterns:
- OpenAI: `gpt-4*`, `gpt-3.5*`, `o1*`, `o3*` — supports logit bias
- Anthropic: `claude-*` — does not support logit bias
- Unknown: defaults to unsupported (safe degradation per I7)

### 5. Configuration and Strategy Selection

Add to `DiceAnchorsProperties.AssemblyConfig`:

```java
public record AssemblyConfig(
        @Min(0) @DefaultValue("0") int promptTokenBudget,
        @DefaultValue("false") boolean adaptiveFootprintEnabled,
        @DefaultValue("PROMPT_ONLY") EnforcementStrategy enforcementStrategy
) {}
```

```java
public enum EnforcementStrategy {
    PROMPT_ONLY,
    LOGIT_BIAS,
    HYBRID
}
```

Default is PROMPT_ONLY per L5. No behavioral change without explicit opt-in.

Scenario YAML gains an optional `enforcementStrategy` field:
```yaml
id: adversarial-contradictory
enforcementStrategy: LOGIT_BIAS   # optional, defaults to PROMPT_ONLY
# ... rest of scenario
```

### 6. Observability

Structured logging emitted by all enforcement operations:
- `compliance.strategy` — which strategy executed (PROMPT_ONLY, LOGIT_BIAS, HYBRID)
- `compliance.logit_bias.token_count` — number of biased tokens
- `compliance.logit_bias.constraint_count` — number of anchor constraints translated
- `compliance.logit_bias.overflow` — tokens dropped due to 300-token limit
- `compliance.constraints.translation_coverage` — fraction of anchors expressible as token constraints
- `compliance.capability.logit_bias_supported` — whether the model supports logit bias

## Risks / Trade-offs

| Risk | Mitigation |
|------|-----------|
| **Semantic-to-token translation is lossy** (~30-40% coverage) | Entity name biasing is intentionally conservative. `translationCoverage` metric tracks the gap. Untranslatable anchors fall back to prompt injection. |
| **OpenAI 300-token limit** | Authority-priority truncation (CANON first). `overflowCount` logged. Sufficient for typical anchor sets (20 budget × ~5 tokens/anchor = ~100 tokens). |
| **False enforcement (bias changes meaning)** | Entity name biasing has near-zero false enforcement risk. Boosting "Baron Krell" cannot change the sentence's meaning. More aggressive strategies deferred. |
| **Constrained decoding infrastructure never materializes** | Interface is defined regardless. `LogitBiasEnforcer` provides immediate value. Constrained decoding is explicitly deferred per L3. |
| **Model capability detection is heuristic-based** | Known model patterns checked first. Unknown models default to unsupported (safe). No hard failures per I7. |

## Migration Plan

1. Create `AnchorConstraint` record and `AnchorConstraintIndex` (isolated, no refactoring)
2. Create `EnforcementStrategy` enum and add to `DiceAnchorsProperties.AssemblyConfig`
3. Create `LogitBiasMap` record and `LogitBiasEnforcer`
4. Create `ConstraintMask` record and `ConstrainedDecodingEnforcer` interface with stub
5. Create `ModelCapabilityDetector` and `HybridComplianceEnforcer`
6. Add `enforcementStrategy` to scenario YAML schema
7. Add compliance rate metric to `ScoringService`
8. Update `application.yml` with default enforcement strategy

No breaking changes. Default strategy (PROMPT_ONLY) preserves current behavior. Rollback is removing the new implementations — the interface and existing enforcers are unaffected.

## Open Questions

- Should `LogitBiasMap` token keys eventually use a proper tokenizer (tiktoken)? Out of scope for initial delivery; the string-key approach is sufficient for entity name biasing.
- Should constraint translation coverage be surfaced in the UI (BenchmarkView)? SHOULD be implemented as a follow-up.
