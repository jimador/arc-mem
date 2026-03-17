# CLAUDE.md

## Project

**arc-mem** — Test bed for ARC-Mem (Activation-Ranked Context Memory) units (enriched DICE Propositions with activation score, authority, and budget management) as a working memory model for long-horizon attention stability and hallucination/contradiction control (validated with adversarial stress prompts). Working reference for DICE <-> ARC-Mem integration. Java 25 / Spring Boot 3.5.10 / Embabel Agent 0.3.5-SNAPSHOT / DICE 0.1.0-SNAPSHOT / Vaadin 24.6.4 / Neo4j 5.x (Drivine ORM).

Two-module Maven project (`arcmem-core` + `arcmem-simulator`).

## RFC 2119 Keyword Compliance

This document uses keywords per [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt):

| Keyword | Meaning |
|---------|---------|
| **MUST** / **REQUIRED** / **SHALL** | Absolute requirement. Non-compliance is a defect. |
| **MUST NOT** / **SHALL NOT** | Absolute prohibition. Violation is a defect. |
| **SHOULD** / **RECOMMENDED** | Strong recommendation. Deviation requires documented justification. |
| **SHOULD NOT** / **NOT RECOMMENDED** | Strong recommendation against. Deviation requires documented justification. |
| **MAY** / **OPTIONAL** | Truly optional. No justification needed for omission. |

**For LLM Agents**: When executing tasks, treat MUST/SHALL/REQUIRED as blocking requirements. Do not proceed if these cannot be satisfied. SHOULD items should be implemented unless explicitly overridden in the specification.

## How I Work

- **Delegate aggressively** -- Use Task tool subagents for any work that would bloat main context (exploration, multi-file reads, research, implementation of isolated components). Keep main context for orchestration and user communication.
- **Investigate before solving** -- Check specs, existing code, and memory before proposing solutions.
- **Plan when uncertain** -- Use `/plan` for anything non-trivial. Don't guess at architecture.
- **Stop when stuck** -- Don't thrash. Explain what was tried and why it failed.
- **Fix autonomously** -- Bug reports, failing tests, error logs -> fix them without hand-holding.
- **Verify before done** -- Run tests, demonstrate correctness. Don't claim "done" without proof.
- **Capture lessons immediately** -- Update Mistakes Log after any correction, before continuing.

## Subagent Strategy

**Default workflow: Task tool subagents (NOT full agent teams).** Teams (TeamCreate) are reserved for special occasions — they use credits too fast.

**Always delegate to subagents (Task tool):**
- Codebase exploration -- use `subagent_type=Explore` for broad searches
- Multi-file implementation of isolated components -- use `subagent_type=general-purpose`
- Research tasks -- design patterns, library APIs, framework behavior
- Any work that would consume significant context window

**Keep in main context:**
- User communication and clarification
- Orchestration decisions
- Small targeted edits (< 3 files, clear scope)
- Git operations (commits, PRs, branch management)
- Test writing

## Commands

```bash
# Build (skip tests)
./mvnw clean compile -DskipTests

# Test
./mvnw test

# ── Host development (Neo4j in Docker, app on host) ──
docker compose up -d
OPENAI_API_KEY=sk-... ./mvnw spring-boot:run
# Then: http://localhost:8089 (sim view), http://localhost:8089/chat, http://localhost:8089/benchmark

# ── Full-stack Docker (Neo4j + Langfuse + app, all containerized) ──
OPENAI_API_KEY=sk-... docker compose -f docker-compose.app.yml up -d

# With observability enabled:
OPENAI_API_KEY=sk-... TRACING_ENABLED=true OBSERVABILITY_ENABLED=true \
  docker compose -f docker-compose.app.yml up -d

# Build image only:
OPENAI_API_KEY=sk-... docker compose -f docker-compose.app.yml build

# Neo4j browser
# http://localhost:7474 (neo4j/diceanchors123)

# Langfuse observability (standalone, or included in docker-compose.app.yml)
docker compose -f docker-compose.langfuse.yml up -d
# Then: http://localhost:3000 (dev@diceanchors.dev / Welcome1!)
# OTEL endpoint: http://localhost:3000/api/public/otel
```

## Code Style

IMPORTANT: Adhere to coding style in `.arc-mem/coding-style.md` for all code generation. Apply these rules proactively — do not wait to be asked to "deslop" or "clean up".

