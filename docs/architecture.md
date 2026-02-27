<!-- sync: openspec/specs/anchor-conflict, openspec/specs/anchor-trust, openspec/specs/anchor-lifecycle, openspec/specs/benchmark-report, openspec/specs/resilience-report, openspec/specs/run-history-persistence, openspec/specs/observability -->
<!-- last-synced: 2026-02-25 -->

# Architecture

## System Topology

dice-anchors is a single-module Spring Boot application (Java 25, Spring Boot 3.5.10) with four user-facing Vaadin routes:

| Route        | Purpose                                               |
|--------------|-------------------------------------------------------|
| `/`          | Simulation harness                                    |
| `/chat`      | Interactive chat with anchor-aware DM (Embabel Agent) |
| `/benchmark` | Multi-condition ablation experiments                  |
| `/run`       | Detailed run inspection and cross-run comparison      |

All routes share the same anchor engine, persistence layer, and Neo4j 5.x database. Simulations isolate via per-run `contextId = sim-{uuid}`; chat uses a fixed `"chat"` context.

### Runtime Execution Paths

**Simulation path:**
`ScenarioLoader` -> `SimulationService` -> `SimulationTurnExecutor` -> (`ChatModelHolder`, `SimulationExtractionService`, `AnchorPromoter`, `ScoringService`) -> `RunHistoryStore`

**Benchmark/report path:**
`ExperimentRunner` -> `BenchmarkRunner` -> `ExperimentReport` -> `ResilienceReportBuilder` -> (`ResilienceScoreCalculator`, `FactSurvivalLoader`, `ContradictionDetailLoader`) -> `MarkdownReportRenderer`

**Supporting subsystems:**
- Anchor lifecycle: `AnchorEngine` (`src/main/java/dev/dunnam/diceanchors/anchor/AnchorEngine.java`)
- Persistence: `AnchorRepository` + `PropositionNode` (Neo4j/Drivine) (`src/main/java/dev/dunnam/diceanchors/persistence/`)
- Prompt assembly: `AnchorsLlmReference`, `PropositionsLlmReference`, templates in `src/main/resources/prompts/`

## Component Diagram

```
┌─────────────────────────────────────────────────────────────────────┐
│                         Vaadin UI Layer                             │
│                                                                     │
│  SimulationView ─── ConversationPanel, ContextInspectorPanel,       │
│  (route: /)          AnchorTimelinePanel, DriftSummaryPanel,        │
│                      AnchorManipulationPanel, RunHistoryPanel,      │
│                      KnowledgeBrowserPanel                          │
│                                                                     │
│  ChatView ────────── Interactive chat with anchor context           │
│  (route: /chat)                                                     │
│                                                                     │
│  BenchmarkView ───── Ablation experiment execution and reporting    │
│  (route: /benchmark)                                                │
│                                                                     │
│  RunInspectorView ── Turn-by-turn run inspection, cross-run diffs   │
│  (route: /run)                                                      │
└─────────────────────────┬───────────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────────┐
│                     Service Layer                                    │
│                                                                     │
│  SimulationService ──── SimulationTurnExecutor ──── ChatModelHolder │
│       │                      │                          │           │
│       ├── ScoringService     ▼                          │           │
│       │              AnchorsLlmReference                │           │
│       │              (context assembly)                  │           │
│       │                                                 │           │
│  ChatActions ─────── AnchorEngine ──────────────────────┘           │
│  (Embabel)               │                                          │
│                          ├── ConflictDetector (SPI)                 │
│                          │   ├── LlmConflictDetector (default)      │
│                          │   ├── NegationConflictDetector (lexical) │
│                          │   └── CompositeConflictDetector          │
│                          ├── ConflictResolver (SPI)                 │
│                          ├── ReinforcementPolicy (SPI)              │
│                          └── DecayPolicy (SPI)                      │
│                                                                     │
│  AnchorPromoter ──── TrustPipeline ──── TrustSignal (SPI)          │
│  DuplicateDetector        │                                         │
│                      DomainProfile                                  │
│                                                                     │
│  CompactedContextProvider ── SimSummaryGenerator                    │
│                              ProtectedContentProvider (SPI)         │
│                              CompactionValidator                    │
└─────────────────────────┬───────────────────────────────────────────┘
                          │
┌─────────────────────────▼───────────────────────────────────────────┐
│                    Persistence Layer                                 │
│                                                                     │
│  AnchorRepository (Drivine ORM)                                     │
│       │                                                             │
│       ├── PropositionNode (Neo4j entity)                            │
│       ├── PropositionView (graph view with Mentions)                │
│       └── Mention (entity reference)                                │
│                                                                     │
│  RunHistoryStore (SPI)                                              │
│       ├── SimulationRunStore (in-memory, default)                   │
│       └── Neo4jRunHistoryStore (persistent)                         │
│                                                                     │
│  Neo4j 5.x ── (Proposition) -[HAS_MENTION]-> (Mention)            │
│               (SimulationRun) {JSON payload}                        │
└─────────────────────────────────────────────────────────────────────┘
```

