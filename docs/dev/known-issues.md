# Known Issues

<!-- sync: openspec/specs/anchor-conflict, openspec/specs/anchor-trust, openspec/specs/anchor-lifecycle, openspec/specs/share-ready-implementation-gate -->
<!-- last-synced: 2026-02-25 -->

Confirmed defects, operational limitations, and open research uncertainty. dice-anchors is an exploration-quality demo, not a production-hardened memory system. Items are separated into confirmed limitations (known defects with mitigations), credibility risks (risk register from adversarial review), and open research uncertainty (not defects -- areas requiring further investigation).

---

## 1. Confirmed Limitations

### L1: Trust evaluation -- uncalibrated thresholds

| Field | Value |
|-------|-------|
| Severity | Medium |
| Impact | Domain profiles (`BALANCED`, `SECURE`, `NARRATIVE`) are manually tuned by inspection. Thresholds are not calibrated against labeled data. Profile behavior may be brittle across model versions and domains. |
| Mitigation | Profiles are functional for demo scenarios. Manual tuning provides reasonable defaults. |
| Follow-up | Build labeled calibration set; run grid-search or Bayesian threshold tuning per profile; publish confidence intervals for false-promote and false-reject rates. |

### L2: Compaction -- detect-only, no retry

| Field | Value |
|-------|-------|
| Severity | Medium |
| Impact | `CompactionValidator` detects loss of protected facts but does not retry or recover. Degraded context quality persists until operator intervenes. |
| Mitigation | Detection alerts operator to failures. Existing context is not replaced with lower-validity output. |
| Follow-up | Add retry with stricter regeneration constraints; add extractive fallback that preserves protected facts. |

### L3: Token estimation heuristic

| Field | Value |
|-------|-------|
| Severity | Low |
| Impact | `CharHeuristicTokenCounter` uses fixed chars-per-token ratio. Estimated counts can diverge from model reality. Strict budget guarantees are weaker. |
| Mitigation | Acceptable for rough simulation and demo use. |
| Follow-up | Use model-specific tokenizer or provider token-count endpoints for strict budget paths; record estimate-vs-actual deltas. |

### L4: Single-threaded simulation UI

| Field | Value |
|-------|-------|
| Severity | Low |
| Impact | UI runs one scenario at a time, limiting throughput for larger experiment sets. |
| Mitigation | Benchmark runner supports batch execution outside UI. |
| Follow-up | Add headless batch runner for parallel scenario execution; keep UI single-run but consume batch outputs. |

### L5: Statistical rigor -- no CIs, non-deterministic

| Field | Value |
|-------|-------|
| Severity | High |
| Impact | Single runs are non-deterministic. Comparisons can be overfit to one run. Claim confidence is weak without repeated trials. |
| Mitigation | Effect-size reporting exists. Users are warned that single runs are not statistically meaningful. |
| Follow-up | Add repeated-run benchmark protocol with confidence intervals; persist experiment manifests (model/version/prompt/scenario hashes). |

### L6: Auto-generated adversarial messages -- LLM quality variance

| Field | Value |
|-------|-------|
| Severity | Medium |
| Impact | Random strategy/fact selection can produce weak or incoherent attacks, inflating apparent robustness. |
| Mitigation | Scripted scenarios provide deterministic baseline coverage. |
| Follow-up | Add curated attack templates by tactic class; add red-team quality filters before accepting generated turns. |

### L7: Neo4j dependency

| Field | Value |
|-------|-------|
| Severity | Low |
| Impact | Running Neo4j instance required for normal operation. Local quick-start friction for users without Docker. |
| Mitigation | `docker-compose.yml` provides single-command Neo4j setup. |
| Follow-up | Offer lightweight local mode for exploration; provide fixture-backed testing profile. |

### L8: Cross-session persistence scope

| Field | Value |
|-------|-------|
| Severity | Low |
| Impact | Anchors are scoped to per-session contexts. No user-facing long-term continuity across app restarts. |
| Mitigation | By design for demo isolation. Sim contexts use `sim-{uuid}` and clean up after. |
| Follow-up | Add optional cross-session memory profile; add retention policy and archive/restore controls. |

---

## 2. Credibility Risks

Risk register from adversarial harness audit. Status updates reflect implementation changes where applicable.

### Blocking Risks

