## Why

Anchor compliance is currently implicit and probabilistic. Anchors are injected into the system prompt via `AnchorsLlmReference` and the LLM is trusted to comply. There is no verification layer. A response that contradicts a CANON anchor is indistinguishable from one that complies, until the drift evaluator catches it after the fact (in simulation) or it goes undetected (in chat).

This gap is unacceptable for high-stakes anchors. CANON anchors represent world-defining facts that MUST NOT be contradicted. Without explicit compliance enforcement, the system has:

- **No response verification**: The LLM receives anchor-injected prompts but nothing checks whether the response actually respects those anchors. Compliance is assumed, not verified.
- **No authority-aware enforcement**: CANON anchors deserve stricter enforcement than PROVISIONAL anchors, but all anchors receive the same treatment — prompt injection with no post-generation check.
- **No enforcement spectrum**: The architecture supports only one enforcement strategy (prompt injection). Future strategies like constrained decoding (F12) or Prolog invariant inference have no integration point.

Research context: The Google AI STATIC paper (2024) demonstrated sparse matrix constrained decoding for deterministic structured output generation. While STATIC-style constrained decoding requires local model infrastructure (out of scope), the *enforcement spectrum* concept — from zero-cost prompt injection through one-LLM-call validation to deterministic constrained decoding — provides the architectural framing for this change.

## What Changes

Add a **Compliance Enforcement Layer** — a strategy-based subsystem in the `assembly/` package that validates LLM responses against active anchors. The layer defines a three-tier enforcement spectrum and implements the first two tiers.

### Core types (assembly/ package)

- **`ComplianceEnforcer`**: `@FunctionalInterface` with single method `enforce(ComplianceContext) -> ComplianceResult`. Intentionally non-sealed to allow future strategies (Prolog invariant inference, logit bias, constrained decoding).
- **`ComplianceAction`**: Enum — `ACCEPT`, `RETRY`, `REJECT`. Recommended action for the caller.
- **`ComplianceViolation`**: Record — `anchorId`, `anchorText`, `anchorAuthority`, `description`, `confidence`. Captures a single violation with enough detail for logging, UI display, and caller decision-making.
- **`ComplianceContext`**: Record — `responseText`, `activeAnchors`, `policy`. Contains a nested `CompliancePolicy` record with per-authority enforcement toggles (`enforceCanon`, `enforceReliable`, `enforceUnreliable`, `enforceProvisional`) and factory methods (`canonOnly()`, `tiered()`).
- **`ComplianceResult`**: Record — `compliant`, `violations`, `suggestedAction`, `validationDuration`. Includes a `compliant()` factory for zero-cost enforcers.
- **`PromptInjectionEnforcer`**: `@Primary @Component`. Default enforcer that always returns compliant. Preserves existing behavior — zero behavioral change, zero overhead.
- **`PostGenerationValidator`**: `@Component`. LLM-based post-generation validation. Filters anchors by policy, sends a single validation prompt, parses violations. Suggests `REJECT` for CANON violations, `RETRY` for lower authority.

### Enforcement spectrum

| Tier | Strategy | Cost | Verification | Status |
|------|----------|------|-------------|--------|
| 1 | `PromptInjectionEnforcer` | Zero | None (trust LLM) | This change |
| 2 | `PostGenerationValidator` | One LLM call | Semantic (LLM judges contradiction) | This change |
| 3 | `PrologInvariantEnforcer` | Near-zero | Deterministic for rule-expressible invariants | Future (Wave 2-3) |
| 4 | Constrained decoding (F12) | Integrated | Deterministic at token level | Future |

### Important distinction

The `assembly/` package `CompliancePolicy` (nested in `ComplianceContext`) and the `anchor/` package `CompliancePolicy` (interface) are **different concepts**:

- **`anchor/CompliancePolicy`**: Maps authority levels to compliance *strengths* for prompt rendering. Controls how strongly the LLM is *instructed* to respect anchors. Used by `AnchorsLlmReference` during prompt assembly.
- **`assembly/ComplianceContext.CompliancePolicy`**: Controls which authority levels are *enforced* during post-generation validation. Determines which anchors are checked, not how they're presented.

These are complementary: one controls the input (prompt), the other controls the output (validation).

## Capabilities

### New Capabilities
- `compliance-enforcement`: Response validation against active anchors with configurable enforcement strategies spanning the prompt injection to constrained decoding spectrum.

### Modified Capabilities
- None. This is purely additive. No existing behavior changes.

## Impact

### New files
- `src/main/java/dev/dunnam/diceanchors/assembly/ComplianceAction.java`
- `src/main/java/dev/dunnam/diceanchors/assembly/ComplianceViolation.java`
- `src/main/java/dev/dunnam/diceanchors/assembly/ComplianceContext.java`
- `src/main/java/dev/dunnam/diceanchors/assembly/ComplianceResult.java`
- `src/main/java/dev/dunnam/diceanchors/assembly/ComplianceEnforcer.java`
- `src/main/java/dev/dunnam/diceanchors/assembly/PromptInjectionEnforcer.java`
- `src/main/java/dev/dunnam/diceanchors/assembly/PostGenerationValidator.java`

### Modified files
- None. All types are new additions to the `assembly/` package.

### Constitutional alignment
- **Article I (RFC 2119)**: Compliant. All normative statements use RFC 2119 keywords.
- **Article II (Neo4j only)**: Compliant. No new persistence. Enforcement is stateless — validates a response, returns a result.
- **Article III (Constructor injection)**: Compliant. `PostGenerationValidator` uses constructor injection for `ChatModel`.
- **Article IV (Records)**: Compliant. `ComplianceViolation`, `ComplianceContext`, `ComplianceContext.CompliancePolicy`, and `ComplianceResult` are all records.
- **Article V (Anchor invariants)**: Not affected. Enforcement reads anchor state but does not modify it.
- **Article VI (Sim isolation)**: Not affected. Enforcers are stateless; context isolation is maintained by callers.
- **Article VII (Test-first)**: Tests accompany implementation.