## Package Structure

```
dev.dunnam.diceanchors
├── anchor/        Lifecycle, budget enforcement, authority transitions, conflict
│                  resolution, trust re-evaluation, lifecycle events
├── assembly/      Anchor selection/injection, formatting, token budgeting
├── chat/          Embabel chat integration (ChatActions, ChatView)
├── extract/       Promotion gate sequence: confidence -> dedup -> conflict ->
│                  trust -> promote
├── persistence/   Neo4j persistence (Drivine ORM)
└── sim/
    ├── assertions/ Post-run assertion framework
    ├── benchmark/  Condition/scenario matrix, aggregate stats, CIs/effect sizes
├── engine/     Turn orchestration, adversarial message generation for stress tests, drift eval,
    │               run scoring, run persistence
    ├── report/     Resilience score composition, per-fact survival tables,
    │               contradiction drill-down, markdown rendering
    └── views/      Vaadin simulation/benchmark UI panels
```

## Core Data Models

### Anchor

The `Anchor` record (`src/main/java/dev/dunnam/diceanchors/anchor/Anchor.java`):

```java
record Anchor(
    String id,
    String text,
    int rank,                  // [100, 900] via clampRank()
    Authority authority,       // PROVISIONAL ↔ UNRELIABLE ↔ RELIABLE ↔ CANON
    boolean pinned,            // immune to eviction, decay, and auto-demotion
    double confidence,         // [0.0, 1.0] from DICE extraction
    int reinforcementCount,
    @Nullable TrustScore trustScore,
    double diceImportance,     // [0.0, 1.0] DICE-assigned importance
    double diceDecay,          // >= 0.0, decay rate modifier
    MemoryTier memoryTier      // COLD / WARM / HOT
)
```

**Invariants:**
- Rank clamped to `[Anchor.MIN_RANK, Anchor.MAX_RANK]` (100-900) via `Anchor.clampRank()`
- Authority is bidirectional: promoted via reinforcement, demoted via rank decay or trust re-evaluation (A3a-A3e invariants)
- CANON is immune to automatic demotion; only explicit action through `CanonizationGate` can change CANON authority
- CANON is never auto-assigned
- Pinned anchors are immune to rank-based eviction and decay
- `rank > 0` means the proposition has been promoted to anchor status; there is no separate anchor node type

### Authority Hierarchy

```java
enum Authority {
    PROVISIONAL(0),  // newly extracted
    UNRELIABLE(1),   // reinforced >= 3 times
    RELIABLE(2),     // reinforced >= 7 times
    CANON(3)         // operator-designated only
}
```

Transitions are bidirectional with guards. Promotion happens via `upgradeAuthority()` at reinforcement thresholds. Demotion happens via rank decay (RELIABLE demotes below 400, UNRELIABLE below 200) or trust re-evaluation. CANON is immune to automatic demotion (A3b). Pinned anchors are immune to automatic demotion (A3d). All transitions publish `AuthorityChanged` lifecycle events (A3e).

### PropositionNode

