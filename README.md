# dice-anchors

Anchors resist adversarial prompt drift — a working reference for DICE + Embabel Agent integration.

## What is dice-anchors?

Large language models forget. Over a multi-turn conversation, established facts silently erode: an attacker reframes a detail, the model accommodates, and ground truth drifts. In agentic systems where the LLM drives decisions, this drift has real consequences.

**dice-anchors** demonstrates a defense: **Anchors** — enriched [DICE](https://github.com/embabel/dice) Propositions with rank, authority, and budget management that are injected into every LLM prompt as a ranked reference block. When incoming text contradicts an anchor, the system detects the conflict and resolves it based on authority. Budget constraints (max 20 active anchors) force eviction of the weakest, keeping the context window focused on the most important facts.

The project includes a D&D-themed adversarial simulation harness that stress-tests anchor stability. An attacker (controlled by the LLM) tries to reframe or contradict ground-truth facts using tactics like subtle reframing, detail flooding, or false authority claims. The system evaluates drift per turn — how far anchors have strayed from ground truth — and a benchmarking framework measures resilience across ablation conditions.

## Architecture Overview

### Core Packages

| Package        | Purpose                                                                                                                                                                                                                                                                                                     |
|----------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| `anchor/`      | Anchor lifecycle: AnchorEngine (budget + promotion), conflict detection/resolution, trust pipeline, reinforcement and decay policies, memory tiers, invariant evaluation                                                                                                                                    |
| `assembly/`    | Prompt assembly: AnchorsLlmReference (inject anchors into prompts), PromptBudgetEnforcer (token limits), CompactedContextProvider (context summarization), AnchorContextLock (multi-request safety)                                                                                                         |
| `chat/`        | Chat flow: ChatView (Vaadin UI), ChatActions (Embabel Agent @EmbabelComponent), AnchorTools                                                                                                                                                                                                                 |
| `extract/`     | DICE integration: DuplicateDetector (find near-dupes), AnchorPromoter (promote extracted propositions to anchors)                                                                                                                                                                                           |
| `persistence/` | Neo4j repository layer: AnchorRepository, PropositionNode (Drivine ORM)                                                                                                                                                                                                                                     |
| `prompt/`      | Prompt templates: PromptTemplates, PromptPathConstants, Jinja2 template files                                                                                                                                                                                                                               |
| `sim/`         | Simulation harness: engine (orchestration, LLM calls, scoring, adversary strategies), views (SimulationView, RunInspectorView, BenchmarkView, ContextInspectorPanel), benchmark (BenchmarkRunner, ablation conditions), report (ResilienceReport, MarkdownReportRenderer), assertions (post-run validation) |

### Chat Flow

```
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

```
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

An **Anchor** is a fact promoted to special status in the LLM's context:

- **Rank** [100-900]: numerical importance; higher rank = more influence on the LLM
- **Authority**: lifecycle stage from PROVISIONAL → UNRELIABLE → RELIABLE → CANON (upgrade-only; CANON never auto-assigned)
- **Reinforcement tracking**: each successful reuse boosts rank and may upgrade authority
- **Decay**: unused anchors decay exponentially; eventually auto-archived at MIN_RANK
- **Budget enforcement**: max 20 active anchors; evicts lowest-ranked non-pinned when exceeded

### Trust Pipeline

`TrustPipeline` evaluates propositions through multiple trust signals before promotion:

- Source authority assessment
- Extraction confidence scoring
- Reinforcement history tracking
- `TrustAuditRecord` captures the decision trail
- Domain-specific invariant rules via `InvariantEvaluator`
- `CanonizationGate` controls CANON assignment (always explicit, never automatic)

### Adversarial Simulation

The simulation harness runs scenarios with turn-by-turn execution and drift evaluation:

- **Drift verdicts**: each ground-truth fact is evaluated as CONTRADICTED, CONFIRMED, or NOT_MENTIONED per turn
- **Epistemic hedging** (DM declines to affirm without asserting the opposite) is classified as NOT_MENTIONED, not CONTRADICTED
- **Scene-setting turn 0**: when `setting` is non-blank and extraction is enabled, an ESTABLISH turn executes before turn 1 to seed initial propositions organically
- **Adversary modes**: scripted (explicit player messages in YAML) or adaptive (strategy selection based on anchor state and attack history)

### Context Compaction

`CompactedContextProvider` summarizes older context when token thresholds are exceeded. `CompactionValidator` ensures protected facts survive compaction. Memory tiers (`T0_INVARIANT`, `T1_WORKING`, `T2_EPISODIC`) influence decay rates and eviction priority.

## Getting Started

### Prerequisites

| Requirement    | Version | Notes                                       |
|----------------|---------|---------------------------------------------|
| Java           | 25      | Required by the project                     |
| Docker         | Recent  | For Neo4j container                         |
| Docker Compose | v2+     | Bundled with Docker Desktop                 |
| LLM API Key    | --      | OpenAI key required for simulation and chat |
| Maven          | Bundled | Use the included `./mvnw` wrapper           |

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

| Route                             | View             | Purpose                             |
|-----------------------------------|------------------|-------------------------------------|
| `http://localhost:8089`           | SimulationView   | Adversarial simulation harness      |
| `http://localhost:8089/chat`      | ChatView         | Interactive anchor demo             |
| `http://localhost:8089/benchmark` | BenchmarkView    | Multi-condition ablation benchmarks |
| `http://localhost:8089/run`       | RunInspectorView | Detailed run inspection             |

## Running Simulations

Navigate to `http://localhost:8089` (routes to SimulationView).

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
- **Scenario pack**: select deterministic claim pack for primary evidence, stochastic stress pack for secondary
- **Repetitions**: 10-20 per cell for reliable results

Reports are built by `ResilienceReportBuilder` and rendered by `MarkdownReportRenderer`. See [evaluation.md](docs/evaluation.md) for metrics definitions, integrity checks, and interpretation guidance.

## Scenario Configuration

Scenarios are YAML files in `src/main/resources/simulations/`. Each file defines a complete test case.

**Key fields:**

| Field                                             | Purpose                                                                          |
|---------------------------------------------------|----------------------------------------------------------------------------------|
| `id`, `category`, `adversarial`                   | Identification and classification                                                |
| `persona`                                         | Player character (name, description, playStyle)                                  |
| `model`, `temperature`, `maxTurns`, `warmUpTurns` | Execution parameters                                                             |
| `setting`                                         | Multi-line campaign context injected into the DM system prompt                   |
| `groundTruth`                                     | Facts to evaluate against (id + text)                                            |
| `seedAnchors`                                     | Pre-seeded anchors (text, authority, rank)                                       |
| `turns`                                           | Scripted player turns with type, strategy, prompt, targetFact                    |
| `assertions`                                      | Post-run validation (anchor-count, rank-distribution, kg-context-contains, etc.) |
| `trustConfig`                                     | Optional trust profile and weight overrides                                      |
| `compactionConfig`                                | Optional compaction triggers and thresholds                                      |

**Scene-setting turn 0**: when `setting` is non-blank and extraction is enabled, an ESTABLISH turn executes before turn 1. The DM narrates the setting; DICE extraction captures initial propositions as anchors.

Current corpus: 24 scenarios, 357 scripted turns, 180 evaluated turns.

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

| Type                     | Params                      | Validates                                         |
|--------------------------|-----------------------------|---------------------------------------------------|
| `anchor-count`           | `min`, `max`                | Final anchor count is within range                |
| `rank-distribution`      | `minAbove`, `rankThreshold` | At least N anchors have rank above threshold      |
| `trust-score-range`      | `min`, `max`                | All trust scores fall within range                |
| `promotion-zone`         | `zone`, `minCount`          | At least N anchors are in the specified zone      |
| `authority-at-most`      | `maxAuthority`              | No anchor exceeds the specified authority level   |
| `kg-context-contains`    | `patterns`                  | Final anchor texts contain all specified patterns |
| `kg-context-empty`       | (none)                      | No active anchors remain                          |
| `no-canon-auto-assigned` | (none)                      | No anchor has CANON authority                     |
| `compaction-integrity`   | `requiredFacts`             | All required facts survive compaction             |

### Per-Turn Execution Sequence

Each turn executes through `SimulationTurnExecutor`:

1. Query active anchors via `AnchorEngine.inject(contextId)`
2. Format anchors as `ESTABLISHED FACTS` block (if injection enabled)
3. Build system prompt: DM persona, campaign setting, anchor block, contradiction resistance instructions
4. Build user prompt: recent conversation history + current player message
5. Call LLM via Spring AI `ChatModel`
6. Build `ContextTrace` (token counts, injected anchors, full prompts)
7. If turn type requires evaluation: send to evaluator LLM, parse JSON verdicts with severity
8. Diff anchor state vs previous turn: detect CREATED, REINFORCED, DECAYED, AUTHORITY_CHANGED, EVICTED, ARCHIVED events
9. Check compaction thresholds; compact if needed (validate protected fact survival via `CompactionValidator`)
10. Return `TurnExecutionResult`

### Run History Persistence

Configure via `dice-anchors.run-history.store` in `application.yml`:

| Value              | Storage                       | Lifecycle                  |
|--------------------|-------------------------------|----------------------------|
| `memory` (default) | ConcurrentHashMap             | Lost on restart            |
| `neo4j`            | Neo4j nodes with JSON payload | Persistent across restarts |

## Running Tests

```bash
./mvnw test
```

696 tests across packages: `anchor/`, `assembly/`, `chat/`, `extract/`, `sim/`.

- **Unit tests**: JUnit 5 + Mockito + AssertJ
- **Integration tests** (`*IT.java`, `@Tag("integration")`): excluded by default via Surefire configuration
- Test structure uses `@Nested` + `@DisplayName`
- Method naming: `actionConditionExpectedOutcome`

## Neo4j Access

| Property    | Value                   |
|-------------|-------------------------|
| Browser URL | `http://localhost:7474` |
| Username    | `neo4j`                 |
| Password    | `diceanchors123`        |
| Bolt port   | `7687`                  |

Started via `docker-compose.yml`. Both chat and simulation use the same `AnchorRepository` (Drivine-backed, scoped by `contextId`).

## Observability

Optional Langfuse stack for OTEL-based observability.

```bash
docker compose -f docker-compose.langfuse.yml up -d
```

| Property      | Value                                   |
|---------------|-----------------------------------------|
| UI URL        | `http://localhost:3000`                 |
| Login         | `dev@diceanchors.dev` / `Welcome1!`     |
| OTEL endpoint | `http://localhost:3000/api/public/otel` |

## Troubleshooting

| Issue                          | Resolution                                                                                        |
|--------------------------------|---------------------------------------------------------------------------------------------------|
| Neo4j connection refused       | Verify `docker-compose up -d` completed; check `docker ps` for healthy container                  |
| `OPENAI_API_KEY` errors        | Ensure the environment variable is set before `spring-boot:run`                                   |
| Port 8089 in use               | Stop conflicting process or change `server.port` in `application.yml`                             |
| Test failures on clean clone   | Run `./mvnw clean compile` first; integration tests (`*IT.java`) require a running Neo4j instance |
| Langfuse not starting          | Ensure port 3000 is free; the Langfuse stack is independent of the main `docker-compose.yml`      |
| Stale simulation data          | Each run uses an isolated `contextId`; stale data from previous runs does not affect new runs     |
| Build failures on Java version | Java 25 is required; verify with `java -version`                                                  |

## Technology Stack

| Component     | Version                          |
|---------------|----------------------------------|
| Java          | 25                               |
| Spring Boot   | 3.5.10                           |
| Embabel Agent | 0.3.5-SNAPSHOT                   |
| DICE          | 0.1.0-SNAPSHOT                   |
| Vaadin        | 24.6.4                           |
| Neo4j         | 5.x (Drivine ORM)                |
| Jinja2        | All prompts use Jinja2 templates |
| JUnit 5       | Testing framework                |

## Development

### Coding Style

Key conventions (see `.dice-anchors/coding-style.md` for full reference):

- **Constructor injection only** — never `@Autowired` on fields
- **Records** for all immutable data; sealed interfaces for fixed type hierarchies
- **Modern Java 25**: switch expressions, pattern matching, `.toList()`, text blocks
- **Immutable collections** — `List.of()`, `Set.of()`, `Map.of()`
- **Minimal comments** — code is self-documenting; comment only non-obvious logic

### Adding Features (OpenSpec)

dice-anchors uses [OpenSpec](https://github.com/Fission-AI/OpenSpec) for structured feature work:

1. `/opsx:new` — propose a feature, write a spec, design the solution
2. `/opsx:continue` — elaborate the spec, create tasks
3. `/opsx:apply` — work through implementation tasks
4. `/opsx:verify` — confirm implementation matches spec
5. `/opsx:archive` — finalize and document the change

## Links

- [CLAUDE.md](CLAUDE.md) — Architecture decisions, detailed coding style, key files reference
- [Developer Docs](docs/) — Architecture, design rationale, evaluation protocol, known issues
- [Embabel Agent](https://github.com/embabel/embabel-agent)
- [DICE](https://github.com/embabel/dice)
- [impromptu](https://github.com/embabel/impromptu)
- [OpenSpec](https://github.com/Fission-AI/OpenSpec)

