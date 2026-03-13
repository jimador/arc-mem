# Context Unit Memory Optimization Roadmap

## Intent

Systematically adopt techniques from Sleeping LLM (wake/sleep memory consolidation) and Google AI STATIC (sparse matrix constrained decoding) research to optimize context units' context unit lifecycle, conflict detection, maintenance, and compliance enforcement. The initiative works backwards from transformative long-term goals (deterministic compliance, interference-aware budgeting) to ensure foundational interfaces are designed first, even when full implementations are deferred.

Key principle: **interface-first, implement-later**. Wave 1 defines API contracts that Wave 3–4 features will consume, ensuring medium-term implementations don't create technical debt that blocks long-term ambitions.

## RFC 2119 Compliance

Normative requirements in this roadmap use RFC 2119 keywords only (`MUST`, `SHOULD`, `MAY`, and their negations).

## Research Configuration

- `Research mode`: off (research phase complete)
- `Research documents`:
  - `openspec/research/llm-optimization-external-research.md` — Sleeping LLM + Google AI STATIC applicability analysis
  - `openspec/research/dependency-research-memory-optimization.md` — 3rd-party dependency evaluation (libraries, frameworks, DICE Prolog)

## Scope

### In Scope

1. All 11 recommendations (A–K) from `openspec/research/llm-optimization-external-research.md`, plus consolidated DICE Prolog integration (F13)
2. Validation-gated rollback framework (user requirement, inspired by sleeping-llm + Spring @Transactional)
3. Maintenance API rework to support both reactive (current) and proactive models, testable in simulator
4. Interface definitions for long-term features even when full implementation is deferred
5. Consolidation with overlapping features from `context units-working-memory-evolution` roadmap

### Out of Scope

1. Local model hosting infrastructure (required for full constrained decoding but out of scope for this project)
2. Multi-agent context unit governance (covered by `unit-working-memory-research` roadmap)
3. DICE upstream framework changes (covered by `context units-working-memory-evolution` F08)
4. UI redesign beyond visibility requirements for new features

## Constraints

1. Constitution Article V: Context Unit invariants (A1–A4) MUST be preserved through all optimizations
2. Constitution Article II: Neo4j as sole persistence authority
3. Constitution Article VI: Simulation isolation via contextId
4. All interface changes MUST maintain backward compatibility with existing simulation scenarios
5. LLM call reduction MUST NOT sacrifice conflict detection accuracy below current baseline
6. New maintenance strategies MUST be A/B testable in the simulation harness
7. Every Prolog-backed implementation MUST have a non-Prolog counterpart behind the same interface, selectable per-simulation for A/B comparison

## Consolidation with Existing Roadmaps

### context units-working-memory-evolution

| Existing Feature | This Roadmap Feature | Decision | Rationale |
|---|---|---|---|
| F01: Working Memory Tiering Core | F11: Tiered Context Unit Storage | **Superseded** | Our F11 is research-backed (STATIC hybrid dense/sparse) with concrete access pattern optimization; existing F01 is skeleton-level |
| F02: Conflict Detection Calibration | F05: Precomputed Conflict Index | **Complementary** | Existing F02 focuses on accuracy calibration; our F05 focuses on performance (O(1) lookup). Both needed. F05 depends on calibration being solid. |
| F05: Compaction Recovery Guardrails | F01: Context Unit Transaction Framework (deferred) | **Complementary** | Our F01 would provide a general @Transactional-style rollback subsuming compaction recovery, but is deferred. Existing compaction guardrails remain the active solution. |

Existing features not listed (F03 Retrieval, F04 Bi-Temporal, F06 Benchmarking, F07 Invariants, F08 DICE Upstream) are unaffected by this roadmap.

## Proposal Waves