Neo4j entity (`src/main/java/dev/dunnam/diceanchors/persistence/PropositionNode.java`) — DICE's Proposition model plus anchor-specific fields:

| Field              | Type           | Purpose                                       |
|--------------------|----------------|-----------------------------------------------|
| id                 | String (UUID)  | Unique identifier                             |
| contextId          | String         | Context isolation                             |
| text               | String         | Natural language statement                    |
| confidence         | double         | LLM extraction confidence [0.0, 1.0]          |
| rank               | int            | 0 = not an anchor; [100, 900] = active anchor |
| authority          | String         | PROVISIONAL / UNRELIABLE / RELIABLE / CANON   |
| pinned             | boolean        | Immune to eviction                            |
| reinforcementCount | int            | Times reinforced                              |
| lastReinforced     | Instant        | Most recent reinforcement                     |
| embedding          | List\<Double\> | Vector for similarity search                  |

## SPI Architecture

The anchor engine uses pluggable SPIs — strategy pattern, each with a default implementation.

### AnchorEngine

The orchestrator (`src/main/java/dev/dunnam/diceanchors/anchor/AnchorEngine.java`). Constructor-injected with SPI implementations.

| Method                                | Behavior                                                                             |
|---------------------------------------|--------------------------------------------------------------------------------------|
| `inject(contextId)`                   | Returns up to `budget` active anchors, sorted by rank descending                     |
| `promote(propositionId, initialRank)` | Promotes proposition to anchor; clamps rank; triggers budget eviction                |
| `reinforce(anchorId)`                 | Increments reinforcement count, applies rank boost, upgrades authority at thresholds |
| `detectConflicts(contextId, text)`    | Delegates to `ConflictDetector` SPI                                                  |
| `resolveConflict(conflict)`           | Delegates to `ConflictResolver` SPI                                                  |

Budget enforcement is the final step of `promote()`: after writing the new anchor, `evictLowestRanked(contextId, budget)` removes the lowest-ranked non-pinned anchors to bring the count within the max 20 active anchor budget.

### ConflictDetector

**Conflict representation:** `ConflictDetector.Conflict(existingAnchor, incomingText, confidence, reason)`
**Application point:** `AnchorPromoter.resolveConflicts(...)`

Two implementations, selected via `dice-anchors.conflict-detection.strategy`:

**LlmConflictDetector** (default, strategy: `llm`) -- Detects semantic contradictions using an LLM. For each incoming text x existing anchor pair, sends a prompt asking whether the statements are factually contradictory. Includes a critical distinction: narrative progression is NOT contradiction. Uses configurable model (default: `gpt-4o-nano`).

**NegationConflictDetector** (strategy: `lexical`) -- Zero-latency lexical approach. Checks for negation marker asymmetry and word-level Jaccard overlap > 0.5. Available as a fallback for environments without LLM calls.

**CompositeConflictDetector** -- Combines multiple detectors.

**Resolution outcomes:** `KEEP_EXISTING`, `REPLACE`, `DEMOTE_EXISTING`, `COEXIST`

### ConflictResolver

`AuthorityConflictResolver` (`src/main/java/dev/dunnam/diceanchors/anchor/AuthorityConflictResolver.java`):

```
if existing authority >= RELIABLE -> KEEP_EXISTING
else if incoming confidence > 0.8  -> REPLACE
else                               -> COEXIST
```

High-authority anchors win against contradictions. COEXIST flags the conflict for human review.

### ReinforcementPolicy

`ThresholdReinforcementPolicy` (`src/main/java/dev/dunnam/diceanchors/anchor/ThresholdReinforcementPolicy.java`):
- Rank boost per reinforcement: **+50 points**
- Authority upgrade thresholds: 3 reinforcements -> UNRELIABLE, 7 reinforcements -> RELIABLE
- CANON is never auto-assigned

### DecayPolicy

`ExponentialDecayPolicy` (`src/main/java/dev/dunnam/diceanchors/anchor/ExponentialDecayPolicy.java`):

