## ADDED Requirements

### Requirement: ComplianceEnforcer interface (REQ-INTERFACE)

The system SHALL define a `ComplianceEnforcer` interface in `dev.dunnam.diceanchors.assembly` annotated with `@FunctionalInterface`. The interface SHALL have a single method:

```java
ComplianceResult enforce(ComplianceContext context);
```

`ComplianceEnforcer` MUST NOT be sealed. The interface is intentionally open to allow future enforcement strategies (Prolog invariant inference, logit bias, constrained decoding via F12) without API changes.

All implementations MUST be thread-safe. `enforce()` MAY be called concurrently from multiple simulation threads or chat sessions.

#### Scenario: Interface is a functional interface

- **GIVEN** the `ComplianceEnforcer` interface
- **WHEN** inspected via reflection
- **THEN** it SHALL have exactly one abstract method (`enforce`) and be annotated with `@FunctionalInterface`

#### Scenario: Interface is not sealed

- **GIVEN** the `ComplianceEnforcer` interface
- **WHEN** a new class implements `ComplianceEnforcer`
- **THEN** no permits clause or sealed modifier SHALL prevent the implementation

---

### Requirement: ComplianceAction enum (REQ-ACTIONS)

The system SHALL define a `ComplianceAction` enum in `dev.dunnam.diceanchors.assembly` with exactly three values:

| Value | Meaning |
|-------|---------|
| `ACCEPT` | Response is compliant; proceed normally |
| `RETRY` | Response has violations; suggest regeneration |
| `REJECT` | Response has serious violations; do not deliver |

The enum MUST NOT contain additional values. Callers use the suggested action to decide how to handle the validated response without inspecting violation details directly.

#### Scenario: All action values present

- **GIVEN** the `ComplianceAction` enum
- **WHEN** `ComplianceAction.values()` is called
- **THEN** it SHALL return exactly `[ACCEPT, RETRY, REJECT]`

---

### Requirement: ComplianceViolation record (REQ-VIOLATION)

The system SHALL define a `ComplianceViolation` record in `dev.dunnam.diceanchors.assembly` with the following components:

| Component | Type | Description |
|-----------|------|-------------|
| `anchorId` | `String` | Neo4j node ID of the violated anchor |
| `anchorText` | `String` | Proposition text of the violated anchor (for readability in logs/UI) |
| `anchorAuthority` | `Authority` | Authority level of the violated anchor; drives action escalation |
| `description` | `String` | Human-readable description of the violation |
| `confidence` | `double` | Validator confidence in this violation (0.0--1.0); LLM-sourced |

The record MUST capture sufficient detail for callers to log violations, display them in the UI, and make retry/reject decisions based on authority level.

#### Scenario: Violation captures authority for action escalation

- **GIVEN** a `ComplianceViolation` with `anchorAuthority = CANON`
- **WHEN** a caller inspects the violation
- **THEN** the caller SHALL have access to `anchorAuthority()` to determine the severity of the violation

---

### Requirement: ComplianceContext record (REQ-CONTEXT)

The system SHALL define a `ComplianceContext` record in `dev.dunnam.diceanchors.assembly` with the following components:

| Component | Type | Description |
|-----------|------|-------------|
| `responseText` | `String` | The LLM response text to validate |
| `activeAnchors` | `List<Anchor>` | Active anchors to check the response against |
| `policy` | `CompliancePolicy` | Strictness configuration controlling which authority tiers are enforced |

`ComplianceContext` SHALL contain a nested `CompliancePolicy` record.

**Important**: This `CompliancePolicy` (nested in `ComplianceContext`, `assembly/` package) is DISTINCT from the `CompliancePolicy` interface in the `anchor/` package. The `anchor/CompliancePolicy` maps authority to compliance *strength* for prompt rendering. The `assembly/ComplianceContext.CompliancePolicy` controls which authority tiers are *enforced* during post-generation validation. These are complementary concepts.

#### Scenario: Context provides all inputs for enforcement

- **GIVEN** a `ComplianceContext` with responseText, activeAnchors, and policy
- **WHEN** passed to `ComplianceEnforcer.enforce()`
- **THEN** the enforcer SHALL have access to all three components needed for validation

---

### Requirement: CompliancePolicy nested record (REQ-POLICY)

The `ComplianceContext.CompliancePolicy` record SHALL have the following components:

| Component | Type | Default | Description |
|-----------|------|---------|-------------|
| `enforceCanon` | `boolean` | `true` | Validate responses against CANON anchors |
| `enforceReliable` | `boolean` | varies | Validate responses against RELIABLE anchors |
| `enforceUnreliable` | `boolean` | `false` | Validate responses against UNRELIABLE anchors |
| `enforceProvisional` | `boolean` | `false` | Validate responses against PROVISIONAL anchors |

