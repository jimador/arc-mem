# Anchors for DICE

Anchors add a trust-governed working memory layer on top of DICE + Embabel so long-running chats don’t silently drift.

![simulation](docs/images/anchor-sim-attack.png)

## 1. The Problem

LLMs degrade in multi-turn conversations. [Laban et al. (2025)](https://arxiv.org/abs/2505.06120) measured across 200,000+ simulated conversations with 15 LLMs: a 39% average performance drop from single-turn to multi-turn settings, a 112% increase in unreliability (variance between best and worst runs), with degradation starting at 2+ turns regardless of information density. Models prematurely attempt solutions with incomplete information and then over-rely on those early attempts. Temperature reduction to 0.0 eliminates single-turn unreliability but 30%+ unreliability persists in multi-turn. The root cause is not context capacity but the absence of any mechanism to distinguish **load-bearing facts** from ambient context.

Standard mitigations fall short:

| Approach               | Why it fails                                                                             |
|------------------------|------------------------------------------------------------------------------------------|
| Longer context windows | More tokens does not mean better attention. Lost-in-the-middle effects persist.          |
| Summarization          | Lossy. Recursive summarization compounds errors.                                         |
| RAG                    | Retrieves relevant content but does not mandate consistency. No enforcement.             |
| Prompt engineering     | "Remember these facts" degrades as context grows. Vulnerable to confident contradiction. |

The problem is acute in agentic systems where invariants must hold across long horizons under adversarial pressure. A dead NPC must stay dead. A drug allergy must never be forgotten. A legal privilege must never be waived.

### Why D&D as a Test Domain

This project uses tabletop RPGs as its proving ground, but the approach is domain-agnostic. D&D is a particularly useful sandbox because:

- **Invariants are legible.** World facts, character states, and rules form a clear set of constraints. A dead NPC stays dead (barring necromancy shenanigans). Violations are immediately obvious.
- **Creative freedom is the point.** The value of the LLM is improvisation, elaboration, and responsive storytelling. Restricting this defeats the purpose.
- **Adversarial pressure is natural.** Players routinely test boundaries, make false claims, and try to manipulate the narrative — providing organic adversarial testing that mirrors real-world prompt injection concerns.
- **Long horizons are the norm.** Campaigns span dozens of sessions. Facts established early must persist across hundreds of turns.

This tension — *be creative, but don't break the rules* — exists wherever agentic systems operate within constraints.

## What are Anchors?

LLMs are great at continuation, not persistence. In long-running conversations, facts that *should* stay stable slowly degrade as new turns reweight attention. That’s fine for creativity; it’s a problem when you’re trying to preserve rules, world state, decisions, or any other “we already settled this” constraints.

**Anchors** are promoted [DICE](https://github.com/embabel/dice) propositions with extra control fields:

- **Rank** — importance under memory pressure
- **Authority** — trust level (PROVISIONAL ↔ UNRELIABLE ↔ RELIABLE ↔ CANON); bidirectional with invariant guards
- **Authority ceiling** — the highest level this fact can auto-reach based on provenance
- **Budget membership** — explicit inclusion in a bounded working set (default: max 20 active anchors)

At runtime, anchors are injected into every prompt as a ranked reference block. That makes memory an explicit, governed mechanism instead of an accidental side effect:

- conflict checks gate contradictory updates
- trust scoring gates promotion and authority movement
- budget enforcement evicts low-value anchors first
- reinforcement/decay updates importance over time

Knowledge graphs answer “what facts exist.” Anchors answer “what must stay salient and trusted *right now*.” That distinction is the whole point.

### Design Decisions

**Explicit state over implicit memory.** Facts are managed state with rank, authority, and provenance rather than raw conversation history the model must parse.

**Authority tiers as governance primitive.** A four-level hierarchy creates policy hooks you don't get with flat retrieval stacks. Bidirectional transitions with invariant guards work to prevent adversarial downgrade and unauthorized escalation.

**Hard budget enforcement.** A cap (default 20 active anchors) prevents context bloat. Long-context capability doesn't fix attention-allocation failures. In our testing, a small focused fact set outperforms a large dump.

**Adversarial resistance by design.** Anchors are formatted as authoritative instructions with explicit correction directives. The model is told to *actively resist* contradiction attempts, not just recall facts.

**Mandatory injection over retrieval.** RAG content competes for attention. Summarization can drop things. Anchors occupy a fixed system-prompt block injected before the user message — they're always there.

## Why DICE + Embabel?

This repo is an extension layer, not a replacement stack:

- **DICE** handles proposition extraction, grounding, and graph persistence.
- **Embabel** handles agent orchestration plus prompt/action lifecycle integration.
- **Anchors** adds trust/authority governance and bounded working-memory control (where base proposition systems are intentionally neutral).

Practically: DICE/Embabel handle acquisition and flow; Anchors handles memory prioritization, trust-constrained mutation, and drift resistance.

## Architecture Overview

### Core Packages

| Package        | Purpose |
|----------------|---------|
| `anchor/`      | Anchor lifecycle: `AnchorEngine` (budget + promotion), conflict detection/resolution, trust pipeline, reinforcement/decay policies, memory tiers, invariant evaluation |
| `assembly/`    | Prompt assembly: `AnchorsLlmReference` (inject anchors into prompts), `PromptBudgetEnforcer` (token limits), `CompactedContextProvider` (context summarization), `AnchorContextLock` (multi-request safety) |
| `chat/`        | Chat flow: `ChatView` (Vaadin UI), `ChatActions` (Embabel Agent `@EmbabelComponent`), `AnchorTools` |
| `extract/`     | DICE integration: `DuplicateDetector` (near-dupes), `AnchorPromoter` (promotion) |
| `persistence/` | Neo4j repository layer: `AnchorRepository`, `PropositionNode` (Drivine ORM) |
| `prompt/`      | Prompt templates: `PromptTemplates`, `PromptPathConstants`, Jinja2 template files |
| `sim/`         | Simulation harness: engine (orchestration, LLM calls, scoring, adversary strategies), views (`SimulationView`, `RunInspectorView`, `BenchmarkView`, `ContextInspectorPanel`), benchmark (`BenchmarkRunner`, ablation conditions), report (`ResilienceReport`, `MarkdownReportRenderer`), assertions (post-run validation) |

### Chat Flow

```text
ChatView (user types)
  → ChatActions (Embabel @EmbabelComponent)
  → AnchorsLlmReference (inject anchors into system prompt)
  → LLM (gpt-4.1-mini) + PromptBudgetEnforcer (token limits)
  → Extract propositions (DICE extraction)
  → AnchorPromoter (promote promising propositions to anchors)
  → AnchorEngine (reinforce existing anchors, manage budget)
  → Neo4j persistence (Drivine ORM)
```

### Simulation Flow

```text
SimulationView (user selects scenario and hits "Run")
  → SimulationService (load scenario YAML, seed anchors, phase state machine)
  → SimulationTurnExecutor (per turn):
     - Assemble anchor + system/user prompts
     - Call LLM (DM response)
     - If ATTACK turn: evaluate drift against ground truth (concurrent)
     - Extract propositions from DM response (concurrent)
     - Promote extracted propositions (concurrent)
  → Adversary strategy (adaptive or scripted from scenario YAML)
  → LLM again (generate adversarial player message)
  → Repeat until scenario ends
  → SimulationView updates with final anchor state, drift verdicts, and rankings
```

## Key Features

### Anchor Lifecycle

An anchor is a fact promoted to special status in the LLM’s context:

- **Rank** `[100–900]` — higher rank = more influence under budget pressure
- **Authority** — PROVISIONAL → UNRELIABLE → RELIABLE → CANON (bidirectional — promoted via reinforcement, demoted via decay/trust re-evaluation; CANON is never auto-assigned and immune to auto-demotion)
- **Reinforcement tracking** — reuse boosts rank and may upgrade authority
- **Decay** — unused anchors decay exponentially; eventually auto-archived at `MIN_RANK`
- **Budget enforcement** — max 20 active anchors; evicts lowest-ranked non-pinned when exceeded

### Trust Pipeline

`TrustPipeline` runs propositions through multiple trust signals before promotion:

- source authority assessment
- extraction confidence scoring
- reinforcement history tracking
- `TrustAuditRecord` captures the decision trail
- domain-specific invariant rules via `InvariantEvaluator`
- `CanonizationGate` controls CANON assignment — always explicit, never automatic

### Adversarial Simulation

The simulation harness runs scenarios turn-by-turn with drift evaluation:

- **Drift verdicts** — each ground-truth fact is evaluated as `CONTRADICTED`, `CONFIRMED`, or `NOT_MENTIONED` per turn
- **Epistemic hedging** — DM declines to affirm without asserting the opposite → `NOT_MENTIONED`, not `CONTRADICTED`
- **Scene-setting turn 0** — when `setting` is non-blank and extraction is enabled, an `ESTABLISH` turn runs before turn 1 to seed initial propositions organically
- **Adversary modes** — scripted (explicit player messages in YAML) or adaptive (strategy selection based on anchor state and attack history)

### Context Compaction

`CompactedContextProvider` summarizes older context when token thresholds are exceeded. `CompactionValidator` checks that protected facts survive compaction. Memory tiers (`COLD`, `WARM`, `HOT`) influence decay rates and eviction priority.

### Lifecycle Flow

```
DICE Extraction → Duplicate Detection → Conflict Detection
                                              │
                                    ┌─────────┴─────────┐
                                no conflict         conflict
                                    │                    │
                              Trust Evaluation    Authority-Based
                                    │              Resolution
                                    ▼
                           Promotion Decision
                        (AUTO_PROMOTE / REVIEW / ARCHIVE)
                                    │
                              Budget Enforcement
                          (evict lowest non-pinned)
                                    │
                             Active Anchor Pool
                     (reinforcement, decay, pin controls)
                                    │
                            Context Assembly
                    (top-ranked → ESTABLISHED FACTS block)
```

## Anchor API Reference

### The Anchor Record

| Field | Type | Range/Default | Purpose |
|-------|------|---------------|---------|
| `id` | String | UUID | Unique identifier |
| `text` | String | — | Natural language statement |
| `rank` | int | [100, 900] | Importance under memory pressure; clamped via `clampRank()` |
| `authority` | Authority | PROVISIONAL..CANON | Trust level governing conflict resolution and eviction |
| `pinned` | boolean | false | Immune to eviction, decay, and auto-demotion |
| `confidence` | double | [0.0, 1.0] | DICE extraction confidence |
| `reinforcementCount` | int | 0+ | Times re-confirmed; drives authority upgrades |
| `trustScore` | TrustScore | nullable | Composite trust evaluation |
| `diceImportance` | double | [0.0, 1.0], default 0.0 | DICE-assigned importance; > 0.7 boosts rank |
| `diceDecay` | double | >= 0.0, default 1.0 | DICE-assigned decay modifier; adjusts effective half-life |
| `memoryTier` | MemoryTier | COLD/WARM/HOT | Tier classification influencing decay rate |

### Rank

Range `[100, 900]`, clamped by `Anchor.clampRank()`. Initial rank: 500 (configurable). Reinforcement: +50 per confirmation. Decay: exponential with tier-modulated half-life.

Rank-triggered authority demotion:
- RELIABLE demotes to UNRELIABLE when rank drops below 400
- UNRELIABLE demotes to PROVISIONAL when rank drops below 200
- CANON and pinned anchors are immune to rank-triggered demotion

### Authority

```
PROVISIONAL(0) < UNRELIABLE(1) < RELIABLE(2) < CANON(3)
```

Authority is **bidirectional** with the following invariants:

| ID | Invariant |
|----|-----------|
| A3a | CANON is never auto-assigned; requires explicit action through `CanonizationGate` |
| A3b | CANON is immune to automatic demotion (decay, trust re-evaluation); only explicit action can demote |
| A3c | Automatic demotion applies to RELIABLE → UNRELIABLE → PROVISIONAL via rank decay or trust degradation |
| A3d | Pinned anchors are immune to automatic demotion |
| A3e | All transitions (both directions) publish `AuthorityChanged` lifecycle events |

RFC 2119 compliance mapping: CANON = MUST (absolute requirement), RELIABLE = SHOULD (strong recommendation), UNRELIABLE = MAY (optional consideration), PROVISIONAL = tentative.

### Trust Pipeline

`TrustPipeline` runs propositions through pluggable `TrustSignal` implementations weighted by a `DomainProfile`:

| Profile | Auto-Promote | Review | Archive | Tuning |
|---------|-------------|--------|---------|--------|
| BALANCED | >= 0.70 | >= 0.40 | < 0.25 | Equal signal weights |
| SECURE | >= 0.85 | >= 0.50 | < 0.30 | Heavy on graph consistency |
| NARRATIVE | >= 0.60 | >= 0.35 | < 0.20 | Heavy on source authority |

Routing: `PromotionZone.AUTO_PROMOTE` promotes immediately, `REVIEW` queues for manual review, `ARCHIVE` skips. Authority ceiling constrains max auto-reachable authority based on provenance.

### Memory Tiers

| Tier | Rank Range | Decay Multiplier | Semantics |
|------|-----------|-------------------|-----------|
| HOT | >= 600 | 1.5x (slower decay) | Protected window |
| WARM | 350–599 | 1.0x (baseline) | Normal decay |
| COLD | < 350 | 0.6x (faster decay) | Accelerated cleanup |

Tier thresholds and multipliers are configurable via `dice-anchors.anchor.tier.*`.

### Conflict Detection and Resolution

`CompositeConflictDetector` chains detection strategies (configurable via `dice-anchors.conflict-detection.strategy`):

| Strategy | Behavior |
|----------|----------|
| `LEXICAL` | `NegationConflictDetector` only (zero LLM cost) |
| `LLM` | `LlmConflictDetector` only (semantic comparison) |
| `HYBRID` | Negation + LLM with subject filtering |

Conflicts are classified by `ConflictType`:

| Type | Meaning | Resolution |
|------|---------|------------|
| `CONTRADICTION` | Incoming asserts the opposite of existing | Authority-based: RELIABLE+ kept, high-confidence replaces lower |
| `REVISION` | Incoming updates/refines existing (non-adversarial) | Authority-gated: CANON immutable, PROVISIONAL always replaceable |
| `WORLD_PROGRESSION` | Narrative change, not a conflict | Always `COEXIST` |

Resolution outcomes: `KEEP_EXISTING`, `REPLACE`, `DEMOTE_EXISTING`, `COEXIST`. Memory tier defense modifiers adjust resolution thresholds (HOT anchors get +0.1 defense, COLD get -0.1).

### Promotion Pipeline

`AnchorPromoter` runs a 5-gate pipeline: **confidence → dedup → conflict → trust → promote**.

1. **Confidence gate** — drops propositions below `autoActivateThreshold` (default 0.65)
2. **Dedup gate** — `DuplicateDetector` removes near-duplicates of existing anchors
3. **Conflict gate** — `ConflictDetector` checks for contradictions/revisions with existing anchors; `ConflictResolver` determines outcome
4. **Trust gate** — `TrustPipeline` evaluates composite trust score; routes to AUTO_PROMOTE, REVIEW, or ARCHIVE
5. **Promote** — `AnchorEngine.promote()` writes the anchor and runs budget enforcement

### Canonization Gate

`CanonizationGate` enforces human-in-the-loop control over CANON authority:
- Canonization and decanonization requests queue as `PENDING` in Neo4j
- Auto-approved in simulation contexts (`contextId` starting with `sim-`)
- In chat contexts, requires explicit `approve()` or `reject()` call
- Stale detection — if the anchor's authority changed since the request was created, the request is marked `STALE`

## DICE Integration

Anchors is a downstream consumer of DICE, not a replacement. Everything here is implemented locally in `dice-anchors`.

### How DICE Concepts Map to Anchors

| DICE Concept            | Anchors Concept                | Relationship                                                                                              |
|-------------------------|--------------------------------|-----------------------------------------------------------------------------------------------------------|
| Proposition extraction  | Raw material                   | DICE extracts propositions from conversation text; Anchors selects a subset for promotion                 |
| Proposition revision    | Conflict/reinforcement trigger | DICE revisions feed Anchors conflict detection and reinforcement pipelines                                |
| Entity mentions         | Subject filtering              | DICE entities used by `SubjectFilter` to scope anchor queries                                             |
| Proposition persistence | Shared store                   | Both use `AnchorRepository` (Neo4j/Drivine). Anchors adds rank/authority/tier metadata                    |
| Incremental analysis    | Turn-by-turn extraction        | DICE `ConversationPropositionExtraction` runs per turn; Anchors processes results through promotion gates |

DICE owns extraction and revision. Anchors owns promotion, lifecycle governance, budget enforcement, and context injection.

### Memory Layering

DICE Agent Memory and Anchors serve different purposes and run as separate layers:

| Layer                   | Mechanism                                          | Purpose                                                                                          |
|-------------------------|----------------------------------------------------|--------------------------------------------------------------------------------------------------|
| **DICE Agent Memory**   | `searchByTopic`, `searchRecent`, `searchByType`    | Broad retrieval of relevant propositions, entity relationships, and historical context on demand |
| **Anchors working set** | Rank-sorted mandatory injection into system prompt | Guaranteed presence of load-bearing facts regardless of retrieval relevance scoring              |

Anchors augments DICE memory — doesn't replace it. DICE retrieval provides the broader knowledge base. Anchors provides the invariant enforcement layer. A proposition can exist in DICE memory and never become an anchor. An anchor always has a corresponding DICE proposition as its origin.

### Low-Trust Knowledge

Not all extracted knowledge deserves anchor status. The authority hierarchy keeps low-trust knowledge available without contaminating the invariant set:

- **PROVISIONAL** anchors carry provenance qualifiers in the injected context (tagged `[PROVISIONAL]`), signaling the model to treat them as tentative.
- Propositions below the AUTO_PROMOTE threshold (trust score < 0.80) enter the REVIEW queue or are archived, remaining in DICE's proposition store for retrieval without occupying anchor budget.
- Authority ceiling (persisted at promotion) constrains how high a low-provenance fact can be upgraded — designed to prevent adversarial escalation through repetition alone.
- The `TrustSignal` composite evaluates source authority, extraction confidence, and reinforcement history to gate promotion decisions.

### Runtime Boundaries

**What remains in DICE:**
- Proposition extraction from conversational text
- Entity mention and relationship identification
- Proposition revision and incremental analysis
- Base persistence schema and query contracts

**What this repo adds:**
- Rank, authority, and trust metadata on propositions
- Promotion pipeline with duplicate/conflict/trust gates (`AnchorPromoter`, `DuplicateDetector`, `LlmConflictDetector`)
- Budget enforcement and eviction policy (`AnchorEngine`, `PromptBudgetEnforcer`)
- Mandatory context injection (`AnchorsLlmReference`, `AnchorContextLock`)
- Decay and reinforcement policies (`ExponentialDecayPolicy`, `ThresholdReinforcementPolicy`)
- Adversarial simulation and drift evaluation harness (`sim/engine/`, `sim/report/`)

### Where Integration Is Rough

- **No DICE lifecycle hooks** — promotion and tiering run after extraction completes. There's no way to hook into the extraction pipeline itself.
- **No temporal validity** — DICE propositions have no `validFrom`/`validTo`. Representing "this was true then but not now" takes app-level workarounds.
- **Separate audit schemas** — anchor lifecycle events and DICE extraction events use different structures, so unified tracing takes extra plumbing.
- **Text-keyed maps** — the trust pipeline and promotion paths key by proposition text, not a stable DICE proposition ID. Text normalization edge cases could cause collisions.
- **Parse failure quarantine** — duplicate/conflict parse failures quarantine instead of auto-accepting, but what operators actually do with quarantined items is still undefined.
- **Upstream divergence** — if DICE adds its own memory tiering, our patterns may need to adapt. Adapter boundaries help, but it's a real risk.
- **Authority is domain policy** — the `PROVISIONAL`/`UNRELIABLE`/`RELIABLE`/`CANON` taxonomy belongs to this application, not to DICE.
- **Temporal state machines** — adding `validFrom`/`validTo` to propositions means real state machine complexity that DICE may not want in its core.

### Integration Summary

| Dimension       | Status                                                                                                                                                                                                    |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Intent**      | Demonstrate working-memory anchoring as a composable layer over DICE extraction                                                                                                                          |
| **Current fit** | DICE provides extraction; Anchors consumes output and adds lifecycle governance. Integration is functional but loosely coupled (no shared hooks)                                                          |
| **Known gaps**  | No DICE lifecycle hooks, no temporal primitives, text-keyed maps, fail-open parse paths                                                                                                                   |
| **Next steps**  | (1) add temporal metadata to persistence model, (2) publish deterministic ablation manifests                                                                                                              |

## How This Compares

| Feature                    | Anchors                                     | MemGPT                  | Graphiti/Zep              | HippoRAG             | ACON            |
|----------------------------|---------------------------------------------|-------------------------|---------------------------|----------------------|-----------------|
| Guaranteed prompt presence | Yes (mandatory injection)                   | Partial (memory blocks) | No (retrieved)            | No (retrieved)       | No (compressed) |
| Importance ranking         | Yes [100-900]                               | No                      | No                        | PageRank-based       | Task-aware      |
| Authority hierarchy        | Yes (4 levels, bidirectional with guards)    | No                      | No                        | No                   | No              |
| Budget enforcement         | Hard cap (20)                               | Token limit             | None                      | Top-k                | Token reduction |
| Conflict detection         | LLM-based semantic + lexical fallback       | No                      | Temporal                  | No                   | No              |
| Adversarial resistance     | Primary design goal                         | Not a focus             | Not a focus               | Not a focus          | Not a focus     |
| Temporal validity          | Not yet (planned)                           | No                      | Yes (bi-temporal)         | No                   | No              |
| Decay/reinforcement        | Exponential decay + threshold reinforcement | Self-edit               | Temporal validity windows | Spreading activation | Failure-driven  |
| Graph-native retrieval     | Neo4j store, no graph retrieval yet         | No                      | Yes                       | Yes                  | No              |

**What's different here:** Adversarial resistance is a primary design goal, not an afterthought. The closest comparison is MemGPT's fixed memory blocks — Anchors adds explicit ranking, authority governance, and lifecycle management on top. Graphiti/Zep's temporal model looks like the most natural complement and a planned integration direction.

## Getting Started

### Prerequisites

| Requirement    | Version | Notes |
|----------------|---------|-------|
| Java           | 25      | Required by the project |
| Docker         | Recent  | For Neo4j container |
| Docker Compose | v2+     | Bundled with Docker Desktop |
| LLM API Key    | —       | OpenAI key required for simulation and chat |
| Maven          | Bundled | Use the included `./mvnw` wrapper |

### Quick Start

```bash
# Clone
git clone <repo-url>
cd dice-anchors

# Start Neo4j
docker-compose up -d

# Build (skip tests for faster iteration)
./mvnw clean compile -DskipTests

# Run the application
OPENAI_API_KEY=sk-... ./mvnw spring-boot:run
```

### Application Routes

| Route                             | View             | Purpose |
|-----------------------------------|------------------|---------|
| `http://localhost:8089`           | SimulationView   | Adversarial simulation harness |
| `http://localhost:8089/chat`      | ChatView         | Interactive anchor demo |
| `http://localhost:8089/benchmark` | BenchmarkView    | Multi-condition ablation benchmarks |
| `http://localhost:8089/run`       | RunInspectorView | Detailed run inspection |

## Running Simulations

Go to `http://localhost:8089` (routes to `SimulationView`).

**UI Controls:**

- **Scenario selector** — dropdown of all loaded scenarios from `src/main/resources/simulations/`
- **Injection toggle** — enable/disable anchor injection mid-run
- **Run / Pause / Resume / Cancel** — turn-boundary execution controls
- **Conversation panel** — turn-by-turn messages with verdict badges
- **Context Inspector** — 4-tab view: anchor state, system prompt, context trace, compaction
- **Anchor Timeline** — visual lifecycle event log
- **Drift Summary** — aggregate metrics (survival rate, contradiction counts, strategy effectiveness)
- **Run History** — cross-run comparison
- **Manipulation Panel** — modify anchor ranks during paused simulation

Each run generates a unique `contextId` (`sim-{uuid}`) for Neo4j isolation.

## Running Benchmarks

Navigate to `http://localhost:8089/benchmark`.

**Configure the benchmark matrix:**

- **Conditions**: `FULL_ANCHORS`, `NO_ANCHORS`, `FLAT_AUTHORITY` (and `NO_TRUST` once implemented)
- **Scenario pack**: deterministic claim pack for primary evidence; stochastic stress pack for secondary
- **Repetitions**: 10-20 per cell for stable results

`ResilienceReportBuilder` builds reports, `MarkdownReportRenderer` renders them. See `docs/evaluation.md` for metrics definitions, integrity checks, and interpretation guidance.

## Scenario Configuration

Scenarios are YAML files in `src/main/resources/simulations/`. Each file defines a complete test case.

**Key fields:**

| Field                                             | Purpose |
|---------------------------------------------------|---------|
| `id`, `category`, `adversarial`                   | Identification and classification |
| `persona`                                         | Player character (name, description, playStyle) |
| `model`, `temperature`, `maxTurns`, `warmUpTurns` | Execution parameters |
| `setting`                                         | Multi-line campaign context injected into the DM system prompt |
| `groundTruth`                                     | Facts to evaluate against (id + text) |
| `seedAnchors`                                     | Pre-seeded anchors (text, authority, rank) |
| `turns`                                           | Scripted player turns with type, strategy, prompt, targetFact |
| `assertions`                                      | Post-run validation (anchor-count, rank-distribution, kg-context-contains, etc.) |
| `trustConfig`                                     | Optional trust profile and weight overrides |
| `compactionConfig`                                | Optional compaction triggers and thresholds |

**Scene-setting turn 0** — when `setting` is non-blank and extraction is enabled, an `ESTABLISH` turn runs before turn 1. The DM narrates the setting; DICE extraction captures initial propositions as anchors.

Current corpus: 22 scenarios (plus strategy catalog), 357 scripted turns, 180 evaluated turns.

### Available Scenarios

- `adaptive-tavern-fire.yml` — Adaptive adversary in tavern fire narrative
- `adversarial-contradictory.yml` — Straightforward contradiction attacks
- `adversarial-displacement.yml` — Anchor displacement attacks
- `adversarial-poisoned-player.yml` — Attack via indirect NPC persona compromise
- `anchor-drift.yml` — Test semantic drift over long turns
- `authority-inversion-chain.yml` — Authority hierarchy inversion attacks
- `balanced-campaign.yml` — Multi-session campaign with dormancy
- `budget-starvation-interference.yml` — Budget exhaustion via low-value anchor flooding
- `compaction-stress.yml` — Stress-test context summarization
- `conflicting-canon-crisis.yml` — Conflicting CANON-level assertions
- `cursed-blade.yml` — Narrative scenario (artifact curse detection)
- `dead-kingdom.yml` — High-complexity kingdom state
- `dormancy-revival.yml` — Test rank decay and archive/revival
- `dungeon-of-mirrors.yml` — Illusion-based anchor confusion attacks
- `episodic-recall.yml` — Multi-episode anchor retention
- `evidence-laundering-poisoning.yml` — Evidence laundering through indirect attribution
- `extraction-baseline.yaml` — Extraction accuracy baseline (no attacks)
- `extraction-under-attack.yaml` — Extraction accuracy under adversarial pressure
- `gen-adversarial-dungeon.yml` — LLM-generated dungeon + adaptive attacks
- `gen-easy-dungeon.yml` — LLM-generated dungeon + baseline (no attacks)
- `multi-session-campaign.yml` — Anchors persisted across sessions
- `narrative-dm-driven.yml` — DM-controlled narrative (no adversary)
- `trust-evaluation-basic.yml` — Basic trust score evaluation
- `trust-evaluation-full-signals.yml` — Full trust score with all signals
- `strategy-catalog.yml` — Not a scenario; defines attack strategies for adaptive mode

### YAML Schema Reference

```yaml
id: string                        # Unique identifier
category: string                  # adversarial | baseline | trust | dormancy | multi-session | compaction | extraction | ...
adversarial: boolean              # Whether the scenario includes attacks

persona:
  name: string                    # Player character name
  description: string             # Character background
  playStyle: string               # Playstyle description

model: string                     # LLM model for DM responses (e.g., "gpt-4.1-mini")
temperature: float                # LLM temperature (default: 0.7)
maxTurns: int                     # Total turns in the scenario
warmUpTurns: int                  # Non-adversarial turns before attacks begin

setting: |                        # Multi-line campaign context
  Campaign description injected into the DM's system prompt...

groundTruth:                      # Facts to evaluate against
  - id: string                    # Fact identifier (referenced in turns)
    text: string                  # The ground truth statement

seedAnchors:                      # Pre-seeded anchors at scenario start
  - text: string                  # Anchor text
    authority: string             # PROVISIONAL | UNRELIABLE | RELIABLE | CANON
    rank: int                     # Initial rank [100-900]

turns:                            # Scripted player turns
  - turn: int                     # 1-based turn number
    role: PLAYER                  # Only PLAYER turns are executed
    type: string                  # Turn type (see evaluation.md)
    strategy: string              # Attack strategy (optional, for adversarial turns)
    prompt: string                # Player message text
    targetFact: string            # Ground truth fact ID being targeted (optional)

trustConfig:                      # Optional trust evaluation settings
  profile: string                 # BALANCED | SECURE | NARRATIVE
  weightOverrides:                # Optional per-signal weight overrides
    signal_name: float

compactionConfig:                 # Optional context compaction settings
  enabled: boolean
  forceAtTurns: [int, ...]        # Turn numbers to force compaction
  tokenThreshold: int             # Compact when estimated tokens exceed this
  messageThreshold: int           # Compact when message count exceeds this

assertions:                       # Optional post-run validation
  - type: string                  # Assertion type (see below)
    params:                       # Type-specific parameters
      key: value
```

### Assertion Types

| Type                     | Params                      | Validates |
|--------------------------|-----------------------------|----------|
| `anchor-count`           | `min`, `max`                | Final anchor count is within range |
| `rank-distribution`      | `minAbove`, `rankThreshold` | At least N anchors have rank above threshold |
| `trust-score-range`      | `min`, `max`                | All trust scores fall within range |
| `promotion-zone`         | `zone`, `minCount`          | At least N anchors are in the specified zone |
| `authority-at-most`      | `maxAuthority`              | No anchor exceeds the specified authority level |
| `kg-context-contains`    | `patterns`                  | Final anchor texts contain all specified patterns |
| `kg-context-empty`       | (none)                      | No active anchors remain |
| `no-canon-auto-assigned` | (none)                      | No anchor has CANON authority |
| `compaction-integrity`   | `requiredFacts`             | All required facts survive compaction |

### Per-Turn Execution Sequence

`SimulationTurnExecutor` runs each turn:

1. Query active anchors via `AnchorEngine.inject(contextId)`
2. Format anchors as `ESTABLISHED FACTS` block (if injection enabled)
3. Build system prompt — DM persona, campaign setting, anchor block, contradiction resistance instructions
4. Build user prompt — recent conversation history + current player message
5. Call LLM via Spring AI `ChatModel`
6. Build `ContextTrace` (token counts, injected anchors, full prompts)
7. If turn type requires evaluation — send to evaluator LLM, parse JSON verdicts with severity
8. Diff anchor state vs previous turn — detect CREATED, REINFORCED, DECAYED, AUTHORITY_CHANGED, EVICTED, ARCHIVED events
9. Check compaction thresholds; compact if needed (`CompactionValidator` checks protected fact survival)
10. Return `TurnExecutionResult`

### Run History Persistence

Set `dice-anchors.run-history.store` in `application.yml`:

| Value              | Storage                       | Lifecycle |
|--------------------|-------------------------------|-----------|
| `memory` (default) | ConcurrentHashMap             | Lost on restart |
| `neo4j`            | Neo4j nodes with JSON payload | Persistent across restarts |

## Running Tests

```bash
./mvnw test
```

Tests span packages: `anchor/`, `assembly/`, `chat/`, `extract/`, `sim/`.

- **Unit tests**: JUnit 5 + Mockito + AssertJ
- **Integration tests** (`*IT.java`, `@Tag("integration")`): excluded by default via Surefire configuration
- Test structure uses `@Nested` + `@DisplayName`
- Method naming: `actionConditionExpectedOutcome`

## Neo4j Access

| Property    | Value |
|-------------|-------|
| Browser URL | `http://localhost:7474` |
| Username    | `neo4j` |
| Password    | `diceanchors123` |
| Bolt port   | `7687` |

Started via `docker-compose.yml`. Both chat and simulation use the same `AnchorRepository` (Drivine-backed, scoped by `contextId`).

## Observability

Optional Langfuse stack for OTEL-based tracing.

```bash
docker compose -f docker-compose.langfuse.yml up -d
```

| Property      | Value |
|---------------|-------|
| UI URL        | `http://localhost:3000` |
| Login         | `dev@diceanchors.dev` / `Welcome1!` |
| OTEL endpoint | `http://localhost:3000/api/public/otel` |

## Troubleshooting

| Issue                          | Resolution |
|--------------------------------|------------|
| Neo4j connection refused       | Verify `docker-compose up -d` completed; check `docker ps` for healthy container |
| `OPENAI_API_KEY` errors        | Ensure the environment variable is set before `spring-boot:run` |
| Port 8089 in use               | Stop conflicting process or change `server.port` in `application.yml` |
| Test failures on clean clone   | Run `./mvnw clean compile` first; integration tests (`*IT.java`) require a running Neo4j instance |
| Langfuse not starting          | Ensure port 3000 is free; the Langfuse stack is independent of the main `docker-compose.yml` |
| Stale simulation data          | Each run uses an isolated `contextId`; stale data from previous runs does not affect new runs |
| Build failures on Java version | Java 25 is required; verify with `java -version` |

## Technology Stack

| Component     | Version |
|---------------|---------|
| Java          | 25 |
| Spring Boot   | 3.5.10 |
| Embabel Agent | 0.3.5-SNAPSHOT |
| DICE          | 0.1.0-SNAPSHOT |
| Vaadin        | 24.6.4 |
| Neo4j         | 5.x (Drivine ORM) |
| Jinja2        | All prompts use Jinja2 templates |
| JUnit 5       | Testing framework |

## Development

### Coding Style

Key conventions (see `.dice-anchors/coding-style.md` for full reference):

- **Constructor injection only** — never `@Autowired` on fields
- **Records** for all immutable data; sealed interfaces for fixed type hierarchies
- **Modern Java 25** — switch expressions, pattern matching, `.toList()`, text blocks
- **Immutable collections** — `List.of()`, `Set.of()`, `Map.of()`
- **Minimal comments** — code is self-documenting; comment only non-obvious logic

### Adding Features (OpenSpec)

dice-anchors uses [OpenSpec](https://github.com/Fission-AI/OpenSpec) for structured feature work:

1. `/opsx:new` — propose a feature, write a spec, design the solution
2. `/opsx:continue` — elaborate the spec, create tasks
3. `/opsx:apply` — work through implementation tasks
4. `/opsx:verify` — confirm implementation matches spec
5. `/opsx:archive` — finalize and document the change

## Ongoing Research

### Collaborative Anchor Mutation

The [collaborative-anchor-mutation roadmap](openspec/roadmaps/collaborative-anchor-mutation-roadmap.md) tackles legitimate revision of established anchors in multi-actor contexts. Five feature waves:

1. **Revision intent classification** — `ConflictType` enum distinguishing REVISION from CONTRADICTION
2. **Prompt compliance revision carveout** — authority-gated revision eligibility in prompt templates
3. **Dependent anchor cascade** — invalidation of dependent anchors during supersession
4. **Anchor provenance metadata** — extraction turn, speaker role tracking
5. **UI-controlled mutation** — explicit anchor editing via the chat sidebar

Six research tasks (R00–R05) are complete. Key finding: none of the AI memory frameworks we surveyed distinguish update from contradiction — to our knowledge, revision-vs-contradiction classification hasn't been done elsewhere in this space.

### Current Direction: AGM Belief Revision

Research (R05) identified AGM belief revision theory as the best-fitting theoretical foundation we found:
- **Contraction** maps to anchor archival
- **Revision** maps to supersession (archive + create successor)
- **Entrenchment ordering** maps to authority tiers
- **Minimal change principle** constrains cascade scope

Cross-domain patterns from TMS (label propagation for cascade), Wikipedia ORES (two-axis intent/impact classification), and accounting materiality (authority x impact radius) are shaping the implementation direction.

### Future Work

- **Track B**: Creative retrieval — spreading activation and graph-native retrieval for serendipitous knowledge discovery
- **Track C**: A2A governance — multi-agent anchor revision protocols
- Cross-domain generalization beyond tabletop RPGs
- Memory poisoning threat model and defenses

### Known Limitations

- **Very long conversations** — the system prompt itself gets diluted and attention allocation fails even for injected content.
- **Subtle reframing attacks** — adversaries that don't directly contradict but slowly reframe facts over multiple turns. The conflict detector catches direct contradictions but misses gradual drift.
- **Weak instruction followers** — some models ignore system prompt directives regardless of formatting. Anchors can't help if the model won't read them.
- **Cross-session persistence** — anchors must survive context resets and be reconstructed from storage. This works but hasn't been stress-tested.

### Open Questions

1. **Dynamic budget and memory pressure** — Could anchors adapt budget dynamically based on conversation pressure? ACON's failure-driven compression suggests eviction policy could be driven by what causes drift, not just rank.
2. **Premature commitment and anchor timing** — Anchors established in early turns could compound premature commitment (per Laban et al.). When should anchors be promoted? Is there a minimum conversation maturity threshold?
3. **Bi-temporal validity** — Graphiti/Zep distinguishes world-time validity from system-recording time. Anchors currently have no temporal dimension. How should temporal validity interact with authority?
4. **Graph-based reinforcement** — HippoRAG uses spreading activation and personalized PageRank. Current reinforcement is count-based (+50 per mention). Could reinforcement consider knowledge graph position (centrality)?
5. **Self-editing anchors** — MemGPT allows model self-editing of memory blocks. Could the model propose modifications to anchors? Authority constraints could limit this to PROVISIONAL anchors.
6. **Operator-defined invariants** — In production, the most valuable anchors will be defined upfront by operators. This points toward an invariant definition API.
7. **Conflict detection at scale** — As the pool grows, conflicts may be indirect (two facts individually consistent but collectively contradictory). Graph-based consistency checking may be needed.

## Links

- [CLAUDE.md](CLAUDE.md) — Architecture decisions, detailed coding style, key files reference
- [Developer Docs](docs/) — Architecture, evaluation protocol, known issues, UI views, workflows, research directions
- [Embabel Agent](https://github.com/embabel/embabel-agent)
- [DICE](https://github.com/embabel/dice)
- [impromptu](https://github.com/embabel/impromptu)
- [OpenSpec](https://github.com/Fission-AI/OpenSpec)

## References

1. Laban, P. et al. (2025). *LLMs Get Lost In Multi-Turn Conversation*. [arXiv:2505.06120](https://arxiv.org/abs/2505.06120)
2. Packer, C. et al. (2023). *MemGPT: Towards LLMs as Operating Systems*. [arXiv:2310.08560](https://arxiv.org/abs/2310.08560)
3. Radhakrishnan, A. et al. (2025). *Graphiti: Building Real-Time Knowledge Graphs*. [arXiv:2501.13956](https://arxiv.org/abs/2501.13956)
4. Gutierrez, B.J. et al. (2024). *HippoRAG*. [arXiv:2405.14831](https://arxiv.org/abs/2405.14831) (NeurIPS 2024)
5. ACON Framework. *Task-Aware Compression*. [OpenReview](https://openreview.net/pdf?id=7JbSwX6bNL)
6. Johnson, R. (2026). *Agent Memory Is Not A Greenfield Problem*. [Embabel](https://medium.com/embabel/agent-memory-is-not-a-greenfield-problem-ground-it-in-your-existing-data-9272cabe1561)
7. Maharana, A. et al. (2024). *LoCoMo: Evaluating Very Long-term Conversational Memory*. [arXiv:2402.17753](https://arxiv.org/abs/2402.17753)
8. Wu, Y. et al. (2023). *Recursive Summarization*. [arXiv:2308.15022](https://arxiv.org/abs/2308.15022)