| Wave | Feature ID | Feature Slug | Priority | Depends On | Visibility | OpenSpec Change Slug | Research Rec |
|------|------------|--------------|----------|------------|------------|----------------------|--------------|
| 1 | F02 | unified-maintenance-strategy | MUST | none | UI + Observability | unified-maintenance-strategy | A (partial) |
| 1 | F03 | compliance-enforcement-layer | MUST | none | Observability | compliance-enforcement-layer | J (interface) |
| 2 | F04 | memory-pressure-gauge | MUST | F02 | UI + Observability | memory-pressure-gauge | K |
| 2 | F05 | precomputed-conflict-index | SHOULD | none | Observability | precomputed-conflict-index | F, I |
| 2 | F06 | promotion-pipeline-optimization | SHOULD | F05 | Observability | promotion-pipeline-optimization | E, H |
| 3 | F07 | proactive-maintenance-cycle | MUST | F02, F04 | UI + Observability | proactive-maintenance-cycle | A (full) |
| 3 | F08 | proposition-quality-scoring | SHOULD | none | Observability | proposition-quality-scoring | B |
| 3 | F09 | adaptive-prompt-footprint | SHOULD | none | Observability | adaptive-prompt-footprint | D |
| 4 | F10 | interference-density-budget | MAY | F05 | UI + Observability | interference-density-budget | C |
| 4 | F11 | tiered-unit-storage | MAY | F02 | Observability | tiered-unit-storage | G |
| 4 | F12 | constraint-aware-decoding | MAY | F03 | Observability | constraint-aware-decoding | J (full) |
| 4 | F13 | prolog-integration-layer | SHOULD | F03, F05, F07 | Observability | prolog-integration-layer | Cross-cutting |

## Sequencing Rationale

### Backwards from long-term goals

The roadmap is planned backwards from three transformative long-term capabilities:

1. **Deterministic compliance** (F12): Requires a `ComplianceEnforcer` interface (F03, Wave 1) that abstracts over enforcement strategies. Post-generation validation is the immediate strategy; constrained decoding is the eventual one.

2. **Proactive maintenance** (F07): Requires a unified maintenance strategy interface (F02, Wave 1) that supports reactive and proactive models, AND a pressure gauge (F04, Wave 2) to trigger cycles. A full transaction/rollback framework (F01) is deferred as too heavy for a PoC — but APIs SHOULD be designed so transactional behavior can be layered on later without breaking changes.

3. **Interference-aware budgeting** (F10): Requires a precomputed conflict index (F05, Wave 2) that captures context unit relationships. Without pre-indexed relationships, interference density calculation would require O(N²) LLM calls.

### Wave logic

- **Wave 1 (Foundation)**: Interface definitions + minimal implementations. These 2 features reshape the API surface. DecayPolicy/ReinforcementPolicy become strategies within MaintenanceStrategy. Compliance becomes an explicit layer rather than implicit prompt injection. All existing behavior continues working through backward-compatible default strategies. APIs SHOULD be designed to accommodate future transactional behavior (F01, deferred) without requiring rework.

- **Wave 2 (Instrumentation)**: Build the measurement and indexing infrastructure. Memory pressure quantifies system health. Conflict index eliminates redundant LLM calls. Promotion pipeline optimization chains these together for measurable throughput gains.

- **Wave 3 (Intelligence)**: Implement the smart features that consume Wave 1 interfaces and Wave 2 infrastructure. Proactive maintenance is the headline feature — the sleeping-llm-inspired audit cycle. Quality scoring and adaptive footprint are complementary optimizations.

- **Wave 4 (Transformative)**: Long-term features where the interface exists (from Wave 1) but full implementation requires additional infrastructure or research. Interference-density budget needs clustering algorithms. Tiered storage needs repository layer changes. Constrained decoding needs local model support or logit bias API access. Prolog integration layer (F13) consolidates the cross-cutting DICE Prolog work — establishing proposition-to-fact projection and implementing Prolog-backed alternatives for existing interfaces (LOGICAL conflict detection, audit pre-filter, invariant enforcer), enabling A/B comparison against heuristic/LLM counterparts. These are implemented when the ecosystem supports them.