`CompliancePolicy` SHALL provide factory methods:

- `canonOnly()` — returns `new CompliancePolicy(true, false, false, false)`. Suitable for production with minimal overhead.
- `tiered()` — returns `new CompliancePolicy(true, true, false, false)`. Stricter coverage for high-stakes contexts.

#### Scenario: canonOnly enforces only CANON

- **GIVEN** `CompliancePolicy.canonOnly()`
- **WHEN** the policy is inspected
- **THEN** `enforceCanon()` SHALL return `true` and all others SHALL return `false`

#### Scenario: tiered enforces CANON and RELIABLE

- **GIVEN** `CompliancePolicy.tiered()`
- **WHEN** the policy is inspected
- **THEN** `enforceCanon()` and `enforceReliable()` SHALL return `true`, and `enforceUnreliable()` and `enforceProvisional()` SHALL return `false`

---

### Requirement: ComplianceResult record (REQ-RESULT)

The system SHALL define a `ComplianceResult` record in `dev.dunnam.diceanchors.assembly` with the following components:

| Component | Type | Description |
|-----------|------|-------------|
| `compliant` | `boolean` | `true` when no violations were detected |
| `violations` | `List<ComplianceViolation>` | Violations found; empty when compliant |
| `suggestedAction` | `ComplianceAction` | Recommended action for the caller |
| `validationDuration` | `Duration` | Wall-clock time consumed by validation |

`ComplianceResult` SHALL provide a static factory:

- `compliant(Duration duration)` — returns `new ComplianceResult(true, List.of(), ComplianceAction.ACCEPT, duration)`. Used by zero-cost enforcers and as the fast-path when no anchors need enforcement.

#### Scenario: Compliant factory returns ACCEPT with empty violations

- **GIVEN** a call to `ComplianceResult.compliant(Duration.ofMillis(5))`
- **WHEN** the result is inspected
- **THEN** `compliant()` SHALL return `true`, `violations()` SHALL be empty, `suggestedAction()` SHALL be `ACCEPT`, and `validationDuration()` SHALL be 5ms

#### Scenario: Non-compliant result has violations and action

- **GIVEN** a `ComplianceResult` with `compliant = false`, one CANON violation, and `suggestedAction = REJECT`
- **WHEN** the caller inspects the result
- **THEN** `violations()` SHALL contain the violation and `suggestedAction()` SHALL guide the caller's response

---

### Requirement: PromptInjectionEnforcer default implementation (REQ-DEFAULT)

The system SHALL define a `PromptInjectionEnforcer` class in `dev.dunnam.diceanchors.assembly` implementing `ComplianceEnforcer`. It SHALL be annotated with `@Primary` and `@Component`.

`PromptInjectionEnforcer.enforce()` SHALL always return `ComplianceResult.compliant(Duration.ZERO)` regardless of the input context. This preserves the existing behavior: anchors are injected into the system prompt via `AnchorsLlmReference`, and no post-generation verification is performed.

The `@Primary` annotation ensures Spring selects this implementation by default. Callers requiring stricter enforcement SHOULD use `@Qualifier` to inject a specific enforcer.

Zero overhead. Zero LLM calls beyond the primary generation.

#### Scenario: Always returns compliant

- **GIVEN** a `PromptInjectionEnforcer` instance
- **AND** any `ComplianceContext` (including one with CANON anchors and contradicting response text)
- **WHEN** `enforce(context)` is called
- **THEN** the result SHALL have `compliant = true`, `violations` empty, `suggestedAction = ACCEPT`, and `validationDuration = Duration.ZERO`

#### Scenario: Default Spring bean selection

- **GIVEN** a Spring context with both `PromptInjectionEnforcer` and `PostGenerationValidator` registered
- **WHEN** `ComplianceEnforcer` is injected without `@Qualifier`
- **THEN** `PromptInjectionEnforcer` SHALL be selected due to `@Primary`

---

### Requirement: PostGenerationValidator LLM-based enforcement (REQ-VALIDATOR)

The system SHALL define a `PostGenerationValidator` class in `dev.dunnam.diceanchors.assembly` implementing `ComplianceEnforcer`. It SHALL be annotated with `@Component`.

`PostGenerationValidator` SHALL:

1. Filter `context.activeAnchors()` by `context.policy()`, retaining only anchors whose authority level is enabled in the policy
2. If no anchors remain after filtering, return `ComplianceResult.compliant(duration)` immediately (fast-path)
3. Build a single validation prompt containing all filtered anchors and the response text
4. Call the LLM via `ChatModel` with the validation prompt
5. Parse the LLM response as JSON to extract violations
6. Construct `ComplianceViolation` records by correlating parsed violation entries with the enforced anchors
7. Determine the suggested `ComplianceAction` based on violation severity
8. Return a `ComplianceResult` with all violations, the suggested action, and the validation duration

