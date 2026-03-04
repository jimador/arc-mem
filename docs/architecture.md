<!-- sync: openspec/specs/anchor-conflict, openspec/specs/anchor-trust, openspec/specs/anchor-lifecycle, openspec/specs/benchmark-report, openspec/specs/resilience-report, openspec/specs/run-history-persistence, openspec/specs/observability -->
<!-- last-synced: 2026-02-25 -->

# Architecture

This project is a single Spring Boot app (Java 25) + Neo4j 5.x. It is intentionally opinionated and demo-focused.

## System topology

| Route | Purpose |
|---|---|
| `/` | simulation harness |
| `/chat` | interactive anchor-aware DM chat |
| `/benchmark` | condition/scenario batch experiments |
| `/run` | run inspection and cross-run comparison |

Everything shares the same anchor lifecycle engine and persistence layer.

## Runtime execution paths

Simulation path:

`ScenarioLoader -> SimulationService -> SimulationTurnExecutor -> (LLM call + ComplianceEnforcer.enforce()) -> (extraction + ConflictPreCheck + AnchorPromoter) -> (MaintenanceStrategy.onTurnComplete()) -> ScoringService -> RunHistoryStore`

Chat path:

`ChatView -> ChatActions -> prompt assembly -> model response -> async extraction event -> AnchorPromoter`

Benchmark/report path:

`ExperimentRunner -> BenchmarkRunner -> ResilienceReportBuilder -> MarkdownReportRenderer`

```mermaid
flowchart LR
    subgraph Sim["Simulation Path"]
        SL["ScenarioLoader"] --> SS["SimulationService"]
        SS --> STE["SimulationTurnExecutor"]
        STE --> LLM["Model Call"]
        STE --> EXT["Extraction + Promotion"]
        STE --> SC["ScoringService"]
        SC --> RHS["RunHistoryStore"]
    end

    subgraph Chat["Chat Path"]
        CV["ChatView"] --> CA["ChatActions"]
        CA --> PA["Prompt Assembly"]
        PA --> CR["Model Response"]
        CR --> EVT["ConversationAnalysisRequestEvent"]
        EVT --> AP["AnchorPromoter"]
    end

    subgraph Bench["Benchmark Path"]
        ER["ExperimentRunner"] --> BR["BenchmarkRunner"]
        BR --> RRB["ResilienceReportBuilder"]
        RRB --> MRR["MarkdownReportRenderer"]
    end
```

## Package map

- `anchor/`: rank/authority lifecycle, conflict resolution, decay, trust re-eval, maintenance strategies (reactive/proactive/hybrid), memory pressure gauge, conflict index, budget strategies, Prolog integration
- `assembly/`: context assembly, lock, relevance scoring, compaction/token budget, compliance enforcement
- `extract/`: proposition-to-anchor gate pipeline
- `chat/`: Embabel action/tool integration + chat UI
- `persistence/`: Neo4j entities/repository, tiered anchor storage (HOT/WARM/COLD)
- `sim/`: scenario execution, judge scoring, benchmarking, report output, UI panels

## Core data model

### Anchor (active working-memory item)

Anchors are not a separate node type. A proposition with `rank > 0` is active.

```java
record Anchor(
    String id,
    String text,
    int rank,                  // [100, 900]
    Authority authority,       // PROVISIONAL/UNRELIABLE/RELIABLE/CANON
    boolean pinned,
    double confidence,
    int reinforcementCount,
    @Nullable TrustScore trustScore,
    double diceImportance,
    double diceDecay,
    MemoryTier memoryTier      // COLD/WARM/HOT
)
```

### Authority hierarchy

```java
enum Authority {
    PROVISIONAL(0),
    UNRELIABLE(1),
    RELIABLE(2),
    CANON(3)
}
```

```mermaid
stateDiagram-v2
    [*] --> PROVISIONAL
    PROVISIONAL --> UNRELIABLE: reinforcement threshold hit
    UNRELIABLE --> RELIABLE: reinforcement threshold hit
    RELIABLE --> CANON: explicit canonization only

    RELIABLE --> UNRELIABLE: rank/trust demotion
    UNRELIABLE --> PROVISIONAL: rank/trust demotion

    CANON --> CANON: no automatic demotion
```

Strict invariants:
- CANON is never auto-assigned.
- CANON is immune to automatic demotion.
- Pinned anchors are immune to decay and budget eviction.
- Rank is always clamped through `Anchor.clampRank()`.

## Anchor engine responsibilities

`AnchorEngine` is the orchestrator. The behavior that matters most:

- `inject(contextId)`: returns active anchors sorted by rank desc
- `promote(propositionId, initialRank, authorityCeiling?)`: promote then enforce budget
- `reinforce(anchorId)`: increment reinforcement, apply rank bump, maybe authority upgrade
- `detectConflicts(contextId, text)`: delegate to configured detector
- `resolveConflict(conflict)`: delegate to configured resolver
- `supersede(predecessorId, successorId, reason)`: archive + lineage link

### Budget enforcement

Budget is enforced per promotion call, not as a batch post-pass.

```text
promote()
  -> write promoted anchor
  -> evictLowestRanked(contextId, budget)
```

Default budget is `20` active anchors per context.

## Maintenance strategies

`MaintenanceStrategy` sealed interface with three modes:

