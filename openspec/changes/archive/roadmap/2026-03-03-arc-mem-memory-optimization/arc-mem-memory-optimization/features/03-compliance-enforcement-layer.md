# Feature: Compliance Enforcement Layer

## Feature ID

`F03`

## Summary

Define a `ComplianceEnforcer` interface that abstracts context unit compliance enforcement, decoupling enforcement strategy from prompt assembly. Implement `PostGenerationValidator` as the immediate strategy. The interface MUST support future strategies (logit bias, constrained decoding) without API changes.

## RFC 2119 Compliance

All normative statements in this document use RFC 2119 keywords (`MUST`, `MUST NOT`, `SHALL`, `SHOULD`, `SHOULD NOT`, `MAY`, and their negations). Non-normative guidance uses plain language.

## Why This Feature

1. **Problem addressed**: Context Unit compliance is currently probabilistic -- context units are injected into the system prompt via `ArcMemLlmReference` and the LLM may or may not respect them. There is no verification layer. A response that contradicts a CANON context unit is indistinguishable from one that complies, until the drift evaluator catches it after the fact (in simulation) or it goes undetected (in chat).
2. **Value delivered**: An explicit compliance enforcement abstraction that makes compliance strategy pluggable. The immediate implementation (post-generation validation) catches CANON violations before they reach the user. The interface enables future deterministic enforcement (Prolog invariant inference, constrained decoding via F12) without API changes.
3. **Why now**: Wave 1 interface definition. F12 (constraint-aware decoding) is the long-term goal but requires local model infrastructure. Building the abstraction now ensures the medium-term implementation (post-generation validation) doesn't hardcode assumptions that block F12.

## Scope

### In Scope

1. `ComplianceEnforcer` interface: `enforce(ComplianceContext) -> ComplianceResult`.
2. `ComplianceContext` record: agent response text, active context units (with authority levels), compliance policy configuration.
3. `ComplianceResult` record: compliant (boolean), violations (list of context unit ID + violation description), suggested action (ACCEPT, RETRY, REJECT).
4. `PromptInjectionEnforcer` implementation: current behavior -- inject context units into system prompt. Always returns ACCEPT (no verification).
5. `PostGenerationValidator` implementation: validate response against CANON/RELIABLE context units after generation, flag violations.
6. Authority-based strictness: CANON context units MUST be enforced; RELIABLE SHOULD be enforced; lower authorities MAY be enforced (configurable).
7. Integration point: enforcement wraps the LLM call pipeline so enforcement happens automatically.
8. **Future strategy: Prolog invariant enforcement** (Wave 2-3). The `ComplianceEnforcer` interface MAY be implemented by a `PrologInvariantEnforcer` that expresses context unit invariants as Prolog rules and queries for violations deterministically via DICE's tuProlog projection (`PrologEngine.query()`). This provides a mid-tier between lexical checks (fast/crude) and full LLM validation (slow/accurate) — deterministic logical inference at near-zero cost. Already on classpath via DICE 0.1.0-SNAPSHOT (tuProlog / 2p-kt). No new dependencies REQUIRED.
9. **A/B testability**: Every `ComplianceEnforcer` implementation (including future `PrologInvariantEnforcer`) MUST be selectable per-simulation via scenario YAML or properties. The simulator MUST support running the same scenario with different enforcement strategies for direct comparison (e.g., `PostGenerationValidator` vs. `PrologInvariantEnforcer` vs. both composed).

### Out of Scope

1. Logit bias enforcement (future strategy, requires API support).
2. Constrained decoding enforcement (F12, requires local model infrastructure).
3. Prolog invariant enforcement implementation (future strategy, Wave 2-3). Interface accommodation is in scope; implementation is not.
4. Automatic retry on violation (callers decide retry policy).
5. UI for compliance configuration (properties/YAML configuration only).
6. Modifications to `ArcMemLlmReference` prompt assembly logic.

## Dependencies