`PostGenerationValidator` MUST use constructor injection for `ChatModel`.

#### Scenario: Detects CANON contradiction

- **GIVEN** a CANON anchor "The guardian is a warrior"
- **AND** a response text "The guardian, a powerful wizard, cast a spell"
- **WHEN** `enforce()` is called with `CompliancePolicy.canonOnly()`
- **THEN** the result SHALL contain at least one violation referencing the CANON anchor

#### Scenario: Fast-path when no anchors match policy

- **GIVEN** only PROVISIONAL anchors in `activeAnchors`
- **AND** `CompliancePolicy.canonOnly()`
- **WHEN** `enforce()` is called
- **THEN** the result SHALL be compliant with zero LLM calls

---

### Requirement: CANON violations trigger REJECT (REQ-CANON-REJECT)

`PostGenerationValidator` SHALL suggest `REJECT` when any violation targets a CANON anchor. For violations targeting only lower-authority anchors (RELIABLE, UNRELIABLE, PROVISIONAL), the validator SHALL suggest `RETRY`.

The rationale: CANON anchors are world-defining and immune to automatic demotion (invariant A3b). A response that contradicts a CANON anchor is a fundamental compliance failure that SHOULD NOT be retried — the LLM is unlikely to self-correct without additional prompt engineering.

#### Scenario: CANON violation triggers REJECT

- **GIVEN** violations containing at least one with `anchorAuthority = CANON`
- **WHEN** `determineAction()` evaluates the violations
- **THEN** the result SHALL be `REJECT`

#### Scenario: RELIABLE-only violation triggers RETRY

- **GIVEN** violations where all have `anchorAuthority = RELIABLE`
- **WHEN** `determineAction()` evaluates the violations
- **THEN** the result SHALL be `RETRY`

#### Scenario: No violations triggers ACCEPT

- **GIVEN** an empty violations list
- **WHEN** `determineAction()` evaluates the violations
- **THEN** the result SHALL be `ACCEPT`

---

### Requirement: Lenient LLM response parsing (REQ-PARSE-LENIENT)

All JSON models used to parse LLM validation responses MUST use `@JsonIgnoreProperties(ignoreUnknown = true)`. LLMs frequently include extra fields (e.g., `"reasoning"`, `"explanation"`) that are not part of the expected schema.

This is a lesson learned from the `BatchConflictResult` parse failure (see Mistakes Log, 2026-02-27): LLM output parsers that reject unknown fields cause silent failures when the LLM includes helpful but unexpected annotations.

If JSON parsing fails entirely, the validator SHOULD treat the response as compliant (fail-open) and log a warning. False negatives (missed violations) are preferable to false positives (blocking valid responses due to parse errors).

#### Scenario: Extra fields in LLM response

- **GIVEN** a validation LLM response containing `{"violations": [], "reasoning": "looks good"}`
- **WHEN** the response is parsed
- **THEN** parsing SHALL succeed and the extra `"reasoning"` field SHALL be ignored

#### Scenario: Malformed JSON fails open

- **GIVEN** a validation LLM response containing unparseable text
- **WHEN** parsing fails
- **THEN** the result SHALL be treated as compliant and a warning SHALL be logged

---

### Requirement: Thread safety (REQ-THREAD-SAFE)

All `ComplianceEnforcer` implementations MUST be thread-safe. `enforce()` MAY be called concurrently from multiple simulation threads or chat sessions.

- `PromptInjectionEnforcer` is inherently thread-safe (stateless, returns a constant).
- `PostGenerationValidator` is thread-safe as a stateless Spring component — `ChatModel` and `ObjectMapper` handle their own concurrency.

#### Scenario: Concurrent enforcement calls

- **GIVEN** two threads calling `enforce()` on the same `PostGenerationValidator` instance simultaneously
- **WHEN** both calls complete
- **THEN** each SHALL return independent results with no interference or shared mutable state

## Invariants

- **CE1**: `ComplianceEnforcer` SHALL remain a `@FunctionalInterface` with exactly one abstract method. Adding methods MUST be done via default methods only.
- **CE2**: `PromptInjectionEnforcer` SHALL always return `compliant = true`. It MUST NOT perform any validation logic.
- **CE3**: `PostGenerationValidator` SHALL suggest `REJECT` for CANON violations and `RETRY` for lower-authority violations. This mapping MUST NOT be inverted.
- **CE4**: All LLM response parsing models MUST use `@JsonIgnoreProperties(ignoreUnknown = true)`.
- **CE5**: Enforcement MUST NOT modify anchor state (rank, authority, pinned status). Enforcers are read-only validators.