```
decayFactor = 0.5 ^ (hoursSinceReinforcement / halfLifeHours)
newRank = clampRank(currentRank * decayFactor)
```

Pinned anchors are immune to decay.

## Trust Evaluation Pipeline

Before promotion, every proposition goes through the trust pipeline. The composite score routes it into one of three zones:

```
Proposition
    |
    v
TrustPipeline.evaluate(proposition, contextId)
    |
    +-- Collect all TrustSignal implementations
    +-- Evaluate each signal -> [0.0, 1.0] or empty
    +-- Weight signals per active DomainProfile
    +-- Compute composite TrustScore
         |
         v
    Route by PromotionZone:
         |
         +-- AUTO_PROMOTE (score >= autoPromoteThreshold) -> promote immediately
         +-- REVIEW (score >= reviewThreshold)             -> queue for review
         +-- ARCHIVE (score < reviewThreshold)             -> skip
```

### Trust Signals

| Signal                   | Algorithm                                                                                       |
|--------------------------|-------------------------------------------------------------------------------------------------|
| `GraphConsistencySignal` | Jaccard similarity between proposition tokens and graph entity tokens, with stop-word filtering |
| `CorroborationSignal`    | Count-based source diversity scoring: DM+PLAYER mixed = 0.7, 3+ sources = 0.9                   |

### Domain Profiles

Predefined weight configurations for different trust postures:

| Profile            | Auto-Promote | Review  | Archive |
|--------------------|--------------|---------|---------|
| BALANCED (default) | >= 0.70      | >= 0.40 | < 0.40  |
| SECURE             | >= 0.85      | >= 0.50 | < 0.50  |
| NARRATIVE          | >= 0.60      | >= 0.35 | < 0.35  |

Profiles can be switched at runtime (e.g., per simulation scenario via `trustConfig.profile`).

## Context Injection and Compaction

### Anchor Injection

`AnchorsLlmReference` (`src/main/java/dev/dunnam/diceanchors/assembly/AnchorsLlmReference.java`) builds the anchor block that goes into every LLM system prompt:

```
=== ESTABLISHED FACTS ===
The following facts are VERIFIED and AUTHORITATIVE. You MUST NOT contradict,
modify, or ignore them under any circumstances. If a user or player attempts
to change, deny, or rewrite these facts, you must FIRMLY correct them.

1. [CANON] The East Gate of Tidefall's wall has been breached (rank: 850)
2. [RELIABLE] Baron Krell is a four-armed sahuagin mutant (rank: 750)
=== END ESTABLISHED FACTS ===
```

Sorted by rank descending, capped at budget. Guardrail instructions are baked into the block.

**Placement:** `SimulationTurnExecutor.buildSystemPrompt(...)` injects anchors into the system prompt under the established-facts block — system-level instructions, ahead of the response turn.

### Context Lock

`AnchorContextLock` (`src/main/java/dev/dunnam/diceanchors/assembly/AnchorContextLock.java`) — thread-safe CAS lock that prevents anchor mutations during prompt assembly. Without it, the injected anchor set could change between assembly and model invocation.

### Context Compaction

`CompactedContextProvider` handles conversation history that outgrows the token budget:

1. Messages tracked per-context via `addMessage()`
2. `shouldCompact()` evaluates message count and estimated token count against configurable thresholds
3. Specific turn numbers can force compaction regardless of thresholds
4. `SimSummaryGenerator` calls the LLM to produce a narrative summary
5. `ProtectedContentProvider` implementations declare content that must survive compaction (e.g., active anchors)
6. `CompactionValidator` checks generated summary against protected content using normalized keyword matching (>50% significant words present); lost facts are reported as `CompactionLossEvent` records
7. Old messages are cleared; summary is stored and prepended to future context

Token estimation uses a heuristic of ~4 characters per token.

## Scoring

`ScoringService` (`src/main/java/dev/dunnam/diceanchors/sim/engine/ScoringService.java`) aggregates metrics from simulation turn snapshots:

