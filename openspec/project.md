# dice-anchors Project Overview

## Purpose
dice-anchors is a standalone application and test bed for **Anchors** — enriched DICE Propositions with rank, authority, and budget management — that implements a working memory model resistant to adversarial prompt drift. Working reference for DICE <-> Anchor integration.

## Tech Stack
- Java 25 (preview) / Spring Boot 3.5.10 / Embabel Agent 0.3.5-SNAPSHOT / DICE 0.1.0-SNAPSHOT
- Drivine ORM / Neo4j 5.x / Vaadin 24.6.4 / Jakarta Bean Validation
- Single-module Maven project

## Architecture
- Four Vaadin routes: `/` (SimulationView), `/chat` (ChatView), `/benchmark` (BenchmarkView), `/run` (RunInspectorView)
- Neo4j is the sole persistence store (no PostgreSQL)
- Anchors are propositions with rank > 0 (no separate node type)
- Configuration validation via Jakarta Bean Validation annotations (fail-fast at startup)
- Simulation harness runs YAML-defined adversarial/baseline scenarios with turn-by-turn execution
- Chat interface uses Embabel Agent for LLM orchestration with anchor context injection
- Budget enforcement: configurable max active anchors (default 20) with eviction of lowest-ranked non-pinned anchors

## Key Subsystems
- **anchor/** — Engine, lifecycle, policies (decay, reinforcement), conflict detection/resolution, trust pipeline, invariant evaluation, canonization gating
- **persistence/** — Neo4j persistence via Drivine (AnchorRepository, PropositionNode, PropositionView)
- **assembly/** — Context injection (AnchorsLlmReference, AnchorContextLock, PromptBudgetEnforcer, CompactedContextProvider)
- **extract/** — DICE → Anchor promotion (AnchorPromoter, DuplicateDetector interface with fast/LLM/composite implementations, pipeline: confidence → dedup → conflict → trust → promote)
- **chat/** — Embabel chat integration (ChatActions, ChatView, AnchorTools for manual anchor management)
- **domain/** — D&D entity models (Character, Creature, DndItem, DndLocation, Faction, StoryEvent)
- **prompt/** — Prompt template management (PromptTemplates, PromptPathConstants)
- **sim/engine/** — Simulation harness (SimulationService, SimulationTurnExecutor, SimulationExtractionService, SimulationRuntimeConfig, LlmCallService, ScoringService, ContextTrace, ScenarioLoader)
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

## Key Design Principles
- **Authority upgrade-only**: PROVISIONAL → UNRELIABLE → RELIABLE → CANON (never downgrade, CANON never auto-assigned)
- **Rank bounds**: All ranks clamped to [100, 900] via `Anchor.clampRank()`
- **Sim isolation**: Each run gets unique `sim-{uuid}` contextId, cleaned up after completion
- **Fail-open dedup**: LLM dedup errors assume unique (don't starve anchor pool)
- **Multi-gate promotion**: Confidence → Dedup → Conflict → Trust → Promote (gates short-circuit early)
- **Memory tiers**: COLD, WARM, HOT influence decay and eviction priority