### Simulator as proving ground

The unified maintenance strategy (F02) is designed specifically so both reactive (current) and proactive (new) models can be configured per-simulation. This enables direct A/B comparison of maintenance approaches using existing adversarial scenarios, producing quantitative evidence for which model better preserves context unit integrity under stress.

## Feature Documents

1. `openspec/roadmaps/unit-memory-optimization/features/01-unit-transaction-framework.md` **(DEFERRED — future enhancement)**
2. `openspec/roadmaps/unit-memory-optimization/features/02-unified-maintenance-strategy.md`
3. `openspec/roadmaps/unit-memory-optimization/features/03-compliance-enforcement-layer.md`
4. `openspec/roadmaps/unit-memory-optimization/features/04-memory-pressure-gauge.md`
5. `openspec/roadmaps/unit-memory-optimization/features/05-precomputed-conflict-index.md`
6. `openspec/roadmaps/unit-memory-optimization/features/06-promotion-pipeline-optimization.md`
7. `openspec/roadmaps/unit-memory-optimization/features/07-proactive-maintenance-cycle.md`
8. `openspec/roadmaps/unit-memory-optimization/features/08-proposition-quality-scoring.md`
9. `openspec/roadmaps/unit-memory-optimization/features/09-adaptive-prompt-footprint.md`
10. `openspec/roadmaps/unit-memory-optimization/features/10-interference-density-budget.md`
11. `openspec/roadmaps/unit-memory-optimization/features/11-tiered-unit-storage.md`
12. `openspec/roadmaps/unit-memory-optimization/features/12-constraint-aware-decoding.md`
13. `openspec/roadmaps/unit-memory-optimization/features/13-prolog-integration-layer.md`

## Prep Documents

1. `openspec/roadmaps/unit-memory-optimization/prep/01-unit-transaction-framework-prep.md` **(DEFERRED)**
2. `openspec/roadmaps/unit-memory-optimization/prep/02-unified-maintenance-strategy-prep.md`
3. `openspec/roadmaps/unit-memory-optimization/prep/03-compliance-enforcement-layer-prep.md`
4. `openspec/roadmaps/unit-memory-optimization/prep/04-memory-pressure-gauge-prep.md`
5. `openspec/roadmaps/unit-memory-optimization/prep/05-precomputed-conflict-index-prep.md`
6. `openspec/roadmaps/unit-memory-optimization/prep/06-promotion-pipeline-optimization-prep.md`
7. `openspec/roadmaps/unit-memory-optimization/prep/07-proactive-maintenance-cycle-prep.md`
8. `openspec/roadmaps/unit-memory-optimization/prep/08-proposition-quality-scoring-prep.md`
9. `openspec/roadmaps/unit-memory-optimization/prep/09-adaptive-prompt-footprint-prep.md`
10. `openspec/roadmaps/unit-memory-optimization/prep/10-interference-density-budget-prep.md`
11. `openspec/roadmaps/unit-memory-optimization/prep/11-tiered-unit-storage-prep.md`
12. `openspec/roadmaps/unit-memory-optimization/prep/12-constraint-aware-decoding-prep.md`
13. `openspec/roadmaps/unit-memory-optimization/prep/13-prolog-integration-layer-prep.md`

## Change Scaffolds