- `REACTIVE` (default): per-turn `DecayPolicy` + `ReinforcementPolicy` — identical to pre-optimization behavior
- `PROACTIVE`: 5-step sleeping-LLM-inspired sweep (audit → refresh → consolidate → prune → validate) triggered by `MemoryPressureGauge` thresholds
- `HYBRID`: reactive per-turn hooks + proactive sweeps

`MemoryPressureGauge` computes composite `[0.0, 1.0]` pressure from budget usage, conflict rate, decay demotions, and compaction frequency. Light-sweep at 0.4, full-sweep at 0.8.

Strategy is selectable globally via `DiceAnchorsProperties` and per-scenario via YAML for A/B comparison.

## Compliance enforcement

`ComplianceEnforcer` interface: `enforce(ComplianceContext) → ComplianceResult`.

Implementations:
- `PromptInjectionEnforcer` — current behavior, always ACCEPT (default)
- `PostGenerationValidator` — LLM validates response against CANON/RELIABLE anchors after generation
- `PrologInvariantEnforcer` — deterministic rule-based checking via DICE tuProlog

Authority-based strictness: CANON enforced by default; lower authorities configurable.

## Conflict handling

Available detector strategies:
- `llm` (default): semantic contradiction detection
- `lexical`: negation marker + token overlap heuristic
- `composite`: multi-detector chaining
- `indexed`: O(1) lookup via precomputed `ConflictIndex` (Neo4j `CONFLICTS_WITH` relationships)
- `logical`: Prolog backward chaining via DICE tuProlog (deterministic, no LLM calls)

Resolver default (authority-biased):

```text
if existing.authority >= RELIABLE -> KEEP_EXISTING
else if incoming.confidence > 0.8 -> REPLACE
else                              -> COEXIST
```

Conflict outcomes:
- `KEEP_EXISTING`
- `REPLACE`
- `DEMOTE_EXISTING`
- `COEXIST`

## Trust pipeline

Trust gating runs before promotion and routes candidates into zones.

```text
TrustPipeline.evaluate(node, contextId)
  -> collect TrustSignal values
  -> apply DomainProfile weights
  -> compute TrustScore
  -> map to PromotionZone: AUTO_PROMOTE / REVIEW / ARCHIVE
```

Profile thresholds:

| Profile | Auto Promote | Review | Archive |
|---|---:|---:|---:|
| `BALANCED` | `>= 0.70` | `>= 0.40` | `< 0.40` |
| `SECURE` | `>= 0.85` | `>= 0.50` | `< 0.50` |
| `NARRATIVE` | `>= 0.60` | `>= 0.35` | `< 0.35` |

## Prompt assembly and compaction

`AnchorsLlmReference` injects an explicit established-facts block into the system prompt.

Example shape:

```text
=== ESTABLISHED FACTS ===
1. [CANON] The East Gate is breached (rank: 850)
2. [RELIABLE] Baron Krell is a four-armed mutant (rank: 750)
=== END ESTABLISHED FACTS ===
```

Compaction path (`CompactedContextProvider`):
1. collect context messages
2. estimate token pressure
3. summarize with LLM when thresholds are exceeded
4. validate protected content survival (`CompactionValidator`)

Current limitation: validator is detect-only, no automatic retry/recovery.

## Revision and supersession

`RevisionAwareConflictResolver` treats `REVISION` separately from `CONTRADICTION` and `WORLD_PROGRESSION`.

High-level policy:
- `CANON`: immutable
- `RELIABLE`: configurable revisability
- `UNRELIABLE`: revisable above confidence threshold
- `PROVISIONAL`: revisable

`REPLACE` typically leads to:

```text
AnchorEngine.supersede()
  -> archive predecessor
  -> create SUPERSEDES edge in Neo4j
  -> emit lifecycle event + audit metadata
```

## Simulation scoring + history

`ScoringService` computes run metrics including:
- fact survival rate
- contradiction counts (including MAJOR count)
- drift absorption rate
- mean turns to first drift
- strategy-level effectiveness

Run storage is behind `RunHistoryStore`:
- `memory`: in-memory `SimulationRunStore`
- `neo4j`: persistent `Neo4jRunHistoryStore`

## Key config defaults

| Setting | Default |
|---|---|
| `anchor.budget` | `20` |
| `anchor.initial-rank` | `500` |
| `conflict-detection.strategy` | `llm` |
| `chat.chat-llm.model` | `gpt-4.1-mini` |
| `sim.evaluator-model` | `gpt-4.1-mini` |
| `run-history.store` | `memory` |
| `anchor.revision.enabled` | `true` |
| `anchor.revision.reliable-revisable` | `false` |
| `anchor.maintenance.mode` | `REACTIVE` |
| `anchor.pressure.light-sweep-threshold` | `0.4` |
| `anchor.pressure.full-sweep-threshold` | `0.8` |
| `anchor.budget.strategy` | `COUNT` |
| `compliance.enforcement-strategy` | `PROMPT_ONLY` |
| `tiered-storage.enabled` | `false` |
| `quality-scoring.enabled` | `false` |

## External dependencies

- Neo4j 5.x
- OpenAI-compatible model endpoint
- OTEL/Langfuse (optional)

## Architectural boundaries

- Core anchor logic does not reference simulation concepts.
- Conflict/trust/promotion should fail safe, not fail open.
- This demo prefers explicit policies over generic abstractions.
