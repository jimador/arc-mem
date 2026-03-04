# Prep: Compliance Enforcement Layer

**Feature**: F03 — `compliance-enforcement-layer`
**Wave**: 1
**Priority**: MUST
**Depends on**: none

## RFC 2119 Compliance

All normative statements use RFC 2119 keywords.

## Locked Decisions

1. **ComplianceEnforcer interface**: Single method: `enforce(ComplianceContext) -> ComplianceResult`. Strategy pattern -- implementations are interchangeable.
2. **PostGenerationValidator as first implementation**: Validates agent response text against active anchors (authority-filtered) using an LLM call. Returns violations with anchor ID, description, and suggested action.
3. **Authority-based strictness**: CANON anchors MUST be enforced. RELIABLE anchors SHOULD be enforced (configurable). PROVISIONAL and UNRELIABLE anchors are NOT enforced by default.
4. **PromptInjectionEnforcer preserves current behavior**: Wraps the existing `AnchorsLlmReference` prompt injection. Always returns `ComplianceResult(compliant=true, violations=[], action=ACCEPT)`. This is the default strategy -- zero behavioral change.
5. **ComplianceResult is actionable**: Includes `compliant` boolean, `violations` list (each with anchor ID + description), and `suggestedAction` enum (`ACCEPT`, `RETRY`, `REJECT`). Callers decide how to act.
6. **No automatic retry**: The enforcer reports violations. The caller (chat actions, sim turn executor) decides whether to retry, accept, or reject. Retry policy is not part of the enforcement layer.
7. **A/B testability**: All `ComplianceEnforcer` implementations MUST be selectable per-simulation. Scenario YAML MUST support specifying the enforcement strategy (e.g., `complianceStrategy: POST_GENERATION` vs. `complianceStrategy: PROLOG_INVARIANT`). This enables direct comparison of enforcement approaches on the same adversarial scenarios.

## Open Questions

| # | Question | Options | Leaning | Resolution Trigger |
|---|----------|---------|---------|-------------------|
| 1 | **Integration point** | (a) Enforcement wraps `ChatActions` response pipeline only. (b) Enforcement wraps `SimulationTurnExecutor` LLM call only. (c) Enforcement wraps both via a shared `EnforcedLlmCallService`. (d) Enforcement is called explicitly by each consumer. | (d) Explicit calls. Each consumer (`ChatActions`, `SimulationTurnExecutor`) calls `ComplianceEnforcer.enforce()` after receiving the LLM response. This avoids a hidden wrapper and keeps the integration point visible. | Design phase. Evaluate whether a shared wrapper reduces boilerplate enough to justify the indirection. |
| 2 | **Retry policy on violation** | (a) No retry -- report violation, caller decides. (b) Configurable max retries with re-generation. (c) Retry with the violation appended to the next prompt ("your response contradicted X, please correct"). | (a) No retry in this feature. Retry logic is caller-specific (chat may retry interactively, sim may log and continue). Retry-with-feedback is a candidate extension. | Design phase. May revisit when F07 (proactive maintenance) adds response quality requirements. |
| 3 | **Streaming compatibility** | (a) Validate complete response only (post-stream). (b) Validate incrementally during streaming (cancel on violation). (c) Buffer stream, validate, then release (adds latency). | (a) Post-stream validation. Incremental validation requires partial-response conflict detection, which is significantly more complex. Post-stream is sufficient for the initial implementation. | Design phase. Streaming enforcement is a long-term consideration for F12. |
| 4 | **Validation prompt design** | (a) Single prompt comparing response against all enforced anchors. (b) Per-anchor validation (one check per CANON anchor). (c) Batch anchors into authority-grouped prompts. | (a) Single prompt. Batch all enforced anchors into one validation prompt. Per-anchor validation is too many LLM calls; authority grouping adds complexity without clear benefit. | Spec phase. May need prompt engineering iteration. |
| 5 | **Prolog invariant enforcement (Wave 2-3)** | (a) Express all enforceable anchor invariants as Prolog rules; use DICE's tuProlog projection to query for violations deterministically. (b) Prolog for structural invariants only; LLM for semantic compliance. (c) Defer entirely to F12. | (b) Prolog for structural invariants (authority constraints, rank bounds, mutual exclusion, domain rules); LLM for semantic compliance (contradiction, paraphrase). This gives a three-tier enforcement spectrum: prompt injection (zero cost, zero verification) → Prolog inference (near-zero cost, deterministic for expressible rules) → LLM validation (high cost, semantic understanding). | Wave 2-3 design phase. Requires DICE Prolog projection API familiarity. tuProlog (2p-kt) is already on classpath via DICE 0.1.0-SNAPSHOT. |

## Visibility Contract

| Surface | What | When | Format |
|---------|------|------|--------|
| RunInspectorView | Compliance check result per turn | When enforcement is active during simulation | Badge: `COMPLIANT` / `VIOLATION` + details |
| Structured logs | `compliance.check` | Each enforcement call | INFO: strategy, anchor count, result, duration |
| Structured logs | `compliance.violation` | Each detected violation | WARN: anchor ID, authority, violation description, suggested action |
| Startup logs | `compliance.enforcer.strategy` | Application startup | INFO: `PROMPT_INJECTION` or `POST_GENERATION` |
| Metrics (future) | Compliance rate, violation rate, retry count | Aggregated | Counter/gauge (deferred to observability integration) |