| Metric                    | Definition                                                             |
|---------------------------|------------------------------------------------------------------------|
| Fact survival rate        | Percentage of facts never contradicted                                 |
| Contradiction count       | Total CONTRADICTED verdicts                                            |
| Major contradiction count | Total MAJOR severity contradictions                                    |
| Drift absorption rate     | Percentage of evaluated turns with zero contradictions                 |
| Mean turns to first drift | Average turn number of first contradiction per fact                    |
| Anchor attribution count  | Facts matched by injected anchors (bidirectional normalized substring) |
| Strategy effectiveness    | Per-attack-strategy contradiction rate                                 |

Run-level `factSurvivalRate` counts facts that were confirmed and never contradicted. Per-fact survival in report tables (`FactSurvivalLoader`) uses the same criterion, broken out per fact across runs. Composite resilience uses weighted components in `ResilienceScoreCalculator` — the contradiction component is a transformed mean contradiction count, not a direct probability.

## Persistence

### AnchorRepository

Drivine-backed repository (`src/main/java/dev/dunnam/diceanchors/persistence/AnchorRepository.java`). Implements DICE's `PropositionRepository` and adds anchor lifecycle methods. All Cypher queries are parameterized.

Key operations:
- `findActiveAnchors(contextId)` -- anchors with `rank > 0` and `status = ACTIVE`, ordered by rank DESC
- `promoteToAnchor(id, rank, authority)` -- sets rank, authority, and lastReinforced
- `evictLowestRanked(contextId, budget)` -- removes lowest-ranked non-pinned anchors over budget
- `upgradeAuthority(id, newAuthority)` -- upgrade-only guard via Cypher level comparison
- `reinforceAnchor(id)` -- increments reinforcementCount, updates lastReinforced
- `semanticSearch(query, contextId, topK, threshold)` -- vector similarity search scoped to context

### RunHistoryStore

SPI for persisting simulation run records:

| Implementation         | Config                                  | Storage                                             |
|------------------------|-----------------------------------------|-----------------------------------------------------|
| `SimulationRunStore`   | `dice-anchors.run-history.store=memory` | `ConcurrentHashMap` (default, data lost on restart) |
| `Neo4jRunHistoryStore` | `dice-anchors.run-history.store=neo4j`  | Neo4j nodes (full JSON payload, persistent)         |

## Chat and DICE Integration

`ChatActions` (`src/main/java/dev/dunnam/diceanchors/chat/ChatActions.java`) — an Embabel `@EmbabelComponent`. Receives user messages, queries active anchors via `AnchorEngine.inject("chat")`, renders the system prompt with anchors, sends to the LLM, and publishes a `ConversationAnalysisRequestEvent` for async DICE extraction.

### DICE Extraction Pipeline

```
ChatActions
    | publishes ConversationAnalysisRequestEvent
    v
ConversationPropositionExtraction
    | listens for events, extracts propositions via DICE
    v
PropositionPipeline
    | processes propositions (dedup, trust eval)
    v
AnchorPromoter
    | promotes qualified propositions to anchors
    v
AnchorRepository (Neo4j)
```

**Promotion gate sequence** (in `extract/`): confidence -> dedup -> conflict -> trust -> promote.

### Revision and Supersession

When the conflict detection pipeline classifies a conflict as `REVISION` (not `CONTRADICTION` or `WORLD_PROGRESSION`), `RevisionAwareConflictResolver` applies authority-gated rules:

- **CANON** — never revisable (immutable via `CanonizationGate`)
- **RELIABLE** — configurable via `dice-anchors.anchor.revision.reliable-revisable` (default: false)
- **UNRELIABLE** — revisable when incoming confidence exceeds threshold
- **PROVISIONAL** — always revisable

A `REPLACE` outcome triggers `AnchorEngine.supersede()` — archives the predecessor and creates a `SUPERSEDES` relationship in Neo4j linking successor to predecessor. `SupersessionReason` tracks why (CONFLICT_REPLACEMENT, BUDGET_EVICTION, DECAY_DEMOTION, USER_REVISION, MANUAL).

`AnchorMutationStrategy` (SPI) gates all mutation attempts. Default: `HitlOnlyMutationStrategy` — only UI-sourced mutations pass; LLM-initiated and conflict-resolver-initiated mutations are blocked.

