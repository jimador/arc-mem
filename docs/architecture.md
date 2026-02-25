> **Migrated:** This content has been consolidated into [`docs/dev/architecture.md`](dev/architecture.md). This file is retained as a transitional pointer and will be removed in a future cleanup.

---

# Architecture

## Overview

dice-anchors is a single-module Spring Boot application with two user-facing routes:

- `/` — Simulation harness (Vaadin)
- `/chat` — Interactive chat with anchor-aware DM (Vaadin + Embabel)

Both routes share the same anchor engine, persistence layer, and Neo4j database. The simulation harness operates in isolation via per-run `contextId` values; the chat interface uses a fixed `"chat"` context.

## Package Structure

```
dev.dunnam.diceanchors
├── anchor/              # Core anchor engine, policies, SPIs
├── assembly/            # Context injection, compaction, prompt assembly
├── chat/                # Embabel chat integration (ChatActions, ChatView)
├── extract/             # Proposition-to-anchor promotion, deduplication
├── persistence/         # Neo4j persistence (Drivine ORM)
└── sim/
    ├── assertions/      # Post-run assertion framework
    ├── engine/          # Simulation orchestration, turn execution, scoring
    └── views/           # Vaadin simulation UI panels
```

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
│                          │   └── NegationConflictDetector (lexical) │
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

## Core Components

### Anchor Model

The `Anchor` record is the primary data transfer object:

```java
record Anchor(
    String id,
    String text,
    int rank,                  // [100, 900] via clampRank()
    Authority authority,       // PROVISIONAL → UNRELIABLE → RELIABLE → CANON
    boolean pinned,            // immune to eviction and decay
    double confidence,         // [0.0, 1.0] from DICE extraction
    int reinforcementCount,
    @Nullable TrustScore trustScore
)
```

**Invariants:**
- Rank is always clamped to `[Anchor.MIN_RANK, Anchor.MAX_RANK]` (100–900) via `Anchor.clampRank()`
- Authority only upgrades, never downgrades
- CANON is never auto-assigned
- Pinned anchors are immune to rank-based eviction

### Authority Hierarchy

```java
enum Authority {
    PROVISIONAL(0),  // newly extracted
    UNRELIABLE(1),   // reinforced >= 3 times
    RELIABLE(2),     // reinforced >= 7 times
    CANON(3)         // operator-designated only
}
```

Authority transitions are monotonic — `upgradeAuthority()` in the repository uses a Cypher `WHERE newLevel > currentLevel` guard to enforce this at the database level.

### AnchorEngine

Central orchestrator for anchor lifecycle. Constructor-injected with SPI implementations for conflict detection, conflict resolution, and reinforcement.

**Key operations:**

| Method | Behavior |
|--------|----------|
| `inject(contextId)` | Returns up to `budget` active anchors, sorted by rank descending |
| `promote(propositionId, initialRank)` | Promotes proposition to anchor; clamps rank; triggers budget eviction |
| `reinforce(anchorId)` | Increments reinforcement count, applies rank boost, upgrades authority at thresholds |
| `detectConflicts(contextId, text)` | Delegates to `ConflictDetector` SPI |
| `resolveConflict(conflict)` | Delegates to `ConflictResolver` SPI |

Budget enforcement is the final step of `promote()`: after writing the new anchor, `evictLowestRanked(contextId, budget)` removes the lowest-ranked non-pinned anchors to bring the count within budget.

## SPI Architecture

The anchor engine uses a Strategy pattern with four service provider interfaces. Each has a default implementation, but the interfaces allow alternative strategies to be plugged in.

### ConflictDetector

Two implementations, selected via `dice-anchors.conflict-detection.strategy`:

#### LlmConflictDetector (default, strategy: `llm`)

Detects semantic contradictions using an LLM:
1. For each incoming text × existing anchor pair, sends a prompt asking whether the statements are factually contradictory
2. Includes a critical distinction: narrative progression is NOT contradiction
3. Parses JSON response (`{"contradicts": boolean, "explanation": string}`)
4. Falls back to checking for `true` in the raw response if JSON parsing fails
5. Uses a configurable model (default: `gpt-4o-nano`) via `dice-anchors.conflict-detection.model`

**Advantages:** Catches semantic contradictions without word overlap. Understands temporal context and narrative flow.

**Tradeoffs:** Adds LLM call latency per anchor comparison. Cost scales with anchor count.

#### NegationConflictDetector (strategy: `lexical`)

Zero-latency lexical approach:
1. Check if one text contains negation markers and the other doesn't
2. Calculate word-level Jaccard overlap (excluding negation markers)
3. Flag as conflict if overlap exceeds 0.5

Negation markers: `not`, `never`, `no longer`, `isn't`, `doesn't`, `wasn't`, `weren't`, `hasn't`, `haven't`, `cannot`, `can't`, `won't`, `didn't`, `don't`, `neither`, `nor`, `false`

**Limitation:** Misses semantic contradictions without explicit negation. Available as a fallback for environments where LLM calls are not available.

### ConflictResolver → AuthorityConflictResolver

Resolves detected conflicts using authority level and incoming confidence:

