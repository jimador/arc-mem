# ARC-Mem

ARC-Mem (Activation-Ranked Context Memory) adds a trust-governed working memory layer on top of DICE + Embabel so long-running chats don't silently drift.

![simulation](docs/images/arc-sim-attack.png)

## 1. The Problem

LLMs degrade in multi-turn conversations. [Laban et al. (2025)](https://arxiv.org/abs/2505.06120) measured across 200,000+ simulated conversations with 15 LLMs: a 39% average performance drop from single-turn to multi-turn settings, a 112% increase in unreliability (variance between best and worst runs), with degradation starting at 2+ turns regardless of information density. Models prematurely attempt solutions with incomplete information and then over-rely on those early attempts. Temperature reduction to 0.0 eliminates single-turn unreliability but 30%+ unreliability persists in multi-turn. The root cause is not context capacity but the absence of any mechanism to distinguish **load-bearing facts** from ambient context.

Standard mitigations fall short:

| Approach               | Why it fails                                                                             |
|------------------------|------------------------------------------------------------------------------------------|
| Longer context windows | More tokens does not mean better attention. Lost-in-the-middle effects persist.          |
| Summarization          | Lossy. Recursive summarization compounds errors.                                         |
| RAG                    | Retrieves relevant content but does not mandate consistency. No enforcement.             |
| Prompt engineering     | "Remember these facts" degrades as context grows. Vulnerable to confident contradiction. |

The problem is acute in agentic systems where invariants must hold across long horizons under sustained context pressure. A dead NPC must stay dead. A drug allergy must never be forgotten. A legal privilege must never be waived.

### Why D&D as a Test Domain

This project uses tabletop RPGs as its proving ground, but the approach is domain-agnostic. D&D is a particularly useful sandbox because:

- **Invariants are legible.** World facts, character states, and rules form a clear set of constraints. A dead NPC stays dead (barring necromancy shenanigans). Violations are immediately obvious.
- **Creative freedom is the point.** The value of the LLM is improvisation, elaboration, and responsive storytelling. Restricting this defeats the purpose.
- **Adversarial pressure is a useful stress harness.** Players routinely test boundaries, make false claims, and try to manipulate the narrative — giving us a deliberate way to force contradiction/hallucination cases and test whether relevant memory units hold.
- **Long horizons are the norm.** Campaigns span dozens of sessions. Facts established early must persist across hundreds of turns.

This tension — *be creative, but don't break the rules* — exists wherever agentic systems operate within constraints.

## What is ARC-Mem?

LLMs are great at continuation, not persistence. In long-running conversations, facts that *should* stay stable slowly degrade as new turns reweight attention. That's fine for creativity; it's a problem when you're trying to preserve rules, world state, decisions, or any other "we already settled this" constraints.

**Memory units** are promoted [DICE](https://github.com/embabel/dice) propositions with extra control fields:

- **Activation score** — importance under memory pressure
- **Authority** — trust level (PROVISIONAL ↔ UNRELIABLE ↔ RELIABLE ↔ CANON); bidirectional with invariant guards
- **Authority ceiling** — the highest level this fact can auto-reach based on provenance
- **Budget membership** — explicit inclusion in a bounded working set (default: max 20 active memory units)

At runtime, memory units are injected into every prompt as a ranked reference block. That makes memory an explicit, governed mechanism instead of an accidental side effect:

- conflict checks gate contradictory updates
- trust scoring gates promotion and authority movement
- budget enforcement evicts low-value memory units first
- reinforcement/decay updates importance over time

Knowledge graphs answer "what facts exist." ARC-Mem answers "what must stay salient and trusted *right now*." That distinction is the whole point.

### Design Decisions

**Explicit state over implicit memory.** Facts are managed state with activation score, authority, and provenance rather than raw conversation history the model must parse.

**Authority tiers as governance primitive.** A four-level hierarchy creates policy hooks you don't get with flat retrieval stacks. Bidirectional transitions with invariant guards prevent unsupported downgrade and unauthorized escalation.

**Hard budget enforcement.** A cap (default 20 active memory units) prevents context bloat. Long-context capability doesn't fix attention-allocation failures. In our testing, a small focused fact set outperforms a large dump.

**Contradiction/hallucination control by design.** Memory units are formatted as authoritative instructions with explicit correction directives. The model is told to preserve relevant established facts under long-horizon pressure, not just recall facts opportunistically.

**Mandatory injection over retrieval.** RAG content competes for attention. Summarization can drop things. Memory units occupy a fixed system-prompt block injected before the user message — they're always there.

## Why DICE + Embabel?

This repo is an extension layer, not a replacement stack:

- **DICE** handles proposition extraction, grounding, and graph persistence.
- **Embabel** handles agent orchestration plus prompt/action lifecycle integration.
- **ARC-Mem** adds trust/authority governance and bounded working-memory control (where base proposition systems are intentionally neutral).

Practically: DICE/Embabel handle acquisition and flow; ARC-Mem handles memory prioritization, trust-constrained mutation, and long-horizon relevance stability.

## Architecture Overview

### Core Packages

| Package        | Purpose |
|----------------|---------|
| `arcmem/`      | Memory unit lifecycle: `ArcMemEngine` (budget + promotion), conflict detection/resolution, trust pipeline, reinforcement/decay policies, memory tiers, invariant evaluation |
| `assembly/`    | Prompt assembly: `ArcMemLlmReference` (inject memory units into prompts), `PromptBudgetEnforcer` (token limits), `CompactedContextProvider` (context summarization), `ArcMemContextLock` (multi-request safety) |
| `chat/`        | Chat flow: `ChatView` (Vaadin UI), `ChatActions` (Embabel Agent `@EmbabelComponent`), memory unit tools |
| `extract/`     | DICE integration: `DuplicateDetector` (near-dupes), `SemanticUnitPromoter` (promotion) |
| `persistence/` | Neo4j repository layer: `MemoryUnitRepository`, `PropositionNode` (Drivine ORM) |
| `prompt/`      | Prompt templates: `PromptTemplates`, `PromptPathConstants`, Jinja2 template files |
| `sim/`         | Simulation harness: engine (orchestration, LLM calls, scoring, adversary strategies), views (`SimulationView`, `RunInspectorView`, `BenchmarkView`, `ContextInspectorPanel`), benchmark (`BenchmarkRunner`, ablation conditions), report (`ResilienceReport`, `MarkdownReportRenderer`), assertions (post-run validation) |

### Chat Flow

```text
ChatView (user types)
  → ChatActions (Embabel @EmbabelComponent)
  → ArcMemLlmReference (inject memory units into system prompt)
  → LLM (gpt-4.1-mini) + PromptBudgetEnforcer (token limits)
  → Extract propositions (DICE extraction)
  → SemanticUnitPromoter (promote promising propositions to memory units)
  → ArcMemEngine (reinforce existing memory units, manage budget)
  → Neo4j persistence (Drivine ORM)
```

### Simulation Flow

```text
SimulationView (user selects scenario and hits "Run")
  → SimulationService (load scenario YAML, seed memory units, phase state machine)
  → SimulationTurnExecutor (per turn):
     - Assemble memory unit + system/user prompts
     - Call LLM (DM response)
     - If ATTACK turn: evaluate drift against ground truth (concurrent)
     - Extract propositions from DM response (concurrent)
     - Promote extracted propositions (concurrent)
  → Adversary strategy (adaptive or scripted from scenario YAML)
  → LLM again (generate adversarial player message)
  → Repeat until scenario ends
  → SimulationView updates with final memory unit state, drift verdicts, and rankings
```

## Key Features

### Memory Unit Lifecycle

A memory unit is a fact promoted to special status in the LLM's context:

- **Activation score** `[100–900]` — higher activation score = more influence under budget pressure
- **Authority** — PROVISIONAL → UNRELIABLE → RELIABLE → CANON (bidirectional — promoted via reinforcement, demoted via decay/trust re-evaluation; CANON is never auto-assigned and immune to auto-demotion)
- **Reinforcement tracking** — reuse boosts activation score and may upgrade authority
- **Decay** — unused memory units decay exponentially; eventually auto-archived at `MIN_RANK`
- **Budget enforcement** — max 20 active memory units; evicts lowest-ranked non-pinned when exceeded

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
- **Adversary modes** — scripted (explicit player messages in YAML) or adaptive (strategy selection based on memory unit state and attack history)

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
                           Active Memory Unit Pool
                     (reinforcement, decay, pin controls)
                                    │
                            Context Assembly
                    (top-ranked → ESTABLISHED FACTS block)
```

## ARC-Mem API Reference

### The Memory Unit Record

| Field | Type | Range/Default | Purpose |
|-------|------|---------------|---------|
| `id` | String | UUID | Unique identifier |
| `text` | String | — | Natural language statement |
| `rank` | int | [100, 900] | Activation score: importance under memory pressure; clamped via `clampRank()` |
| `authority` | Authority | PROVISIONAL..CANON | Trust level governing conflict resolution and eviction |
| `pinned` | boolean | false | Immune to eviction, decay, and auto-demotion |
| `confidence` | double | [0.0, 1.0] | DICE extraction confidence |
| `reinforcementCount` | int | 0+ | Times re-confirmed; drives authority upgrades |
| `trustScore` | TrustScore | nullable | Composite trust evaluation |
| `diceImportance` | double | [0.0, 1.0], default 0.0 | DICE-assigned importance; > 0.7 boosts activation score |
| `diceDecay` | double | >= 0.0, default 1.0 | DICE-assigned decay modifier; adjusts effective half-life |
| `memoryTier` | MemoryTier | COLD/WARM/HOT | Tier classification influencing decay rate |

### Activation Score

Range `[100, 900]`, clamped by `MemoryUnit.clampRank()`. Initial activation score: 500 (configurable). Reinforcement: +50 per confirmation. Decay: exponential with tier-modulated half-life.

Activation-score-triggered authority demotion:
- RELIABLE demotes to UNRELIABLE when activation score drops below 400
- UNRELIABLE demotes to PROVISIONAL when activation score drops below 200
- CANON and pinned memory units are immune to activation-score-triggered demotion

### Authority

```
PROVISIONAL(0) < UNRELIABLE(1) < RELIABLE(2) < CANON(3)
```

Authority is **bidirectional** with the following invariants:

| ID | Invariant |
|----|-----------|
| A3a | CANON is never auto-assigned; requires explicit action through `CanonizationGate` |
| A3b | CANON is immune to automatic demotion (decay, trust re-evaluation); only explicit action can demote |
| A3c | Automatic demotion applies to RELIABLE → UNRELIABLE → PROVISIONAL via activation score decay or trust degradation |
| A3d | Pinned memory units are immune to automatic demotion |
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

| Tier | Activation Score Range | Decay Multiplier | Semantics |
|------|----------------------|-------------------|-----------|
| HOT | >= 600 | 1.5x (slower decay) | Protected window |
| WARM | 350–599 | 1.0x (baseline) | Normal decay |
| COLD | < 350 | 0.6x (faster decay) | Accelerated cleanup |

Tier thresholds and multipliers are configurable via `arc-mem.tier.*`.

### Conflict Detection and Resolution

`CompositeConflictDetector` chains detection strategies (configurable via `arc-mem.conflict-detection.strategy`):

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

Resolution outcomes: `KEEP_EXISTING`, `REPLACE`, `DEMOTE_EXISTING`, `COEXIST`. Memory tier defense modifiers adjust resolution thresholds (HOT memory units get +0.1 defense, COLD get -0.1).

### Promotion Pipeline

`SemanticUnitPromoter` runs a 5-gate pipeline: **confidence → dedup → conflict → trust → promote**.

1. **Confidence gate** — drops propositions below `autoActivateThreshold` (default 0.65)
2. **Dedup gate** — `DuplicateDetector` removes near-duplicates of existing memory units
3. **Conflict gate** — `ConflictDetector` checks for contradictions/revisions with existing memory units; `ConflictResolver` determines outcome
4. **Trust gate** — `TrustPipeline` evaluates composite trust score; routes to AUTO_PROMOTE, REVIEW, or ARCHIVE
5. **Promote** — `ArcMemEngine.promote()` writes the memory unit and runs budget enforcement

### Canonization Gate

`CanonizationGate` enforces human-in-the-loop control over CANON authority:
- Canonization and decanonization requests queue as `PENDING` in Neo4j
- Auto-approved in simulation contexts (`contextId` starting with `sim-`)
- In chat contexts, requires explicit `approve()` or `reject()` call
- Stale detection — if the memory unit's authority changed since the request was created, the request is marked `STALE`

## DICE Integration

ARC-Mem is a downstream consumer of DICE, not a replacement. Everything here is implemented locally in `arc-mem`.

### How DICE Concepts Map to ARC-Mem

| DICE Concept            | ARC-Mem Concept                | Relationship                                                                                              |
|-------------------------|--------------------------------|-----------------------------------------------------------------------------------------------------------|
| Proposition extraction  | Raw material                   | DICE extracts propositions from conversation text; ARC-Mem selects a subset for promotion                 |
| Proposition revision    | Conflict/reinforcement trigger | DICE revisions feed ARC-Mem conflict detection and reinforcement pipelines                                |
| Entity mentions         | Subject filtering              | DICE entities used by `SubjectFilter` to scope memory unit queries                                       |
| Proposition persistence | Shared store                   | Both use `MemoryUnitRepository` (Neo4j/Drivine). ARC-Mem adds activation score/authority/tier metadata    |
| Incremental analysis    | Turn-by-turn extraction        | DICE `ConversationPropositionExtraction` runs per turn; ARC-Mem processes results through promotion gates |

DICE owns extraction and revision. ARC-Mem owns promotion, lifecycle governance, budget enforcement, and context injection.

### Memory Layering

DICE Agent Memory and ARC-Mem serve different purposes and run as separate layers:

| Layer | Mechanism | Purpose |
|-------|-----------|---------|
| **DICE Agent Memory** | `searchByTopic`, `searchRecent`, `searchByType` | Broad retrieval of relevant propositions, entity relationships, and historical context on demand |
| **ARC-Mem working set** | Activation-score-sorted mandatory injection into system prompt | Guaranteed presence of load-bearing facts regardless of retrieval relevance scoring |

ARC-Mem augments DICE memory — doesn't replace it. DICE retrieval provides the broader knowledge base. ARC-Mem provides the invariant enforcement layer. A proposition can exist in DICE memory and never become a memory unit. A memory unit always has a corresponding DICE proposition as its origin.

### Low-Trust Knowledge

Not all extracted knowledge deserves memory unit status. The authority hierarchy keeps low-trust knowledge available without contaminating the invariant set:

- **PROVISIONAL** memory units carry provenance qualifiers in the injected context (tagged `[PROVISIONAL]`), signaling the model to treat them as tentative.
- Propositions below the AUTO_PROMOTE threshold (trust score < 0.80) enter the REVIEW queue or are archived, remaining in DICE's proposition store for retrieval without occupying memory budget.
- Authority ceiling (persisted at promotion) constrains how high a low-provenance fact can be upgraded — designed to prevent adversarial escalation through repetition alone.
- The `TrustSignal` composite evaluates source authority, extraction confidence, and reinforcement history to gate promotion decisions.

### Runtime Boundaries

**What remains in DICE:**
- Proposition extraction from conversational text
- Entity mention and relationship identification
- Proposition revision and incremental analysis
- Base persistence schema and query contracts

**What this repo adds:**
- Activation score, authority, and trust metadata on propositions
- Promotion pipeline with duplicate/conflict/trust gates (`SemanticUnitPromoter`, `DuplicateDetector`, `LlmConflictDetector`)
- Budget enforcement and eviction policy (`ArcMemEngine`, `PromptBudgetEnforcer`)
- Mandatory context injection (`ArcMemLlmReference`, `ArcMemContextLock`)
- Decay and reinforcement policies (`ExponentialDecayPolicy`, `ThresholdReinforcementPolicy`)
- Adversarial simulation and drift evaluation harness (`sim/engine/`, `sim/report/`)

### Where Integration Is Rough

- **No DICE lifecycle hooks** — promotion and tiering run after extraction completes. There's no way to hook into the extraction pipeline itself.
- **No temporal validity** — DICE propositions have no `validFrom`/`validTo`. Representing "this was true then but not now" takes app-level workarounds.
- **Separate audit schemas** — memory unit lifecycle events and DICE extraction events use different structures, so unified tracing takes extra plumbing.
- **Text-keyed maps** — the trust pipeline and promotion paths key by proposition text, not a stable DICE proposition ID. Text normalization edge cases could cause collisions.
- **Parse failure quarantine** — duplicate/conflict parse failures quarantine instead of auto-accepting, but what operators actually do with quarantined items is still undefined.
- **Upstream divergence** — if DICE adds its own memory tiering, our patterns may need to adapt. Adapter boundaries help, but it's a real risk.
- **Authority is domain policy** — the `PROVISIONAL`/`UNRELIABLE`/`RELIABLE`/`CANON` taxonomy belongs to this application, not to DICE.
- **Temporal state machines** — adding `validFrom`/`validTo` to propositions means real state machine complexity that DICE may not want in its core.

### Integration Summary

| Dimension       | Status                                                                                                                                                                                                    |
|-----------------|-----------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **Intent**      | Demonstrate working-memory memory units as a composable layer over DICE extraction                                                                                                                      |
| **Current fit** | DICE provides extraction; ARC-Mem consumes output and adds lifecycle governance. Integration is functional but loosely coupled (no shared hooks)                                                          |
| **Known gaps**  | No DICE lifecycle hooks, no temporal primitives, text-keyed maps, fail-open parse paths                                                                                                                   |
| **Next steps**  | (1) add temporal metadata to persistence model, (2) publish deterministic ablation manifests                                                                                                              |

## How This Compares

| Feature                    | ARC-Mem                                     | MemGPT                  | Graphiti/Zep              | HippoRAG             | ACON            |
|----------------------------|---------------------------------------------|-------------------------|---------------------------|----------------------|-----------------|
| Guaranteed prompt presence | Yes (mandatory injection)                   | Partial (memory blocks) | No (retrieved)            | No (retrieved)       | No (compressed) |
| Importance ranking         | Yes [100-900]                               | No                      | No                        | PageRank-based       | Task-aware      |
| Authority hierarchy        | Yes (4 levels, bidirectional with guards)    | No                      | No                        | No                   | No              |
| Budget enforcement         | Hard cap (20)                               | Token limit             | None                      | Top-k                | Token reduction |
| Conflict detection         | LLM-based semantic + lexical fallback       | No                      | Temporal                  | No                   | No              |
| Stress-tested consistency control | Primary design goal                  | Not a focus             | Not a focus               | Not a focus          | Not a focus     |
| Temporal validity          | Not yet (planned)                           | No                      | Yes (bi-temporal)         | No                   | No              |
| Decay/reinforcement        | Exponential decay + threshold reinforcement | Self-edit               | Temporal validity windows | Spreading activation | Failure-driven  |
| Graph-native retrieval     | Neo4j store, no graph retrieval yet         | No                      | Yes                       | Yes                  | No              |

**What's different here:** Long-horizon attention stability and hallucination/contradiction control are primary design goals, not afterthoughts. Adversarial scenarios are used as stress tests to evaluate those goals. The closest comparison is MemGPT's fixed memory blocks — ARC-Mem adds explicit ranking, authority governance, and lifecycle management on top. Graphiti/Zep's temporal model looks like the most natural complement and a planned integration direction.

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
cd arc-mem

# Start Neo4j
docker-compose up -d

# Build (skip tests for faster iteration)
./mvnw clean compile -DskipTests

# Run the application (from arcmem-simulator module)
OPENAI_API_KEY=sk-... ./mvnw spring-boot:run
```

### Application Routes

| Route                             | View             | Purpose |
|-----------------------------------|------------------|---------|
| `http://localhost:8089`           | SimulationView   | Adversarial simulation harness |
| `http://localhost:8089/chat`      | ChatView         | Interactive memory demo |
| `http://localhost:8089/benchmark` | BenchmarkView    | Multi-condition ablation benchmarks |
| `http://localhost:8089/run`       | RunInspectorView | Detailed run inspection |

## Configuration Reference

All properties are under the `arc-mem` prefix in `application.yml`. Defaults are listed; override via YAML or environment variables (e.g., `ARC_MEM_UNIT_BUDGET=30`).

### `arc-mem.unit` — Memory Unit Lifecycle

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `budget` | int | `20` | Maximum active memory units per context. Lowest-ranked non-pinned units are evicted when exceeded. |
| `initial-rank` | int | `500` | Activation score assigned on promotion. Must be within `[min-rank, max-rank]`. |
| `min-rank` | int | `100` | Activation score floor. Clamped by `MemoryUnit.clampRank()`. |
| `max-rank` | int | `900` | Activation score ceiling. Must be > `min-rank`. |
| `auto-activate` | boolean | `true` | Whether extracted propositions are automatically evaluated for promotion. |
| `auto-activate-threshold` | double | `0.65` | Minimum extraction confidence `[0.0, 1.0]` to enter the promotion pipeline. |
| `dedup-strategy` | enum | `FAST_THEN_LLM` | Duplicate detection strategy. `FAST_THEN_LLM` runs normalized-string matching first, then LLM verification. `LLM_ONLY` skips the fast pass. |
| `compliance-policy` | enum | `TIERED` | Compliance enforcement mode. `TIERED` varies strictness by authority level. `UNIFORM` applies the same policy to all. |
| `lifecycle-events-enabled` | boolean | `true` | Publish `ContextUnitLifecycleEvent` on promote/archive/demote/reinforce. |
| `canonization-gate-enabled` | boolean | `true` | Require explicit approval for CANON promotion via `CanonizationGate`. |
| `auto-approve-promotions` | boolean | `true` | Auto-approve promotions to CANON (when gate is enabled). |
| `demote-threshold` | double | `0.6` | Trust score `[0.0, 1.0]` below which a re-evaluated unit is demoted one authority level. |
| `reliable-rank-threshold` | int | `400` | Activation score above which a unit qualifies for RELIABLE authority. |
| `unreliable-rank-threshold` | int | `200` | Activation score above which a unit qualifies for UNRELIABLE authority. |

#### `arc-mem.unit.tier` — Memory Tier Boundaries

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `hot-threshold` | int | `600` | Activation score >= this → HOT tier. Must be > `warm-threshold`. |
| `warm-threshold` | int | `350` | Activation score >= this (and < `hot-threshold`) → WARM tier. Below → COLD. |
| `hot-decay-multiplier` | double | `1.5` | Decay rate multiplier for HOT units. Higher = faster decay. |
| `warm-decay-multiplier` | double | `1.0` | Decay rate multiplier for WARM units. |
| `cold-decay-multiplier` | double | `0.6` | Decay rate multiplier for COLD units. Lower = slower decay. |

#### `arc-mem.unit.revision` — Revision Detection

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable revision-aware conflict resolution (updates vs. contradictions). |
| `reliable-revisable` | boolean | `false` | Whether RELIABLE-authority units can be revised (not just PROVISIONAL). |
| `confidence-threshold` | double | `0.75` | Minimum confidence `(0.0, 1.0]` for a revision to be accepted. |

#### `arc-mem.unit.quality-scoring`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable LLM-based proposition quality scoring during extraction. |

#### `arc-mem.unit.invariants` — Invariant Rules

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable invariant evaluation on promotion candidates. |
| `rules` | list | `[]` | List of invariant rule definitions. Each has `id`, `type`, `strength`, and optional `context-id`, `unit-text-pattern`, `minimum-authority`, `minimum-count`. |

#### `arc-mem.unit.chat-seed` — Seed Units for Chat

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Pre-populate chat contexts with seed memory units. |
| `units` | list | `[]` | Seed unit definitions. Each has `text`, `authority` (default `RELIABLE`), `rank` (default `500`), `pinned` (default `false`). |

### `arc-mem.assembly` — Prompt Assembly

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `prompt-token-budget` | int | `0` | Maximum tokens for memory unit injection block. `0` = unlimited. |
| `adaptive-footprint-enabled` | boolean | `false` | Authority-graduated templates: PROVISIONAL gets full text, CANON gets minimal reference. |
| `enforcement-strategy` | enum | `PROMPT_ONLY` | How compliance is enforced. `PROMPT_ONLY` (prompt instructions), `POST_GENERATION` (LLM validation), `HYBRID` (both), `PROLOG` (deterministic invariant checking). |

### `arc-mem.chat` — Chat Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `persona` | string | `assistant` | System persona for the chat agent. |
| `max-words` | int | `200` | Maximum words per chat response. |
| `chat-llm.model` | string | — | LLM model for chat responses. |
| `chat-llm.temperature` | double | — | Temperature for chat responses. |

### `arc-mem.memory` — DICE Extraction

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable DICE proposition extraction from conversations. |
| `extraction-llm.model` | string | — | LLM model for proposition extraction. |
| `extraction-llm.temperature` | double | — | Temperature for extraction (typically `0.0` for determinism). |
| `entity-resolution-llm.model` | string | — | LLM model for entity resolution. |
| `entity-resolution-llm.temperature` | double | — | Temperature for entity resolution. |
| `embedding-service-name` | string | `text-embedding-3-small` | Embedding model for semantic similarity. |
| `window-size` | int | `20` | Number of messages in the incremental analysis window. |
| `window-overlap` | int | `5` | Overlap between consecutive analysis windows. |
| `trigger-interval` | int | `6` | Number of new messages before triggering extraction. |

### `arc-mem.persistence`

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `clear-on-start` | boolean | `false` | Wipe all propositions and memory units on application startup. Useful for development. |

### `arc-mem.sim` — Simulation Engine

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `evaluator-model` | string | `gpt-4.1-mini` | LLM model for drift evaluation and scoring. |
| `adversary-budget` | int | `30` | Maximum tokens allocated to the adversary prompt generator. |
| `llm-call-timeout-seconds` | int | `30` | Timeout for individual LLM calls during simulation. |
| `batch-max-size` | int | `10` | Maximum candidates per batch promotion/dedup/conflict call. |
| `parallel-post-response` | boolean | `true` | Run extraction and scoring in parallel after each DM response. |
| `benchmark-parallelism` | int | `4` | Number of concurrent simulation runs during benchmarking. |

### `arc-mem.conflict-detection` — Conflict Detection Strategy

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `strategy` | enum | `LLM` | Detection strategy. `LLM` (semantic), `NEGATION` (lexical), `COMPOSITE` (both), `LOGICAL` (Prolog). |
| `model` | string | `gpt-4o-nano` | LLM model for semantic conflict detection. |

### `arc-mem.conflict` — Conflict Resolution Thresholds

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `negation-overlap-threshold` | double | `0.5` | Minimum token overlap `(0.0, 1.0]` for negation-based conflict detection. |
| `llm-confidence` | double | `0.9` | Minimum LLM confidence `(0.0, 1.0]` to accept a conflict as genuine. |
| `replace-threshold` | double | `0.8` | Confidence above which the incoming unit replaces the existing one. Must be > `demote-threshold`. |
| `demote-threshold` | double | `0.6` | Confidence above which the existing unit is demoted (below `replace-threshold`). |

#### `arc-mem.conflict.tier` — Tier Defense Modifiers

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `hot-defense-modifier` | double | `0.1` | Confidence bonus `[-0.5, 0.5]` for HOT-tier units during conflict resolution. Positive = harder to replace. |
| `warm-defense-modifier` | double | `0.0` | Confidence modifier for WARM-tier units. |
| `cold-defense-modifier` | double | `-0.1` | Confidence modifier for COLD-tier units. Negative = easier to replace. |

### `arc-mem.retrieval` — Context Retrieval

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `mode` | enum | `HYBRID` | Retrieval mode. `RANK_ONLY` (activation score), `SEMANTIC` (embedding similarity), `HYBRID` (weighted blend). |
| `min-relevance` | double | `0.0` | Minimum relevance score `[0.0, 1.0]` to include a unit in context. |
| `baseline-top-k` | int | `5` | Maximum units retrieved for baseline prompt injection. |
| `tool-top-k` | int | `5` | Maximum units returned by query tools. |

#### `arc-mem.retrieval.scoring` — Relevance Scoring Weights

Weights must sum to `1.0`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `authority-weight` | double | `0.4` | Weight of authority level in relevance scoring. |
| `tier-weight` | double | `0.3` | Weight of memory tier (HOT/WARM/COLD) in relevance scoring. |
| `confidence-weight` | double | `0.3` | Weight of extraction confidence in relevance scoring. |

### `arc-mem.attention` — Attention Tracking

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable attention signal tracking. |
| `window-duration` | Duration | `PT5M` | Sliding window duration for attention event aggregation. |
| `pressure-threshold` | double | `0.5` | Attention pressure score `[0.0, 1.0]` that triggers alerts. |
| `min-conflicts-for-pressure` | int | `3` | Minimum conflict events in a window before pressure is flagged. |
| `heat-peak-threshold` | double | `0.7` | Attention heat score `[0.0, 1.0]` considered a peak. |
| `heat-drop-threshold` | double | `0.2` | Heat score drop that signals attention loss. |
| `cluster-drift-min-units` | int | `3` | Minimum units in a cluster before drift detection applies. |
| `max-expected-events-per-window` | int | `20` | Normalization ceiling for event counts within a window. |

### `arc-mem.maintenance` — Maintenance Strategy

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `mode` | enum | `REACTIVE` | Strategy mode. `REACTIVE` (per-turn decay/reinforcement), `PROACTIVE` (pressure-triggered sweeps), `HYBRID` (both). |

#### `arc-mem.maintenance.proactive` — Proactive Sweep Configuration

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `min-turns-between-sweeps` | int | `10` | Minimum turns between proactive maintenance sweeps. |
| `hard-prune-threshold` | double | `0.1` | Audit score below which a unit is archived outright. Must be < `soft-prune-threshold`. |
| `soft-prune-threshold` | double | `0.3` | Audit score below which a unit receives a rank penalty (when pressure is above `soft-prune-pressure-threshold`). |
| `soft-prune-pressure-threshold` | double | `0.6` | Memory pressure level `[0.0, 1.0]` that activates soft pruning. |
| `candidacy-min-reinforcements` | int | `10` | Minimum reinforcement count for CANON candidacy consideration. |
| `candidacy-min-audit-score` | double | `0.8` | Minimum audit score for CANON candidacy. |
| `candidacy-min-age` | int | `5` | Minimum turns since promotion for CANON candidacy. |
| `rank-boost-amount` | int | `50` | Activation score boost for units that pass the audit. `[1, 200]`. |
| `rank-penalty-amount` | int | `50` | Activation score penalty for units that fail the audit. `[1, 200]`. |
| `llm-audit-enabled` | boolean | `false` | Use LLM for audit relevance scoring (expensive). |
| `prolog-pre-filter-enabled` | boolean | `false` | Run Prolog pre-filter to identify logically inconsistent units before LLM audit. |

### `arc-mem.pressure` — Memory Pressure Gauge

Composite `[0.0, 1.0]` score across four dimensions. Weights must sum to `1.0`.

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `true` | Enable memory pressure computation. |
| `budget-weight` | double | `0.4` | Weight of budget utilization in composite pressure score. |
| `conflict-weight` | double | `0.3` | Weight of recent conflict rate. |
| `decay-weight` | double | `0.2` | Weight of recent decay-driven demotions. |
| `compaction-weight` | double | `0.1` | Weight of compaction frequency. |
| `light-sweep-threshold` | double | `0.4` | Pressure score triggering a light maintenance sweep. |
| `full-sweep-threshold` | double | `0.8` | Pressure score triggering a full 5-step sweep. Must be > `light-sweep-threshold`. |
| `budget-exponent` | double | `1.5` | Exponent for budget utilization curve. Higher = sharper pressure ramp near capacity. |
| `conflict-window-size` | int | `5` | Number of recent turns used to compute conflict rate. |

### `arc-mem.budget` — Budget Enforcement Strategy

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `strategy` | enum | `COUNT` | Eviction strategy. `COUNT` (simple max-units cap), `INTERFERENCE_DENSITY` (cluster-aware, inspired by sleeping-LLM phase transition at 13-14 related facts). |
| `density-warning-threshold` | double | `0.6` | Interference density `[0.0, 1.0]` that triggers a warning log. Only used with `INTERFERENCE_DENSITY`. |
| `density-reduction-threshold` | double | `0.8` | Density above which eviction kicks in. Only used with `INTERFERENCE_DENSITY`. |
| `density-reduction-factor` | double | `0.5` | Fraction of cluster to evict when above threshold. Only used with `INTERFERENCE_DENSITY`. |

### `arc-mem.tiered-storage` — Tiered Cache

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `enabled` | boolean | `false` | Enable three-tier storage (HOT from Caffeine cache, WARM from Neo4j, COLD excluded from assembly). |
| `max-cache-size` | int | `1000` | Maximum entries in the HOT-tier Caffeine cache. |
| `ttl-minutes` | int | `60` | Cache entry TTL in minutes. |

### `arc-mem.run-history` — Run History Storage

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `store` | enum | `MEMORY` | Where simulation run history is stored. `MEMORY` (in-process, lost on restart) or `NEO4J` (persisted). |

## Running Simulations

Go to `http://localhost:8089` (routes to `SimulationView`).

**UI Controls:**

- **Scenario selector** — dropdown of all loaded scenarios from `src/main/resources/simulations/`
- **Injection toggle** — enable/disable memory unit injection mid-run
- **Run / Pause / Resume / Cancel** — turn-boundary execution controls
- **Conversation panel** — turn-by-turn messages with verdict badges
- **Context Inspector** — 4-tab view: memory unit state, system prompt, context trace, compaction
- **Memory Unit Timeline** — visual lifecycle event log
- **Drift Summary** — aggregate metrics (survival rate, contradiction counts, strategy effectiveness)
- **Run History** — cross-run comparison
- **Manipulation Panel** — modify memory unit activation scores during paused simulation

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
| `seedUnits`                                       | Pre-seeded memory units (text, authority, activation score) |
| `turns`                                           | Scripted player turns with type, strategy, prompt, targetFact |
| `assertions`                                      | Post-run validation (unit-count, activation-score-distribution, kg-context-contains, etc.) |
| `trustConfig`                                     | Optional trust profile and weight overrides |
| `compactionConfig`                                | Optional compaction triggers and thresholds |

**Scene-setting turn 0** — when `setting` is non-blank and extraction is enabled, an `ESTABLISH` turn runs before turn 1. The DM narrates the setting; DICE extraction captures initial propositions as memory units.

Current corpus: 22 scenarios (plus strategy catalog), 357 scripted turns, 180 evaluated turns.

### Available Scenarios

- `adaptive-tavern-fire.yml` — Adaptive adversary in tavern fire narrative
- `adversarial-contradictory.yml` — Straightforward contradiction attacks
- `adversarial-displacement.yml` — Memory unit displacement attacks
- `adversarial-poisoned-player.yml` — Attack via indirect NPC persona compromise
- `context-drift.yml` — Test semantic drift over long turns
- `authority-inversion-chain.yml` — Authority hierarchy inversion attacks
- `balanced-campaign.yml` — Multi-session campaign with dormancy
- `budget-starvation-interference.yml` — Budget exhaustion via low-value memory unit flooding
- `compaction-stress.yml` — Stress-test context summarization
- `conflicting-canon-crisis.yml` — Conflicting CANON-level assertions
- `cursed-blade.yml` — Narrative scenario (artifact curse detection)
- `dead-kingdom.yml` — High-complexity kingdom state
- `dormancy-revival.yml` — Test activation score decay and archive/revival
- `dungeon-of-mirrors.yml` — Illusion-based memory unit confusion attacks
- `episodic-recall.yml` — Multi-episode memory unit retention
- `evidence-laundering-poisoning.yml` — Evidence laundering through indirect attribution
- `extraction-baseline.yaml` — Extraction accuracy baseline (no attacks)
- `extraction-under-attack.yaml` — Extraction accuracy under stress-test contradiction pressure
- `gen-adversarial-dungeon.yml` — LLM-generated dungeon + adaptive attacks
- `gen-easy-dungeon.yml` — LLM-generated dungeon + baseline (no attacks)
- `multi-session-campaign.yml` — Memory units persisted across sessions
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

seedUnits:                        # Pre-seeded memory units at scenario start
  - text: string                  # Memory unit text
    authority: string             # PROVISIONAL | UNRELIABLE | RELIABLE | CANON
    rank: int                     # Initial activation score [100-900]

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

| Type | Params | Validates |
|------|--------|-----------|
| `unit-count` | `min`, `max` | Final memory unit count is within range |
| `activation-score-distribution` | `minAbove`, `rankThreshold` | At least N memory units have activation score above threshold |
| `trust-score-range` | `min`, `max` | All trust scores fall within range |
| `promotion-zone` | `zone`, `minCount` | At least N memory units are in the specified zone |
| `authority-at-most` | `maxAuthority` | No memory unit exceeds the specified authority level |
| `kg-context-contains` | `patterns` | Final memory unit texts contain all specified patterns |
| `kg-context-empty` | (none) | No active memory units remain |
| `no-canon-auto-assigned` | (none) | No memory unit has CANON authority |
| `compaction-integrity` | `requiredFacts` | All required facts survive compaction |

### Per-Turn Execution Sequence

`SimulationTurnExecutor` runs each turn:

1. Query active memory units via `ArcMemEngine.inject(contextId)`
2. Format memory units as `ESTABLISHED FACTS` block (if injection enabled)
3. Build system prompt — DM persona, campaign setting, context block, contradiction resistance instructions
4. Build user prompt — recent conversation history + current player message
5. Call LLM via Spring AI `ChatModel`
6. Build `ContextTrace` (token counts, injected memory units, full prompts)
7. If turn type requires evaluation — send to evaluator LLM, parse JSON verdicts with severity
8. Diff memory unit state vs previous turn — detect CREATED, REINFORCED, DECAYED, AUTHORITY_CHANGED, EVICTED, ARCHIVED events
9. Check compaction thresholds; compact if needed (`CompactionValidator` checks protected fact survival)
10. Return `TurnExecutionResult`

### Run History Persistence

Set `arc-mem.run-history.store` in `application.yml`:

| Value              | Storage                       | Lifecycle |
|--------------------|-------------------------------|-----------|
| `memory` (default) | ConcurrentHashMap             | Lost on restart |
| `neo4j`            | Neo4j nodes with JSON payload | Persistent across restarts |

## Running Tests

```bash
./mvnw test
```

Tests span two modules: `arcmem-core` (engine, persistence, assembly, extraction, trust) and `arcmem-simulator` (simulation, chat, benchmarking, views).

- **Unit tests**: JUnit 5 + Mockito + AssertJ
- **Integration tests** (`*IT.java`, `@Tag("integration")`): excluded by default via Surefire configuration
- Test structure uses `@Nested` + `@DisplayName`
- Method naming: `actionConditionExpectedOutcome`

## Neo4j Access

| Property    | Value |
|-------------|-------|
| Browser URL | `http://localhost:7474` |
| Username    | `neo4j` |
| Password    | `arcmem123` |
| Bolt port   | `7687` |

Started via `docker-compose.yml`. Both chat and simulation use the same `MemoryUnitRepository` (Drivine-backed, scoped by `contextId`).

## Observability

Optional Langfuse stack for OTEL-based tracing.

```bash
docker compose -f docker-compose.langfuse.yml up -d
```

| Property      | Value |
|---------------|-------|
| UI URL        | `http://localhost:3000` |
| Login         | `dev@arcmem.dev` / `Welcome1!` |
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

Key conventions (see `.arc-mem/coding-style.md` for full reference):

- **Constructor injection only** — never `@Autowired` on fields
- **Records** for all immutable data; sealed interfaces for fixed type hierarchies
- **Modern Java 25** — switch expressions, pattern matching, `.toList()`, text blocks
- **Immutable collections** — `List.of()`, `Set.of()`, `Map.of()`
- **Minimal comments** — code is self-documenting; comment only non-obvious logic

### Adding Features (OpenSpec)

arc-mem uses [OpenSpec](https://github.com/Fission-AI/OpenSpec) for structured feature work:

1. `/opsx:new` — propose a feature, write a spec, design the solution
2. `/opsx:continue` — elaborate the spec, create tasks
3. `/opsx:apply` — work through implementation tasks
4. `/opsx:verify` — confirm implementation matches spec
5. `/opsx:archive` — finalize and document the change

## Ongoing Research

### Collaborative Memory Unit Mutation

The [collaborative-unit-mutation roadmap](openspec/changes/archive/roadmap/2026-03-03-collaborative-unit-mutation/roadmap.md) tackles legitimate revision of established memory units in multi-actor contexts — when an update isn't a contradiction but the memory unit still needs to change. Main threads:

- **Revision intent classification** — `ConflictType` enum distinguishing REVISION from CONTRADICTION
- **Authority-gated revision eligibility** — prompt compliance carveout for memory units that can be revised
- **Dependent memory unit cascade** — invalidation of dependent memory units on supersession
- **Provenance metadata** — extraction turn and speaker role tracking
- **UI-controlled mutation** — explicit memory unit editing via the chat sidebar

The most interesting finding so far: none of the AI memory frameworks I looked at distinguish update from contradiction. They either let the model overwrite memory or reject the conflicting input outright. Revision as a first-class operation seems underexplored.

### Current Direction: AGM Belief Revision

AGM belief revision theory turned out to be a good theoretical fit:
- **Contraction** maps to memory unit archival
- **Revision** maps to supersession (archive + create successor)
- **Entrenchment ordering** maps to authority tiers
- **Minimal change principle** constrains cascade scope

Also drawing on truth maintenance systems for cascade propagation patterns, Wikipedia's ORES for two-axis intent/impact scoring, and accounting materiality as a heuristic for deciding how far a change should ripple.

### Future Work

- **Creative retrieval** — spreading activation and graph-native retrieval for serendipitous knowledge discovery
- **A2A governance** — multi-agent memory unit revision protocols
- Memory poisoning threat model and defenses
- Expanding test harness to multiple Semantic Unit types.  

### Known Limitations

- **Weak instruction followers** — some models ignore system prompt directives regardless of formatting. Memory units can't help if the model won't read them.
- **Cross-session persistence** — memory units must survive context resets and be reconstructed from storage. This works but hasn't been stress-tested.

### Open Questions

1. **Dynamic budget and memory pressure** — Could ARC-Mem adapt budget dynamically based on conversation pressure? ACON's failure-driven compression suggests eviction policy could be driven by what causes drift, not just activation score.
2. **Premature commitment and memory unit timing** — Memory units established in early turns could compound premature commitment (per Laban et al.). When should memory units be promoted? Is there a minimum conversation maturity threshold?
3. **Bi-temporal validity** — Graphiti/Zep distinguishes world-time validity from system-recording time. ARC-Mem currently has no temporal dimension. How should temporal validity interact with authority?
4. **Graph-based reinforcement** — HippoRAG uses spreading activation and personalized PageRank. Current reinforcement is count-based (+50 per mention). Could reinforcement consider knowledge graph position (centrality)?
5. **Self-editing memory units** — MemGPT allows model self-editing of memory blocks. Could the model propose modifications to memory units? Authority constraints could limit this to PROVISIONAL memory units.
6. **Operator-defined invariants** — In production, the most valuable memory units will be defined upfront by operators. This points toward an invariant definition API.
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