## Acceptance Gates

| Gate | Verification | Command |
|------|-------------|---------|
| PostGenerationValidator detects CANON contradictions | Unit test: response text "The guardian is a wizard" against CANON anchor "The guardian is a warrior" -> violation detected. | `./mvnw test -pl . -Dtest=PostGenerationValidatorTest` |
| Interface supports future strategies without changes | Verify `ComplianceEnforcer` interface has a single method; adding `LogitBiasEnforcer` requires only a new implementation class, no interface changes. | Code review (structural verification) |
| PromptInjectionEnforcer preserves current behavior | Unit test: `PromptInjectionEnforcer.enforce()` returns `compliant=true, action=ACCEPT` regardless of input. | `./mvnw test -pl . -Dtest=PromptInjectionEnforcerTest` |
| Authority-based strictness configurable | Unit test: with `compliance.strictness.reliable=false`, RELIABLE anchors are excluded from validation. | `./mvnw test -pl . -Dtest=PostGenerationValidatorTest` |

## Small-Model Constraints

- **Max 4 files per task** (interface + implementations + config + tests)
- **Verification**: `./mvnw test` MUST pass after each task
- **No changes to AnchorsLlmReference**: Prompt assembly logic is untouched
- **Scope boundary**: `assembly/` package for enforcer types; consumers are separate tasks

## Task Sketch

| # | Scope | Files | Gate |
|---|-------|-------|------|
| T1 | `ComplianceEnforcer` interface + `ComplianceContext` + `ComplianceResult` + `ComplianceAction` records | `ComplianceEnforcer.java`, `ComplianceContext.java`, `ComplianceResult.java`, `ComplianceAction.java` | Records compile, interface is single-method |
| T2 | `PromptInjectionEnforcer` (default, always-compliant wrapper) | `PromptInjectionEnforcer.java`, `PromptInjectionEnforcerTest.java` | Always returns ACCEPT |
| T3 | `PostGenerationValidator` with authority-filtered anchor validation | `PostGenerationValidator.java`, `PostGenerationValidatorTest.java` | Detects CANON contradictions |
| T4 | `DiceAnchorsProperties` compliance config + Spring wiring | `DiceAnchorsProperties.java` (update), compliance config section | Strategy selectable, strictness configurable |

## Enforcement Strategy Spectrum

The `ComplianceEnforcer` interface SHOULD accommodate a three-tier enforcement spectrum (Wave 1 implements tiers 1-2; tier 3 is Wave 2-3):

| Tier | Strategy | Cost | Verification | Wave |
|------|----------|------|-------------|------|
| 1 | `PromptInjectionEnforcer` | Zero | None (trust LLM compliance) | 1 |
| 2 | `PostGenerationValidator` | One LLM call per response | Semantic (LLM judges contradiction) | 1 |
| 3 | `PrologInvariantEnforcer` | Near-zero (in-process Prolog query) | Deterministic for rule-expressible invariants | 2-3 |
| 4 | Constrained decoding (F12) | Integrated into generation | Deterministic at token level | Future |

**Prolog enforcement details**: DICE 0.1.0-SNAPSHOT includes tuProlog (2p-kt) for Prolog projection — already on classpath with zero new dependencies. Propositions are projected to Prolog facts; invariant rules are expressed as Prolog clauses; violation queries run via `PrologEngine.query()` / `queryAll()` / `findAll()`. Example invariants expressible as Prolog rules:
- Authority hierarchy constraints (e.g., CANON anchors MUST NOT be contradicted by PROVISIONAL propositions)
- Rank bound enforcement (rank MUST be in [100, 900])
- Mutual exclusion rules (domain-specific: "a character cannot be in two locations simultaneously")
- Budget limit enforcement (active anchor count MUST NOT exceed configured maximum)

Prolog enforcement fills the gap between prompt injection (zero verification) and LLM validation (expensive, probabilistic). For invariants expressible as logical rules, it provides deterministic enforcement at near-zero cost.

## Risks Requiring Design Attention

1. **Validation prompt quality**: The PostGenerationValidator's effectiveness depends entirely on the quality of the validation prompt. Prompt engineering and testing against realistic responses is critical. Include at least 5 test cases covering: clear contradiction, subtle paraphrase, compliant response, partial contradiction, ambiguous response.
2. **LLM call cost**: Every enforced response adds one LLM call. For simulation runs with 20 turns, this is 20 additional calls. Ensure the validation prompt is efficient (single call, not per-anchor). A future `PrologInvariantEnforcer` (Wave 2-3) would eliminate LLM cost for rule-expressible invariants.
3. **Prolog rule expressiveness boundary**: Not all compliance constraints are expressible as Prolog rules. Semantic contradictions (paraphrase, implication, context-dependent meaning) require LLM judgment. The enforcement strategy SHOULD be composable — `PrologInvariantEnforcer` handles structural invariants, `PostGenerationValidator` handles semantic compliance, and both can be active simultaneously.