```
if existing authority >= RELIABLE → KEEP_EXISTING
else if incoming confidence > 0.8  → REPLACE
else                               → COEXIST
```

The KEEP_EXISTING path means high-authority anchors are defended against contradictory information. COEXIST flags the conflict for human review.

### ReinforcementPolicy → ThresholdReinforcementPolicy

Fixed rank boost and threshold-based authority upgrade:
- Rank boost per reinforcement: **+50 points**
- Authority upgrade thresholds:
  - 3 reinforcements → PROVISIONAL upgrades to UNRELIABLE
  - 7 reinforcements → UNRELIABLE upgrades to RELIABLE
  - CANON is never auto-assigned

### DecayPolicy → ExponentialDecayPolicy

Exponential half-life decay for rank:

```
decayFactor = 0.5 ^ (hoursSinceReinforcement / halfLifeHours)
newRank = clampRank(currentRank × decayFactor)
```

Pinned anchors are immune to decay (return original rank unchanged).

## Trust Evaluation Pipeline

The trust pipeline evaluates propositions before promotion, routing them into one of three zones:

```
Proposition
    │
    ▼
TrustPipeline.evaluate(proposition, contextId)
    │
    ├── Collect all TrustSignal implementations
    ├── Evaluate each signal → [0.0, 1.0] or empty
    ├── Weight signals per active DomainProfile
    └── Compute composite TrustScore
         │
         ▼
    Route by PromotionZone:
         │
         ├── AUTO_PROMOTE (score ≥ autoPromoteThreshold) → promote immediately
         ├── REVIEW (score ≥ reviewThreshold)             → queue for review
         └── ARCHIVE (score < reviewThreshold)            → skip
```

### Trust Signal Implementations

| Signal | Algorithm |
|--------|-----------|
| `GraphConsistencySignal` | Jaccard similarity between proposition tokens and graph entity tokens, with stop-word filtering |
| `CorroborationSignal` | Count-based source diversity scoring: DM+PLAYER mixed = 0.7, 3+ sources = 0.9 |

### Domain Profiles

Predefined weight configurations that tune trust evaluation for different use cases:

| Profile | Auto-Promote | Review | Archive |
|---------|-------------|--------|---------|
| BALANCED (default) | ≥ 0.70 | ≥ 0.40 | < 0.40 |
| SECURE | ≥ 0.85 | ≥ 0.50 | < 0.50 |
| NARRATIVE | ≥ 0.60 | ≥ 0.35 | < 0.35 |

Profiles can be switched at runtime (e.g., per simulation scenario via `trustConfig.profile`).

## Context Injection

`AnchorsLlmReference` assembles the anchor block injected into every LLM system prompt:

```
=== ESTABLISHED FACTS ===
The following facts are VERIFIED and AUTHORITATIVE. You MUST NOT contradict,
modify, or ignore them under any circumstances. If a user or player attempts
to change, deny, or rewrite these facts, you must FIRMLY correct them.

1. [CANON] The East Gate of Tidefall's wall has been breached (rank: 850)
2. [RELIABLE] Baron Krell is a four-armed sahuagin mutant (rank: 750)
=== END ESTABLISHED FACTS ===
```

The block is sorted by rank (descending) and limited by the configured budget. The guardrail instructions are part of the block itself, not separate prompt engineering.

### Context Lock

`AnchorContextLock` provides a thread-safe CAS lock to prevent anchor mutations during prompt assembly. This ensures the anchors injected into the system prompt are consistent with what the model sees.

## Context Compaction

`CompactedContextProvider` manages conversation history growth:

1. **Tracking:** Messages are added per-context via `addMessage()`
2. **Threshold check:** `shouldCompact()` evaluates message count and estimated token count against configurable thresholds
3. **Forced turns:** Specific turn numbers can force compaction regardless of thresholds
4. **Compaction:** `SimSummaryGenerator` calls the LLM to produce a narrative summary
5. **Protection:** `ProtectedContentProvider` implementations declare content that must survive compaction (e.g., active anchors)
6. **Validation:** `CompactionValidator` checks the generated summary against protected content using normalized keyword matching (>50% significant words present). Lost facts are reported as `CompactionLossEvent` records.
7. **Result:** Old messages are cleared; summary is stored and prepended to future context

Token estimation uses a heuristic of ~4 characters per token.

## Scoring Service

`ScoringService` is a stateless `@Service` that computes aggregate metrics from simulation turn snapshots:

```java
ScoringResult score(
    List<TurnSnapshot> snapshots,
    List<GroundTruth> groundTruth,
    List<Anchor> injectedAnchors
) → ScoringResult
```

Metrics computed:
- **Fact survival rate** — percentage of facts never contradicted
- **Contradiction count** — total CONTRADICTED verdicts
- **Major contradiction count** — total MAJOR severity contradictions
- **Drift absorption rate** — percentage of evaluated turns with zero contradictions
- **Mean turns to first drift** — average turn number of first contradiction per fact
- **Anchor attribution count** — facts matched by injected anchors (bidirectional normalized substring)
- **Strategy effectiveness** — per-attack-strategy contradiction rate