| Feature ID | Change Slug | Scaffold Status | Path |
|------------|-------------|-----------------|------|
| F01 | unit-transaction-framework | **deferred** | `openspec/changes/unit-transaction-framework/` (scaffold removed) |
| F02 | unified-maintenance-strategy | created | `openspec/changes/unified-maintenance-strategy/` |
| F03 | compliance-enforcement-layer | created | `openspec/changes/compliance-enforcement-layer/` |
| F04 | memory-pressure-gauge | created | `openspec/changes/memory-pressure-gauge/` |
| F05 | precomputed-conflict-index | created | `openspec/changes/precomputed-conflict-index/` |
| F06 | promotion-pipeline-optimization | created | `openspec/changes/promotion-pipeline-optimization/` |
| F07 | proactive-maintenance-cycle | created | `openspec/changes/proactive-maintenance-cycle/` |
| F08 | proposition-quality-scoring | created | `openspec/changes/proposition-quality-scoring/` |
| F09 | adaptive-prompt-footprint | created | `openspec/changes/adaptive-prompt-footprint/` |
| F10 | interference-density-budget | created | `openspec/changes/interference-density-budget/` |
| F11 | tiered-unit-storage | created | `openspec/changes/tiered-unit-storage/` |
| F12 | constraint-aware-decoding | created | `openspec/changes/constraint-aware-decoding/` |
| F13 | prolog-integration-layer | created | `openspec/changes/prolog-integration-layer/` |

## Cross-Cutting Dependencies

### Dependency Research

Full analysis: `openspec/research/dependency-research-memory-optimization.md`

### Libraries to Add

| Library | Maven Artifact | Managed By | Features Served | Wave |
|---------|---------------|------------|-----------------|------|
| **Caffeine** | `com.github.ben-manes.caffeine:caffeine` | Spring Boot BOM (3.2.3) | F11 (HOT tier cache) | 4 |
| **spring-boot-starter-actuator** | `org.springframework.boot:spring-boot-starter-actuator` | Spring Boot BOM | F04 (metrics endpoint, optional) | 2 |

### Already Available (No New Dependencies)

| Library | How Available | Features Served |
|---------|--------------|-----------------|
| **DICE Prolog (tuProlog/2p-kt)** | Transitive via DICE 0.1.0-SNAPSHOT | F03, F05, F07, F08, F10 |
| Micrometer | Transitive via Spring Boot | F04 |
| AttentionWindow | Existing codebase | F04 |
| Neo4j / Drivine | Direct dependency | F05 |
| Guava | Transitive via Embabel Agent | F06 |
| Neo4j vector indexes | Direct dependency | F08 |

### DICE Prolog as Cross-Cutting Capability

DICE 0.1.0-SNAPSHOT includes experimental Prolog projection via tuProlog (2p-kt). Propositions are projected to Prolog facts; queries run via `PrologEngine.query()`, `queryAll()`, `findAll()`. Currently zero Prolog usage in context units.

Prolog serves as a **fast, deterministic pre-filter** that reduces expensive LLM calls across 5 features.

**A/B testability constraint**: Every Prolog-backed implementation MUST have a corresponding non-Prolog implementation behind the same interface. The simulator MUST be able to select between Prolog and standard implementations per-run, enabling direct A/B comparison of Prolog vs. LLM-only (or heuristic-only) approaches. This follows the same principle as the reactive/proactive maintenance A/B testing (F02).

| Feature | Prolog Implementation | Standard Counterpart | Shared Interface |
|---------|----------------------|---------------------|------------------|
| F03 | `PrologInvariantEnforcer` — deterministic rule-based invariant checking | `PostGenerationValidator` — LLM-based semantic validation | `ComplianceEnforcer` |
| F05 | LOGICAL strategy — Prolog backward chaining for logical contradictions | SEMANTIC strategy — LLM-based conflict detection | `ConflictDetectionStrategy` in `CompositeConflictDetector` |
| F07 | `PrologAuditPreFilter` — deterministic contradiction chain detection | LLM-only audit — full batched LLM relevance evaluation | Toggleable pre-filter flag in `ProactiveMaintenanceStrategy` |
| F08 | `PrologRelationshipScorer` — logical entailment/support inference | Heuristic or embedding-based scoring | `TrustSignal` (or scorer interface) |
| F10 | Prolog transitive closure — recursive cluster membership | Connected components or DBSCAN clustering | `InterferenceDensityCalculator` (pluggable) |

The investment is in writing Prolog rules and projection mappings, not in adding libraries. Rules SHOULD be extensible per `DomainProfile`.