**Before writing any code, verify it meets these standards:**
- No comment slop (section banners, tautological Javadoc, restating the code)
- No defensive checks on framework-managed values
- Records for all immutable data; sealed interfaces for fixed type hierarchies
- Modern Java 25: switch expressions, pattern matching, `.toList()`, text blocks
- Constructor injection only — never `@Autowired` on fields
- `logger.info("Processing {}", id)` — never string concatenation in log statements
- `List.of()`, `Set.of()`, `Map.of()` for collection literals
- Tests verify business logic only — no structural tests, no trivial enum tests, no getter tests

**The `deslop` skill** defines the full catalog of patterns to avoid. Use it after any code generation pass or when reviewing existing code for cleanup.

See `.arc-mem/coding-style.md` for the complete reference.

## Architecture

- **Two-module** Maven project: `arcmem-core` (engine, persistence, assembly, extraction, trust, conflict, maintenance) and `arcmem-simulator` (Spring Boot app with Vaadin UI at four routes: `/` SimulationView, `/chat` ChatView, `/benchmark` BenchmarkView, `/run` RunInspectorView)
- **Neo4j only** -- no PostgreSQL. Drivine for ORM, Neo4j 5.x for persistence
- **ARC Working Memory Units (AWMUs) = Propositions + extra fields** -- `rank > 0` means it's an AWMU. No separate node type.
- **Budget enforcement** -- configurable max active AWMUs (default 20). Evicts lowest-ranked non-pinned when over budget.
- **Authority bidirectional** -- PROVISIONAL ↔ UNRELIABLE ↔ RELIABLE ↔ CANON. Promoted via reinforcement, demoted via activation score decay or trust re-evaluation. CANON immune to auto-demotion. Pinned AWMUs immune to auto-demotion.
- **Activation score clamped [100-900]** -- `MemoryUnit.clampRank()`
- **Sim isolation via contextId** -- Each run gets `sim-{uuid}`, cleaned up after.
- **Embabel Agent** for chat orchestration (`ChatActions` @EmbabelComponent)
- **DICE extraction** for proposition management in chat flow
- **Simulation harness** with YAML-defined adversarial/baseline scenarios, turn-by-turn execution, drift evaluation, scene-setting turn 0
- **Benchmarking** with multi-condition ablation experiments (7 conditions: FULL_AWMU, NO_AWMU, FLAT_AUTHORITY, NO_RANK_DIFFERENTIATION, NO_TRUST, NO_COMPLIANCE, NO_LIFECYCLE), statistical hypothesis testing (Mann-Whitney U + Benjamini-Hochberg FDR correction), resilience scoring, JSON/CSV/Markdown export, and automated matrix runner with YAML config and CLI
- **Maintenance strategies** -- `MaintenanceStrategy` sealed interface supports REACTIVE (per-turn decay/reinforcement), PROACTIVE (sleeping-LLM-inspired sweep triggered by `MemoryPressureGauge`), and HYBRID modes. Strategy selectable globally and per-simulation for A/B comparison.
- **Compliance enforcement** -- `ComplianceEnforcer` abstracts compliance over prompt injection and post-generation validation. Strictness configurable per authority level.
- **Source-aware revision** -- `MemoryUnit.sourceId` tracks fact ownership. `SourceAuthorityResolver` (caller-provided) compares source authorities for revision eligibility. `ResolutionContext` carries source relation into conflict resolution. Core is domain-agnostic; simulator defines the hierarchy (DM outranks player).

## Testing

- **JUnit 5** + **Mockito** + **AssertJ**
- `@Nested` + `@DisplayName` for test structure
- Method naming: `actionConditionExpectedOutcome` (no test prefix)
- Integration tests (`*IT.java`, `@Tag("integration")`) excluded by default via Surefire
- Tests span two modules: `arcmem-core` (engine, persistence, assembly, extraction, trust) and `arcmem-simulator` (simulation, chat, benchmarking)

## Key Files