| ID | Risk | Status | Evidence | Invalidation Path | Mitigation |
|----|------|--------|----------|--------------------|------------|
| R1 | No fixed RNG seed for generated/adaptive attacks | Open | `SimulationService.generateAdversarialMessage()` | Cross-run variance cannot be attributed to condition differences | Add scenario-level seed support; persist effective seed per run |
| R2 | Scenario config knobs not fully enforced end-to-end | Open | `SimulationScenario` carries overrides; execution may not manifest all | Reported comparisons may imply config deltas that did not apply | Emit effective config manifest per run; fail if overrides not applied |
| R3 | Conflict parse failure fail-open | **RESOLVED** (task 3.1) | `LlmConflictDetector.parseBatchConflictResponse()` | Contradictory propositions promoted on parse failure | Parse failures now route to degraded/review handling, not auto-accept |
| R4 | Drift judge not calibrated against human labels | Open | `SimulationTurnExecutor.evaluateDrift()` | Judge drift masquerades as system improvement | Build adjudication set; report judge agreement (kappa); run cross-judge checks |
| R5 | `NO_TRUST` ablation missing | Open | `AblationCondition` lacks `NO_TRUST` | Cannot isolate trust contribution | Implement `NO_TRUST` before final claims |

### High Risks

| ID | Risk | Status | Impact | Mitigation |
|----|------|--------|--------|------------|
| R6 | Run-to-run instability with winner sign flips | Open | Direction of effect not stable; "material improvement" not yet defendable | Increase repetitions; require direction stability before claim |
| R7 | Scripted and generated/adaptive attacks mixed in evidence | Open | Comparisons sensitive to generation randomness | Separate deterministic claim pack from stochastic stress pack |
| R8 | Composite resilience score loses discriminative power under heavy contradictions | Open | `max(0, 100 - meanContradictions*20)` clips to zero | Report primary conclusions on raw metrics; composite score is secondary |
| R9 | Narrative/report wording can overstate evidence quality | **Partially addressed** (tasks 4.1-4.3) | Auto-generated summary text from small-N stats; hard-coded positioning block | Provisional labeling and instability flags added; further CI and N reporting needed |
| R10 | Strategy coverage broad but incomplete/non-uniform | Open | 3 strategies uncovered: `GRADUAL_EROSION`, `IMPLICATION_REVERSAL`, `CAUSAL_CHAIN_EROSION` | Add coverage accounting; expand holdout strategy families |
| R11 | Silent drift under-instrumented | Open | Verdict schema is contradiction-centric | Add omission/substitution checks for objective and policy continuity |
| R12 | Run provenance manifest incomplete | **Partially addressed** (tasks 4.1-4.3) | `SimulationRunRecord` lacks prompt hashes/model IDs/config hash | Provenance fields added; full manifest with hashes still needed |

### Medium Risks

| ID | Risk | Impact | Mitigation |
|----|------|--------|------------|
| R13 | Synthetic domain concentration (tabletop-narrative heavy) | External transfer unproven | Add non-narrative scenario packs (ops/support/compliance) |
| R14 | Metrics gamed by evasive non-answers (`NOT_MENTIONED` avoids contradiction penalties) | Apparent robustness hides utility collapse | Add utility/completeness penalties and refusal tracking |
| R15 | Parallel post-response mode timing confounds | Sequential vs parallel behavior may not be comparable | Pin execution mode in experiments; include in manifest |

---

## 3. Open Research Uncertainty

These are not defects. They are areas where further investigation is required before strong claims can be made.

### Calibration and Policy Tuning

Budget, decay, authority thresholds, and profile cutoffs are hand-tuned. Decision policy may not be stable across models and domains. Requires: calibration workflow with labeled data, profile-specific threshold recommendation reports, periodic re-calibration as model versions change.

### Adversarial Methodology and Red-Team Coverage

Current scenarios emphasize direct contradiction and reframing. Coordinated multi-turn and gradual drift attacks are underrepresented. Requires: explicit adversarial taxonomy (`setup`, `build`, `payoff`, `drift`), automated red-team generation with quality gates, attack efficacy distributions rather than per-run outcomes only.

### Cross-Model Generalization

Most testing has centered on OpenAI model behavior. Anchor compliance may vary with instruction-following strength by model family. Requires: model matrix benchmark (at least 3 model families), per-model policy/prompt compatibility notes, compatibility checks before claiming generalized robustness.