### Rejected Frameworks

| Framework | Reason |
|-----------|--------|
| Timefold Solver | Premature — greedy algorithms near-optimal for ~20 unit budget |
| Drools / KIE 10.1.0 | Too heavy (~30 JARs); DICE Prolog provides backward chaining already |
| Evrete 4.1.02 | Forward chaining only; Prolog strictly more capable and already available |

## Global Risks

1. **Interface churn in Wave 1**: Defining interfaces before implementation risks over-engineering or misfit. Mitigation: interfaces SHOULD be minimal (smallest viable contract) and validated against at least one concrete use case before Wave 2 begins.
2. **Maintenance strategy A/B testing complexity**: Running two fundamentally different maintenance models in the same simulator requires careful state isolation. Mitigation: reuse existing contextId isolation; each strategy operates within its sim run independently.
3. **Precomputed conflict index staleness**: Conflict relationships change as context units are promoted/archived. Mitigation: incremental update on lifecycle events, not batch rebuild.
4. **Compliance enforcement false positives**: Post-generation validation may reject valid responses that happen to touch unit-adjacent topics. Mitigation: configurable enforcement strictness; CANON-only enforcement as default.
5. **Scope creep from 13 features**: Risk of losing focus. Mitigation: Wave 1 (3 features) is the critical path; Waves 2–4 can be re-prioritized based on simulator evidence.

## Exit Criteria

Roadmap is complete when:

1. Every feature has a doc with proposal seed content
2. Dependencies are explicit and acyclic
3. Each feature has measurable acceptance criteria
4. Each feature has at least one visibility channel
5. Each feature has a proposal seed and `/opsx:new` command
6. Each feature is ready for `openspec new change <change-slug>`
7. Consolidation decisions with existing roadmaps are documented

## Suggested Proposal Commands

1. `/opsx:new unified-maintenance-strategy`
2. `/opsx:new compliance-enforcement-layer`
4. `/opsx:new memory-pressure-gauge`
5. `/opsx:new precomputed-conflict-index`
6. `/opsx:new promotion-pipeline-optimization`
7. `/opsx:new proactive-maintenance-cycle`
8. `/opsx:new proposition-quality-scoring`
9. `/opsx:new adaptive-prompt-footprint`
10. `/opsx:new interference-density-budget`
11. `/opsx:new tiered-unit-storage`
12. `/opsx:new constraint-aware-decoding`
13. `/opsx:new prolog-integration-layer`

## Known Limitations

1. **Constraint-aware decoding (F12) requires local model infrastructure** that context units does not currently have. The compliance enforcement layer (F03) provides the interface; F12 is implementable only when the ecosystem supports token-level control. Impact: deterministic compliance remains aspirational until then. Post-generation validation (F03) is the pragmatic near-term solution.

2. **Interference-density budget (F10) requires semantic clustering** that may itself need LLM calls, partially offsetting the LLM-call savings from precomputed conflict detection. Impact: net LLM-call reduction depends on cluster stability. Candidate approaches: Prolog transitive closure (zero new dependencies) or Apache Commons Math DBSCAN (external dependency). Candidate follow-up: cache cluster assignments, recompute on context unit topology changes only.

3. **Proactive maintenance (F07) introduces scheduling complexity** that reactive-only systems avoid. A badly-tuned maintenance cycle could degrade performance by running sweeps too frequently. Impact: default configuration MUST be conservative (high pressure threshold). Candidate follow-up: adaptive scheduling based on sweep outcome metrics.

4. **Context Unit Transaction Framework (F01) deferred**. Full @Transactional-style rollback is too heavy for a PoC. Impact: proactive maintenance (F07) and bulk operations lack atomic rollback. Mitigation: APIs in F02 and F07 SHOULD be designed so transactional behavior can be layered on later — e.g., maintenance sweep methods accept an optional snapshot/restore callback, even if the default is a no-op. The feature doc is preserved as a future enhancement specification.