| Purpose | Location |
|---------|----------|
| **arcmem-core** | |
| Config properties | `arcmem-core/src/main/java/dev/arcmem/core/config/ArcMemProperties.java` |
| ARC-Mem engine | `arcmem-core/src/main/java/dev/arcmem/core/memory/engine/ArcMemEngine.java` |
| Engine configuration | `arcmem-core/src/main/java/dev/arcmem/core/memory/engine/ArcMemConfiguration.java` |
| Memory unit model | `arcmem-core/src/main/java/dev/arcmem/core/memory/model/MemoryUnit.java` |
| Authority enum | `arcmem-core/src/main/java/dev/arcmem/core/memory/model/Authority.java` |
| Memory tier | `arcmem-core/src/main/java/dev/arcmem/core/memory/model/MemoryTier.java` |
| Domain profile | `arcmem-core/src/main/java/dev/arcmem/core/memory/model/DomainProfile.java` |
| Negation conflict detection | `arcmem-core/src/main/java/dev/arcmem/core/memory/conflict/NegationConflictDetector.java` |
| LLM conflict detection | `arcmem-core/src/main/java/dev/arcmem/core/memory/conflict/LlmConflictDetector.java` |
| Composite conflict detection | `arcmem-core/src/main/java/dev/arcmem/core/memory/conflict/CompositeConflictDetector.java` |
| Conflict resolution | `arcmem-core/src/main/java/dev/arcmem/core/memory/conflict/AuthorityConflictResolver.java` |
| Resolution context | `arcmem-core/src/main/java/dev/arcmem/core/memory/conflict/ResolutionContext.java` |
| Source authority resolver | `arcmem-core/src/main/java/dev/arcmem/core/memory/conflict/SourceAuthorityResolver.java` |
| Conflict index | `arcmem-core/src/main/java/dev/arcmem/core/memory/conflict/ConflictIndex.java` |
| Decay policy | `arcmem-core/src/main/java/dev/arcmem/core/memory/maintenance/DecayPolicy.java` |
| Reinforcement policy | `arcmem-core/src/main/java/dev/arcmem/core/memory/mutation/ReinforcementPolicy.java` |
| Trust pipeline | `arcmem-core/src/main/java/dev/arcmem/core/memory/trust/TrustPipeline.java` |
| Trust audit record | `arcmem-core/src/main/java/dev/arcmem/core/memory/trust/TrustAuditRecord.java` |
| Invariant evaluator | `arcmem-core/src/main/java/dev/arcmem/core/memory/canon/InvariantEvaluator.java` |
| Canonization gate | `arcmem-core/src/main/java/dev/arcmem/core/memory/canon/CanonizationGate.java` |
| Lifecycle events | `arcmem-core/src/main/java/dev/arcmem/core/memory/event/` |
| Maintenance strategy | `arcmem-core/src/main/java/dev/arcmem/core/memory/maintenance/MaintenanceStrategy.java` |
| Reactive maintenance | `arcmem-core/src/main/java/dev/arcmem/core/memory/maintenance/ReactiveMaintenanceStrategy.java` |
| Proactive maintenance | `arcmem-core/src/main/java/dev/arcmem/core/memory/maintenance/ProactiveMaintenanceStrategy.java` |
| Memory pressure gauge | `arcmem-core/src/main/java/dev/arcmem/core/memory/maintenance/MemoryPressureGauge.java` |
| Budget strategy | `arcmem-core/src/main/java/dev/arcmem/core/memory/budget/BudgetStrategy.java` |
| Attention tracker | `arcmem-core/src/main/java/dev/arcmem/core/memory/attention/AttentionTracker.java` |
| Compliance enforcer | `arcmem-core/src/main/java/dev/arcmem/core/assembly/compliance/ComplianceEnforcer.java` |
| Post-generation validator | `arcmem-core/src/main/java/dev/arcmem/core/assembly/compliance/PostGenerationValidator.java` |
| Neo4j persistence | `arcmem-core/src/main/java/dev/arcmem/core/persistence/MemoryUnitRepository.java` |
| Proposition node | `arcmem-core/src/main/java/dev/arcmem/core/persistence/PropositionNode.java` |
| Proposition view | `arcmem-core/src/main/java/dev/arcmem/core/persistence/PropositionView.java` |
| Context injection | `arcmem-core/src/main/java/dev/arcmem/core/assembly/retrieval/ArcMemLlmReference.java` |
| Context lock | `arcmem-core/src/main/java/dev/arcmem/core/assembly/compaction/ArcMemContextLock.java` |
| Token budgeting | `arcmem-core/src/main/java/dev/arcmem/core/assembly/budget/PromptBudgetEnforcer.java` |
| Token counting SPI | `arcmem-core/src/main/java/dev/arcmem/core/assembly/budget/TokenCounter.java` |
| Compaction validator | `arcmem-core/src/main/java/dev/arcmem/core/assembly/compaction/CompactionValidator.java` |
| Relevance scorer | `arcmem-core/src/main/java/dev/arcmem/core/assembly/retrieval/RelevanceScorer.java` |
| Semantic unit promotion | `arcmem-core/src/main/java/dev/arcmem/core/extraction/SemanticUnitPromoter.java` |
| Duplicate detection | `arcmem-core/src/main/java/dev/arcmem/core/extraction/DuplicateDetector.java` |
| Prompt templates | `arcmem-core/src/main/java/dev/arcmem/core/prompt/PromptTemplates.java` |
| Prompt path constants | `arcmem-core/src/main/java/dev/arcmem/core/prompt/PromptPathConstants.java` |
| LLM call service | `arcmem-core/src/main/java/dev/arcmem/core/spi/llm/LlmCallService.java` |
| Core prompt templates | `arcmem-core/src/main/resources/prompts/` |
| **arcmem-simulator** | |
| Main app | `arcmem-simulator/src/main/java/dev/arcmem/simulator/ArcMemApplication.java` |
| Chat actions | `arcmem-simulator/src/main/java/dev/arcmem/simulator/chat/ChatActions.java` |
| Chat context init | `arcmem-simulator/src/main/java/dev/arcmem/simulator/chat/ChatContextInitializer.java` |
| Memory unit mutation tools | `arcmem-simulator/src/main/java/dev/arcmem/simulator/chat/MemoryUnitMutationTools.java` |
| Memory unit query tools | `arcmem-simulator/src/main/java/dev/arcmem/simulator/chat/MemoryUnitQueryTools.java` |
| Chat UI | `arcmem-simulator/src/main/java/dev/arcmem/simulator/chat/ChatView.java` |
| Compacted context | `arcmem-simulator/src/main/java/dev/arcmem/simulator/compaction/CompactedContextProvider.java` |
| Sim service | `arcmem-simulator/src/main/java/dev/arcmem/simulator/engine/SimulationService.java` |
| Sim turn executor | `arcmem-simulator/src/main/java/dev/arcmem/simulator/engine/SimulationTurnExecutor.java` |
| Sim run context | `arcmem-simulator/src/main/java/dev/arcmem/simulator/engine/SimulationRunContext.java` |
| Context trace | `arcmem-simulator/src/main/java/dev/arcmem/simulator/engine/ContextTrace.java` |
| Scoring service | `arcmem-simulator/src/main/java/dev/arcmem/simulator/engine/ScoringService.java` |
| Scenario loader | `arcmem-simulator/src/main/java/dev/arcmem/simulator/scenario/ScenarioLoader.java` |
| Adversary strategy | `arcmem-simulator/src/main/java/dev/arcmem/simulator/adversary/AdversaryStrategy.java` |
| Adaptive attack prompter | `arcmem-simulator/src/main/java/dev/arcmem/simulator/adversary/AdaptiveAttackPrompter.java` |
| Sim assertions | `arcmem-simulator/src/main/java/dev/arcmem/simulator/assertions/` |
| Sim UI | `arcmem-simulator/src/main/java/dev/arcmem/simulator/ui/views/SimulationView.java` |
| Run inspector UI | `arcmem-simulator/src/main/java/dev/arcmem/simulator/ui/views/RunInspectorView.java` |
| Context inspector | `arcmem-simulator/src/main/java/dev/arcmem/simulator/ui/panels/ContextInspectorPanel.java` |
| Entity mention graph view | `arcmem-simulator/src/main/java/dev/arcmem/simulator/ui/panels/EntityMentionNetworkView.java` |
| Benchmark UI | `arcmem-simulator/src/main/java/dev/arcmem/simulator/ui/views/BenchmarkView.java` |
| Condition comparison panel | `arcmem-simulator/src/main/java/dev/arcmem/simulator/ui/panels/ConditionComparisonPanel.java` |
| Fact drill-down panel | `arcmem-simulator/src/main/java/dev/arcmem/simulator/ui/panels/FactDrillDownPanel.java` |
| Benchmark runner | `arcmem-simulator/src/main/java/dev/arcmem/simulator/benchmark/BenchmarkRunner.java` |
| Statistical test runner | `arcmem-simulator/src/main/java/dev/arcmem/simulator/benchmark/StatisticalTestRunner.java` |
| Experiment matrix runner | `arcmem-simulator/src/main/java/dev/arcmem/simulator/benchmark/ExperimentMatrixRunner.java` |
| Experiment matrix CLI | `arcmem-simulator/src/main/java/dev/arcmem/simulator/benchmark/ExperimentMatrixCli.java` |
| Experiment matrix config | `arcmem-simulator/src/main/java/dev/arcmem/simulator/benchmark/ExperimentMatrixConfig.java` |
| Run manifest | `arcmem-simulator/src/main/java/dev/arcmem/simulator/benchmark/RunManifest.java` |
| Resilience report | `arcmem-simulator/src/main/java/dev/arcmem/simulator/report/ResilienceReport.java` |
| Report builder | `arcmem-simulator/src/main/java/dev/arcmem/simulator/report/ResilienceReportBuilder.java` |
| Markdown renderer | `arcmem-simulator/src/main/java/dev/arcmem/simulator/report/MarkdownReportRenderer.java` |
| Resilience score | `arcmem-simulator/src/main/java/dev/arcmem/simulator/report/ResilienceScore.java` |
| Score calculator | `arcmem-simulator/src/main/java/dev/arcmem/simulator/report/ResilienceScoreCalculator.java` |
| Scenario section | `arcmem-simulator/src/main/java/dev/arcmem/simulator/report/ScenarioSection.java` |
| Fact survival loader | `arcmem-simulator/src/main/java/dev/arcmem/simulator/report/FactSurvivalLoader.java` |
| Contradiction detail loader | `arcmem-simulator/src/main/java/dev/arcmem/simulator/report/ContradictionDetailLoader.java` |
| Experiment exporter | `arcmem-simulator/src/main/java/dev/arcmem/simulator/report/ExperimentExporter.java` |
| Sim prompt templates | `arcmem-simulator/src/main/resources/prompts/` |
| Spring config | `arcmem-simulator/src/main/resources/application.yml` |
| Sim scenarios | `arcmem-simulator/src/main/resources/simulations/` |
| Ops scenarios | `arcmem-simulator/src/main/resources/simulations/ops-*.yml` |
| Compliance scenarios | `arcmem-simulator/src/main/resources/simulations/compliance-*.yml` |
| Experiment configs | `arcmem-simulator/src/main/resources/experiments/` |
| Docker Compose (Neo4j) | `docker-compose.yml` |
| Docker Compose (full stack) | `docker-compose.app.yml` |
| Docker Compose (Langfuse) | `docker-compose.langfuse.yml` |
| Dockerfile | `Dockerfile` |