1. Feature dependencies: none.
2. Priority: MUST.
3. OpenSpec change slug: `compliance-enforcement-layer`.
4. Research rec: J (interface definition; full constrained decoding deferred to F12).
5. Runtime dependency (future Prolog strategy): DICE 0.1.0-SNAPSHOT provides tuProlog (2p-kt) for Prolog projection. Already on classpath — zero new dependencies for the Prolog enforcement path.

## Research Requirements

None for the interface and post-generation validator. STATIC research (recommendation J) informs the future constrained decoding strategy but is not required for this feature. DICE Prolog projection (tuProlog / 2p-kt) is already available on the classpath and SHOULD be evaluated as a future enforcement strategy — see In Scope item 8.

## Impacted Areas

1. **`assembly/` package (primary)**: New types -- `ComplianceEnforcer` (interface), `ComplianceContext` (record), `ComplianceResult` (record), `ComplianceAction` (enum: ACCEPT, RETRY, REJECT), `PromptInjectionEnforcer` (current behavior wrapper), `PostGenerationValidator` (new validation strategy). Future: `PrologInvariantEnforcer` (Wave 2-3).
2. **`chat/` package**: `ChatActions` MAY integrate enforcement into the response pipeline.
3. **`sim/engine/` package**: `SimulationTurnExecutor` MAY enforce compliance during simulation turns.
4. **`ArcMemProperties`**: New `compliance` config section with enforcement strategy, authority-level strictness, and validator parameters.
5. **DICE Prolog integration (future)**: `PrologInvariantEnforcer` would project active context units as Prolog facts and express invariant rules (e.g., authority constraints, rank bounds, mutual exclusion) as Prolog clauses. Violation queries run via `PrologEngine.query()` / `queryAll()`. No new package — lives in `assembly/` alongside other enforcers.

## Visibility Requirements

### UI Visibility

1. Compliance check results MAY be displayed in RunInspectorView when enforcement is active during simulation.
2. Violation details (which context unit was contradicted, response excerpt) SHOULD be visible in context traces.

### Observability Visibility

1. Compliance enforcement MUST emit structured log events: `compliance.check` with strategy, context unit count checked, result (compliant/violated), and duration.
2. Violations MUST be logged at WARN level with context unit ID, authority level, violation description, and suggested action.
3. Enforcement strategy MUST be logged at application startup: `compliance.enforcer.strategy=PROMPT_INJECTION|POST_GENERATION`.
4. Compliance metrics SHOULD be trackable: checks per response, violation rate, retry count.

## Acceptance Criteria

1. `ComplianceEnforcer` interface MUST abstract over enforcement strategy with a single `enforce(ComplianceContext) -> ComplianceResult` method.
2. `PostGenerationValidator` MUST detect when a response contradicts CANON context units.
3. `PostGenerationValidator` SHOULD detect contradictions against RELIABLE context units when configured to do so.
4. Compliance results MUST be observable via structured logging and lifecycle events.
5. Current prompt injection behavior MUST continue working as the default strategy via `PromptInjectionEnforcer`.
6. Enforcement strictness MUST be configurable per authority level via `ArcMemProperties`.
7. The `ComplianceEnforcer` interface MUST accommodate future strategies (logit bias, constrained decoding, Prolog invariant enforcement) without breaking changes.
8. `PostGenerationValidator` MUST NOT add latency to the happy path (compliant responses) beyond the validation LLM call.
9. `ComplianceResult` MUST include sufficient detail for callers to decide on retry vs. accept vs. reject.

## Risks and Mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|------------|
| **False-positive violations** | Medium | Medium | Post-generation validation may reject valid responses that touch unit-adjacent topics without contradicting them. Mitigation: configurable strictness; CANON-only enforcement as default; confidence threshold on violations. |
| **Validation latency** | Medium | Medium | Post-generation validation requires an LLM call to compare response against context units. Mitigation: validation SHOULD be parallelizable with response streaming. Batch context units into a single validation prompt. |
| **Interface lock-in** | Low | High | Once F12 consumers depend on `ComplianceEnforcer`, changing the interface is costly. Mitigation: keep the interface minimal (single method). `ComplianceContext` and `ComplianceResult` are records -- add fields, don't change existing ones. |
| **Enforcement bypass in chat flow** | Medium | Medium | If enforcement is not wired into the chat pipeline, violations go undetected. Mitigation: enforcement integration MUST be tested end-to-end in both chat and simulation paths. |