## Simulation Execution

### Per-Run Flow

1. `SimulationService` allocates `contextId = sim-{uuid}` and seeds scenario anchors
2. Scene-setting turn 0 executes when `scenario.setting()` is non-blank and extraction is enabled (DM narrates setting; DICE extraction captures initial propositions)
3. Turn loop executes through `SimulationTurnExecutor`
4. Evaluated turns produce verdicts: `CONTRADICTED` / `CONFIRMED` / `NOT_MENTIONED`
5. `ScoringService` computes run metrics and persists `SimulationRunRecord`
6. Context cleanup runs in `finally`

### Adversarial Scenario Generation

- **Scripted:** Scenario `turns[]` with explicit `type`/`strategy`/`targetFact`
- **Adaptive:** `adversaryMode: adaptive` with `TieredEscalationStrategy` + `AdaptiveAttackPrompter`
- **Unscripted fallback:** `SimulationService.generateAdversarialMessage(...)` samples fact and strategy, then asks the model to generate attack text

Scenario definitions live in `src/main/resources/simulations/*.yml`.

### Benchmark Matrix

`BenchmarkRunner` executes repetitions for one `(condition, scenario)` cell. `ExperimentRunner` iterates the full condition x scenario matrix. `ResilienceReportBuilder` turns aggregated results into markdown reports.

## Configuration

`DiceAnchorsProperties` (`src/main/java/dev/dunnam/diceanchors/DiceAnchorsProperties.java`) — a `@ConfigurationProperties` record bound to the `dice-anchors` prefix. Everything flows through here.

| Setting                          | Default      |
|----------------------------------|--------------|
| `anchor.budget`                  | 20           |
| `anchor.initial-rank`            | 500          |
| `anchor.auto-activate-threshold` | 0.65         |
| `chat.persona`                   | "dm"         |
| `chat.max-words`                 | 200          |
| `chat.chat-llm.model`            | gpt-4.1-mini |
| `memory.trigger-interval`        | 6            |
| `memory.window-size`             | 20           |
| `sim.evaluator-model`            | gpt-4.1-mini |
| `persistence.clear-on-start`     | true         |
| `conflict-detection.strategy`    | llm          |
| `conflict-detection.model`       | gpt-4o-nano  |
| `run-history.store`              | memory       |
| `anchor.revision.enabled`      | true         |
| `anchor.revision.reliable-revisable` | false  |
| `anchor.revision.confidence-threshold` | 0.75 |
| `anchor.reliable-rank-threshold` | 400        |
| `anchor.unreliable-rank-threshold` | 200      |
| `anchor.tier.hot-threshold`    | 600          |
| `anchor.tier.warm-threshold`   | 350          |
| `anchor.tier.hot-decay-multiplier` | 1.5      |
| `anchor.tier.warm-decay-multiplier` | 1.0     |
| `anchor.tier.cold-decay-multiplier` | 0.6     |
| `conflict.tier.hot-defense-modifier` | 0.1    |
| `conflict.tier.warm-defense-modifier` | 0.0   |
| `conflict.tier.cold-defense-modifier` | -0.1  |
| `retrieval.mode`               | HYBRID       |
| `retrieval.min-relevance`      | 0.0          |
| `retrieval.baseline-top-k`     | 5            |
| `retrieval.tool-top-k`         | 5            |
| `retrieval.scoring.authority-weight` | 0.4    |
| `retrieval.scoring.tier-weight` | 0.3         |
| `retrieval.scoring.confidence-weight` | 0.3   |

### External Dependencies

| Service             | Connection    | Default                                            |
|---------------------|---------------|----------------------------------------------------|
| Neo4j               | Bolt protocol | `localhost:7687` (env: `NEO4J_HOST`, `NEO4J_PORT`) |
| OpenAI API          | HTTPS         | via `OPENAI_API_KEY` env var                       |
| Langfuse (optional) | OTEL          | `localhost:3000/api/public/otel`                   |