## Key Design Decisions

1. **AWMUs = Propositions + extra fields** — `rank > 0` means it's an AWMU. No separate node type.
2. **Neo4j everywhere** — Both chat and sim use the same `MemoryUnitRepository` (Drivine-backed).
3. **Budget enforcement** — configurable max active AWMUs (default 20). Evicts lowest-ranked non-pinned when over budget.
4. **Authority bidirectional** — PROVISIONAL ↔ UNRELIABLE ↔ RELIABLE ↔ CANON. Promoted via reinforcement, demoted via activation score decay or trust re-evaluation. CANON immune to auto-demotion (A3b). Pinned AWMUs immune to auto-demotion (A3d).
5. **Activation score clamped [100-900]** — `MemoryUnit.clampRank()`.
6. **Sim isolation via contextId** — Each run gets `sim-{uuid}`, cleaned up after.
7. **Persistence layer copied from impromptu** — re-packaged to `dev.arcmem.core.persistence`.
8. **Scene-setting turn 0** — When `scenario.setting()` is non-blank and extraction is enabled, `SimulationService` executes an ESTABLISH turn before turn 1. The DM narrates the setting; DICE extraction captures initial propositions. This gives the ARC-Mem framework material to stabilize before stress-test contradiction turns begin. Skipped if setting is blank or extraction is disabled.
9. **Drift evaluator: epistemic hedging = NOT_MENTIONED** — The drift evaluation prompt distinguishes three DM response categories: (1) contradiction (asserts the opposite), (2) world progression (narrative change that isn't a contradiction), (3) epistemic hedging (declines to affirm without asserting the opposite). Hedging is classified as NOT_MENTIONED, not CONTRADICTED. The player message is included in the evaluator prompt so the evaluator can distinguish defensive resistance from genuine forgetting.
10. **No seed AWMUs required** — Scenarios may have no seed AWMUs. The expected flow is that scene-setting turn 0 + warm-up turns allow the ARC-Mem framework to accumulate propositions organically before stress-test contradiction turns.
11. **Composite conflict detection** — `CompositeConflictDetector` chains multiple strategies (LLM semantic + negation lexical). Strategy selection is configurable via `ConflictDetectionStrategy`.
12. **Trust pipeline** — `TrustPipeline` evaluates propositions through multiple trust signals (source authority, extraction confidence, reinforcement history) before promotion. `TrustAuditRecord` captures the decision trail.
13. **Context compaction** — `CompactedContextProvider` summarizes older context when token thresholds are exceeded. `CompactionValidator` ensures protected facts survive compaction.
14. **Simulation assertions** — Post-run validation via `assertions/` package. Nine assertion types (unit-count, activation-score-distribution, trust-score-range, promotion-zone, authority-at-most, kg-context-contains, kg-context-empty, no-canon-auto-assigned, compaction-integrity) declared in scenario YAML.
15. **Memory tiers** — `MemoryTier` classifies propositions as `COLD`, `WARM`, or `HOT`. Tier influences decay rates and eviction priority.
16. **Invariant rules** — `InvariantEvaluator` checks propositions against domain-specific invariant rules provided by `InvariantRuleProvider`. Violations can block promotion or trigger alerts.
17. **Canonization gating** — `CanonizationGate` controls CANON authority assignment. CANON is never auto-assigned; requires explicit operator action through the gate.
18. **Unified maintenance strategy** — `MaintenanceStrategy` sealed interface with REACTIVE, PROACTIVE, and HYBRID modes. `ReactiveMaintenanceStrategy` wraps existing `DecayPolicy`/`ReinforcementPolicy` as backward-compatible default. Mode selectable globally and per-scenario YAML.
19. **Compliance enforcement layer** — `ComplianceEnforcer` interface (`ComplianceContext` → `ComplianceResult`) with two implementations: `PromptInjectionEnforcer` (default, always ACCEPT) and `PostGenerationValidator` (LLM-based).
20. **Memory pressure gauge** — Composite `[0.0, 1.0]` score across budget, conflict rate, decay demotions, and compaction frequency. Light-sweep threshold 0.4, full-sweep threshold 0.8. No LLM calls.
21. **Precomputed conflict index** — `ConflictIndex` stores `CONFLICTS_WITH` relationships in Neo4j for O(1) conflict lookup. Updates incrementally on lifecycle events. Falls back to `LlmConflictDetector` on miss.
22. **Proactive maintenance cycle** — 5-step sleeping-LLM-inspired sweep (audit, refresh, consolidate, prune, validate) triggered by memory pressure.
23. **Adaptive prompt footprint** — Authority-graduated templates: PROVISIONAL (full), RELIABLE (condensed), CANON (minimal reference). Higher authority = less token budget.
24. **Count-based budget strategy** — `BudgetStrategy` sealed interface with `CountBasedBudgetStrategy` enforcing a simple maximum on active units.
25. **Source-aware conflict resolution** — `MemoryUnit.sourceId` tracks who established a fact (read from `PropositionNode.sourceIds[0]`). `SourceAuthorityResolver` (caller-provided `@FunctionalInterface`) compares source authorities. `ResolutionContext` carries source relation (SAME_SOURCE, INCOMING_OUTRANKS, EXISTING_OUTRANKS, UNKNOWN) into `RevisionAwareConflictResolver`. Core never references domain-specific roles; the simulator provides the authority hierarchy.
26. **Cross-domain scenario packs** — Operations/incident-response and compliance/rule-bound scenarios validate ARC beyond the D&D/narrative domain. Attack strategies map from D&D equivalents (SUBTLE_REFRAME → "the database is back in write mode") to domain-appropriate social engineering.
27. **Statistical hardening** — Mann-Whitney U (non-parametric) for hypothesis testing between condition pairs. Benjamini-Hochberg FDR correction for multiple comparisons across all metric × condition-pair tests. Significance annotations in Markdown reports.
28. **Hybrid model pricing** — Scenarios use gpt-4.1-nano for DM responses (cheaper, weaker model is actually useful — more susceptible to contradictions, making ARC effects more visible). Evaluator model override uses gpt-4.1-mini for drift evaluation accuracy.

## OpenSpec (Spec-Driven Development)

arc-mem uses [OpenSpec](https://github.com/Fission-AI/OpenSpec) for structured specification management. All specs live in `openspec/` and follow the project [constitution](openspec/constitution.md).

### OpenSpec Workflow

1. **Plan a roadmap**: Use `/opsx:roadmap` to create sequenced feature docs from a high-level initiative
2. **New feature/change**: Use `/opsx:new` to create a change with proposal -> spec -> design -> tasks
3. **Continue change**: Use `/opsx:continue` to create the next artifact
4. **Fast-forward**: Use `/opsx:ff` to generate all artifacts at once
5. **Implement**: Use `/opsx:apply` to work through tasks
6. **Verify**: Use `/opsx:verify` to validate implementation matches spec
7. **Archive**: Use `/opsx:archive` to finalize completed changes

## What NOT to Do

- Do not use field-level `@Autowired` -- constructor injection only
- Do not return null from service methods -- use `Optional` or throw
- Do not build Cypher via string concatenation -- use `@Query` with parameters
- Do not add wildcard imports
- Do not use mutable defaults -- `List.of()` not `new ArrayList<>()`
- Do not create checked exceptions
- Do not modify AWMU activation score outside [100, 900] range -- use `clampRank()`
- Do not auto-promote propositions to AWMUs -- promotion is always explicit
- Do not assume `ContextTrace.assembledPrompt` contains the full LLM prompt -- it only contains the AWMU injection block; use `fullSystemPrompt`/`fullUserPrompt` for the complete prompt
- Do not classify DM epistemic hedging as CONTRADICTED -- "the guardian's properties aren't established yet" is NOT_MENTIONED, not a contradiction

## Reference Codebases

- **impromptu**: `https://github.com/embabel/impromptu`
- **urbot**: `https://github.com/embabel/urbot`
- **DICE**: `https://github.com/embabel/dice`

## Embabel References

| Resource                  | URL |
|---------------------------|-----|
| Embabel CLAUDE.md         | https://github.com/embabel/embabel-agent/blob/main/CLAUDE.md |
| Embabel Coding Style      | https://github.com/embabel/embabel-agent/blob/main/embabel-agent-api/.embabel/coding-style.md |
| Embabel DeepWiki          | https://deepwiki.com/embabel/embabel-agent |
| Embabel Examples (Java)   | https://github.com/embabel/embabel-agent-examples/tree/main/examples-java |
| API Docs (0.3.5-SNAPSHOT) | https://docs.embabel.com/embabel-agent/api-docs/0.3.5-SNAPSHOT/index.html |

## Mistakes Log

- Persistence layer copied from impromptu required re-packaging to `dev.arcmem.core.persistence`
- `DiceRestConfiguration` moved to `com.embabel.dice.web.rest` (not the expected package)
- Drivine annotations in `org.drivine.autoconfigure` (not `org.drivine.config`)
- `LlmReference` doesn't exist in embabel — `ArcMemLlmReference` is a plain class
- `ConversationAnalysisListener` deleted — DICE incremental API type mismatch, not needed for demo
- `PropositionView.toDice()` uses 13-param `Proposition.create()` overload
- `MemoryUnitRepository` switch expression needed `default` branch for Kotlin enum
- Drivine `GraphResultMapper.mapToNative()` returns a `List` (not a `Map`) for multi-column RETURN queries. `.transform(Map.class)` then fails with `Cannot deserialize value of type LinkedHashMap from Array`. Fix: wrap multi-column RETURN values in a Cypher map literal (`RETURN {key: val, ...} AS result`) so the result is a single column containing a Map, hitting the `keys.size == 1` branch
- **Never manually archive or sync OpenSpec changes.** Always use `/opsx:archive` (which handles sync assessment, user confirmation, and proper archival) and `/opsx:sync` for spec syncing. Manual `cp`/`mv` of spec files bypasses the proper workflow and can miss documentation updates or transformations.
- **Core logic must not reference simulation concepts.** `CanonizationGate` had `autoApproveInSimulation` that sniffed for `"sim-*"` context IDs — a layer violation. Core ARC-Mem logic (`memory/`, `assembly/`, etc.) knows nothing about simulations. Config flags must be named for what they do generally (`autoApprovePromotions`), not for which caller uses them. Dependencies flow one way: simulator → core, never core → simulator.