## Proposal Seed

### Change Slug

`compliance-enforcement-layer`

### Proposal Starter Inputs

1. **Problem statement**: Context Unit compliance is currently probabilistic. Context Units are injected into the system prompt and the LLM may or may not respect them. There is no verification layer. Research (STATIC) shows constrained decoding can make compliance deterministic, but that requires local model infrastructure. In the interim, post-generation validation can catch violations.
2. **Why now**: Defines the interface that F12 (constraint-aware decoding) will implement. Building the abstraction now prevents the medium-term implementation from hardcoding assumptions that block the long-term goal.
3. **Constraints/non-goals**: MUST NOT add latency to the happy path (compliant responses). Validation SHOULD be parallelizable with response streaming. No logit bias or constrained decoding (future strategies). No automatic retry logic.
4. **Visible outcomes**: Compliance violations logged with context unit ID and violation description. Enforcement strategy logged at startup. Violation rate trackable as a metric.

### Suggested Capability Areas

1. **Enforcer interface**: Strategy-agnostic compliance enforcement contract.
2. **Post-generation validation**: LLM-based response validation against active context units.
3. **Authority-based strictness**: Configurable enforcement levels per authority tier.
4. **Pipeline integration**: Enforcement wired into chat and simulation response paths.

### Candidate Requirement Blocks

1. **REQ-INTERFACE**: The system SHALL define a `ComplianceEnforcer` interface with a single `enforce(ComplianceContext) -> ComplianceResult` method.
2. **REQ-VALIDATE**: The `PostGenerationValidator` SHALL detect when agent responses contradict CANON context units.
3. **REQ-STRICTNESS**: Enforcement strictness SHALL be configurable per authority level.
4. **REQ-DEFAULT**: The `PromptInjectionEnforcer` SHALL preserve current behavior as the default strategy.
5. **REQ-EXTENSIBLE**: The interface SHALL support future enforcement strategies (including Prolog invariant enforcement, logit bias, constrained decoding) without breaking changes.

## Validation Plan

1. **Unit tests** MUST verify `PostGenerationValidator` detects CANON contradictions in synthetic response text.
2. **Unit tests** MUST verify `PromptInjectionEnforcer` always returns ACCEPT (backward compatibility).
3. **Unit tests** MUST verify authority-based strictness configuration (CANON enforced, RELIABLE optional, lower ignored).
4. **Unit tests** MUST verify `ComplianceResult` captures violation details (context unit ID, description, action).
5. **Integration test** SHOULD verify enforcement in a simulation turn where the DM response contradicts a CANON context unit.
6. **Observability validation** MUST confirm compliance events are logged with correct attributes.
7. **Regression**: Current chat and simulation behavior MUST NOT change when `PromptInjectionEnforcer` is the active strategy.

## Known Limitations

1. **Post-generation validation requires an LLM call.** This adds latency to every enforced response. The cost is justified only for CANON/RELIABLE context units, not the full context unit set. A future `PrologInvariantEnforcer` (Wave 2-3) would provide deterministic enforcement at near-zero cost for rule-expressible invariants, filling the gap between prompt injection (zero verification) and LLM validation (expensive verification).
2. **Validation accuracy depends on LLM quality.** The validator may miss subtle contradictions or flag paraphrases as violations. Accuracy calibration is a follow-up concern.
3. **No streaming integration.** Post-generation validation operates on complete responses. Streaming enforcement (cancel mid-generation on violation) is architecturally different and out of scope.
4. **Single-response scope.** The validator checks one response at a time. Multi-turn consistency (detecting contradictions that emerge across several responses) is not addressed.
5. **Prolog enforcement scope is bounded by expressible rules.** Not all context unit compliance constraints are expressible as Prolog rules. Semantic contradictions (paraphrase, implication) require LLM judgment. Prolog enforcement is best suited for structural invariants (authority constraints, rank bounds, mutual exclusion, domain rules).

## Suggested Command

```
/opsx:new compliance-enforcement-layer
```
