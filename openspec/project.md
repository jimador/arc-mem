# dice-anchors Project Overview

## Purpose
dice-anchors is a standalone application and test bed for **Anchors** — enriched DICE Propositions with rank, authority, and budget management — that implements a working memory model for long-horizon attention stability and hallucination/contradiction control. Adversarial prompts are used as stress tests, not as the product objective. Working reference for DICE <-> Anchor integration.

## Tech Stack
- Java 25 (preview) / Spring Boot 3.5.10 / Embabel Agent 0.3.5-SNAPSHOT / DICE 0.1.0-SNAPSHOT
- Drivine ORM / Neo4j 5.x / Vaadin 24.6.4 / Jakarta Bean Validation
- Single-module Maven project

## Architecture
- Four Vaadin routes: `/` (SimulationView), `/chat` (ChatView), `/benchmark` (BenchmarkView), `/run` (RunInspectorView)
- Neo4j is the sole persistence store (no PostgreSQL)
- Anchors are propositions with rank > 0 (no separate node type)
- Configuration validation via Jakarta Bean Validation annotations (fail-fast at startup)
- Simulation harness runs YAML-defined adversarial/baseline scenarios with turn-by-turn execution (adversarial scenarios are stress tests for hallucination/contradiction control)
- Chat interface uses Embabel Agent for LLM orchestration with anchor context injection
- Budget enforcement: configurable max active anchors (default 20) with eviction of lowest-ranked non-pinned anchors

## Key Subsystems
- **anchor/** — Engine, lifecycle, policies (decay, reinforcement), conflict detection/resolution, trust pipeline, invariant evaluation, canonization gating, unified maintenance strategies (reactive/proactive/hybrid), memory pressure gauge, precomputed conflict index, budget strategies, Prolog integration
- **persistence/** — Neo4j persistence via Drivine (AnchorRepository, PropositionNode, PropositionView), tiered anchor storage (HOT/WARM/COLD)
- **assembly/** — Context injection (AnchorsLlmReference, AnchorContextLock, PromptBudgetEnforcer, CompactedContextProvider), compliance enforcement (ComplianceEnforcer)
- **extract/** — DICE → Anchor promotion (AnchorPromoter, DuplicateDetector interface with fast/LLM/composite implementations, pipeline: confidence → dedup → conflict → trust → promote)
- **chat/** — Embabel chat integration (ChatActions, ChatView, AnchorTools for manual anchor management)
- **domain/** — D&D entity models (Character, Creature, DndItem, DndLocation, Faction, StoryEvent)
- **prompt/** — Prompt template management (PromptTemplates, PromptPathConstants)
- **sim/engine/** — Simulation harness (SimulationService, SimulationTurnExecutor, SimulationExtractionService, SimulationRuntimeConfig, LlmCallService, ScoringService, ContextTrace, ScenarioLoader, SimulationTurnServices)
- **sim/engine/adversary/** — Adversary strategies (AdversaryStrategy, AdaptiveAttackPrompter, StrategyCatalog)
- **sim/benchmark/** — Multi-condition ablation experiments (BenchmarkRunner, ExperimentRunner, BenchmarkAggregator, BenchmarkStatistics, EffectSizeCalculator)
- **sim/report/** — Resilience reporting (ResilienceReport, ResilienceReportBuilder, ResilienceScoreCalculator, MarkdownReportRenderer, FactSurvivalLoader, ContradictionDetailLoader)
- **sim/assertions/** — Post-run validation (assertion types declared in scenario YAML)
- **sim/views/** — Vaadin UI panels (SimulationView, RunInspectorView, BenchmarkView, ContextInspectorPanel, EntityMentionNetworkView, FactDrillDownPanel, and ~17 supporting panels)

## Testing
- JUnit 5 + Mockito + AssertJ across 689 tests
- Test structure: `@Nested` classes with `@DisplayName` for clarity
- Naming: `actionConditionExpectedOutcome` (no "test" prefix)
- Integration tests (`*IT.java`, `@Tag("integration")`) excluded by default via Surefire config
- Coverage areas: anchor lifecycle, conflict detection, trust pipeline, promotion gates, dedup logic, simulation execution

## Development Workflow
- **OpenSpec** for spec-driven development: proposals → design → specs → tasks
- **Spec-first approach**: Changes documented in proposal, design decisions recorded, specs define requirements, tasks track implementation
- **Key archival**: Completed changes moved to `openspec/changes/archive/YYYY-MM-DD-*`
- Main specs live in `openspec/specs/`; delta specs in change directories, synced to main via `/opsx:sync` or `/opsx:archive`

## Completed Initiatives

### anchor-memory-optimization (2026-03-03)
- **Features**: 12 implemented (F02–F13), F01 deferred
- **Research basis**: Sleeping LLM (wake/sleep consolidation) + Google AI STATIC (constrained decoding)
- **Summary**: Unified maintenance strategy (reactive/proactive/hybrid), compliance enforcement layer, memory pressure gauge, precomputed conflict index, promotion pipeline optimization, proactive maintenance cycle, quality scoring, adaptive prompt footprint, interference-density budget, tiered storage, constraint-aware decoding interface, Prolog integration layer

### anchors-working-memory-evolution (2026-03-03)
- **Features**: 7 implemented (F01–F07), F08 (dice-framework-fit-upstream-proposal) deferred
- **Summary**: Working memory tiering, conflict detection calibration, retrieval quality gate, bi-temporal validity and supersession, compaction recovery guardrails, benchmarking and statistical rigor, operator invariants API and governance

### anchor-working-memory-research (2026-03-03)
- **Features**: 3 implemented (F01–F03), F04–F05 (serendipitous retrieval, multi-agent governance) deferred as future work
- **Summary**: Experiment framework, first-class benchmarking UI, resilience evaluation report

### collaborative-anchor-mutation (2026-03-03)
- **Features**: 2 implemented (F01–F02), F03–F05 (cascade, provenance, UI mutation) deferred
- **Summary**: Revision intent classification, prompt compliance revision carveout

## Key Design Principles
- **Authority upgrade-only**: PROVISIONAL → UNRELIABLE → RELIABLE → CANON (never downgrade, CANON never auto-assigned)
- **Rank bounds**: All ranks clamped to [100, 900] via `Anchor.clampRank()`
- **Sim isolation**: Each run gets unique `sim-{uuid}` contextId, cleaned up after completion
- **Fail-open dedup**: LLM dedup errors assume unique (don't starve anchor pool)
- **Multi-gate promotion**: Confidence → Dedup → Conflict → Trust → Promote (gates short-circuit early)
- **Memory tiers**: COLD, WARM, HOT influence decay and eviction priority