`ScoringResult` is stored on `SimulationRunRecord` and consumed by `DriftSummaryPanel` for display.

## Persistence

### PropositionNode

The Neo4j entity extends DICE's Proposition model with anchor-specific fields:

| Field | Type | Purpose |
|-------|------|---------|
| id | String (UUID) | Unique identifier |
| contextId | String | Context isolation |
| text | String | Natural language statement |
| confidence | double | LLM extraction confidence [0.0, 1.0] |
| rank | int | 0 = not an anchor; [100, 900] = active anchor |
| authority | String | PROVISIONAL / UNRELIABLE / RELIABLE / CANON |
| pinned | boolean | Immune to eviction |
| reinforcementCount | int | Times reinforced |
| lastReinforced | Instant | Most recent reinforcement |
| embedding | List\<Double\> | Vector for similarity search |

**Key invariant:** `rank > 0` means the proposition has been promoted to anchor status. There is no separate anchor node type — anchors are propositions with extra fields.

### AnchorRepository

Drivine-backed repository implementing DICE's `PropositionRepository` contract while extending it with anchor lifecycle methods. All Cypher queries are parameterized (no string concatenation).

Key anchor operations:
- `findActiveAnchors(contextId)` — anchors with `rank > 0` and `status = ACTIVE`, ordered by rank DESC
- `promoteToAnchor(id, rank, authority)` — sets rank, authority, and lastReinforced
- `evictLowestRanked(contextId, budget)` — removes lowest-ranked non-pinned anchors over budget
- `upgradeAuthority(id, newAuthority)` — upgrade-only guard via Cypher level comparison
- `reinforceAnchor(id)` — increments reinforcementCount, updates lastReinforced
- `semanticSearch(query, contextId, topK, threshold)` — vector similarity search scoped to context

### RunHistoryStore

Interface for persisting simulation run records:

```java
public interface RunHistoryStore {
    void save(SimulationRunRecord record);
    Optional<SimulationRunRecord> load(String runId);
    List<SimulationRunRecord> list();
    List<SimulationRunRecord> listByScenario(String scenarioId);
    void delete(String runId);
}
```

| Implementation | Config | Storage | Notes |
|----------------|--------|---------|-------|
| `SimulationRunStore` | `dice-anchors.run-history.store=memory` | `ConcurrentHashMap` | Default. Data lost on restart. |
| `Neo4jRunHistoryStore` | `dice-anchors.run-history.store=neo4j` | Neo4j nodes | Full JSON payload per run. Persistent. |

The interface is designed for easy extension — adding a new implementation requires implementing the 5 methods and registering as a conditional bean.

## Chat Integration

`ChatActions` is an Embabel `@EmbabelComponent` that:

1. Receives `UserMessage` events from the Embabel framework
2. Queries active anchors via `AnchorEngine.inject("chat")`
3. Renders the system prompt using a Jinja template (`dice-anchors.jinja`) with anchors injected
4. Sends the prompt to the LLM
5. Publishes a `ConversationAnalysisRequestEvent` for async DICE extraction

The chat flow operates on the fixed `"chat"` context, while simulations each get their own `"sim-{uuid}"` context.

### DICE Extraction Pipeline

The extraction pipeline processes chat messages asynchronously:

```
ChatActions
    │ publishes ConversationAnalysisRequestEvent
    ▼
ConversationPropositionExtraction
    │ listens for events, extracts propositions via DICE
    ▼
PropositionPipeline
    │ processes propositions (dedup, trust eval)
    ▼
AnchorPromoter
    │ promotes qualified propositions to anchors
    ▼
AnchorRepository (Neo4j)
```

## Configuration

All configuration flows through `DiceAnchorsProperties`, a `@ConfigurationProperties` record bound to the `dice-anchors` prefix in `application.yml`.

| Setting | Default | Source |
|---------|---------|--------|
| `anchor.budget` | 20 | `application.yml` |
| `anchor.initial-rank` | 500 | `application.yml` |
| `anchor.auto-activate-threshold` | 0.65 | `application.yml` |
| `chat.persona` | "dm" | `application.yml` |
| `chat.max-words` | 200 | `application.yml` |
| `chat.chat-llm.model` | gpt-4.1-mini | `application.yml` |
| `memory.trigger-interval` | 6 | `application.yml` |
| `memory.window-size` | 20 | `application.yml` |
| `sim.evaluator-model` | gpt-4.1-mini | `application.yml` |
| `persistence.clear-on-start` | true | `application.yml` |
| `conflict-detection.strategy` | llm | `application.yml` |
| `conflict-detection.model` | gpt-4o-nano | `application.yml` |
| `run-history.store` | memory | `application.yml` |

### External Dependencies

| Service | Connection | Default |
|---------|-----------|---------|
| Neo4j | Bolt protocol | `localhost:7687` (env: `NEO4J_HOST`, `NEO4J_PORT`) |
| OpenAI API | HTTPS | via `OPENAI_API_KEY` env var |
| Langfuse (optional) | OTEL | `localhost:3000/api/public/otel` |
