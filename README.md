# dice-anchors — Anchors Resist Adversarial Prompt Drift

A standalone Spring Boot demo showing how **Anchors** (enriched DICE Propositions with rank, authority, and budget management) resist adversarial attacks that try to reframe or contradict established facts. This is a working reference implementation for integrating Embabel Agent + DICE into a real application, using a D&D-themed simulation harness to test anchor stability under attack.

## Quick Start

**Prerequisites**: Java 25, Docker, OpenAI API key.

```bash
# Start Neo4j (required for persistence)
docker-compose up -d

# Build and run the app
OPENAI_API_KEY=sk-... ./mvnw.cmd spring-boot:run

# Open your browser
# Simulation view (adversary testing): http://localhost:8089
# Chat view (interactive anchor demo):  http://localhost:8089/chat
# Neo4j browser (data inspection):      http://localhost:7474 (neo4j/diceanchors123)
```

To run tests (390+ tests across anchor, assembly, sim.engine, and prompt packages):
```bash
./mvnw.cmd test
```

## What Is an Anchor?

An **Anchor** is a fact promoted to a special status in the LLM's context. It has three key properties:

- **Rank** [100–900]: numerical importance; higher rank = more likely to influence the LLM
- **Authority**: a lifecycle stage from PROVISIONAL (new) → UNRELIABLE (contradicted once) → RELIABLE (proven stable) → CANON (manually elevated, never auto-assigned)
- **Reinforcement tracking**: each time an anchor is used successfully, its rank increases and authority may upgrade

Anchors are injected into every LLM prompt as a ranked reference block. When the LLM produces text that contradicts an anchor, the system detects the conflict and decides: keep the anchor (it's more authoritative), upgrade the anchor (the contradiction strengthened it), or reject the incoming text. Budget constraints (max 20 active anchors) force eviction of the weakest non-pinned anchors when the limit is exceeded.

The **simulation** stress-tests anchors with adversarial turns: an attacker (controlled by the LLM) tries to reframe or contradict ground-truth facts using tactics like subtle reframing, detail flooding, or false authority claims. Meanwhile, the anchor engine persists the defender's facts. At the end of each turn, the system evaluates drift — how far the current anchors have strayed from the original ground truth — and ranks how effective the attack was.

## Architecture

### Core Packages

| Package | Purpose |
|---------|---------|
| **anchor/** | Anchor lifecycle: AnchorEngine (budget + promotion), conflict detection/resolution, reinforcement policies |
| **sim/engine/** | Simulation orchestration: SimulationService (phase state machine), SimulationTurnExecutor (turn execution), turn-by-turn LLM calls and drift eval |
| **sim/engine/adversary/** | Attack strategy selection: TieredEscalationStrategy (adaptive), StrategyCatalog (YAML-defined attacks), attack history tracking |
| **sim/views/** | UI: SimulationView (main), ContextInspectorPanel (inspect anchors mid-run), EntityMentionNetworkView (entity mention graph) |
| **chat/** | Chat flow: ChatView (Vaadin UI), ChatActions (Embabel Agent integration) |
| **assembly/** | Prompt assembly: AnchorsLlmReference (inject anchors into prompts), PromptBudgetEnforcer (token limits), ContextLock (multi-request safety) |
| **extract/** | DICE integration: DuplicateDetector (find near-dupes), AnchorPromoter (promote extracted propositions to anchors) |
| **persistence/** | Neo4j repository layer: AnchorRepository, PropositionNode (Drivine ORM) |

### Key Flows

**Chat Flow**:
```
ChatView (user types)
  ↓
ChatActions (Embabel @EmbabelComponent)
  ↓
AnchorsLlmReference (inject anchors into system prompt)
  ↓
LLM (gpt-4.1-mini) + PromptBudgetEnforcer (token limits)
  ↓
Extract propositions (DICE extraction)
  ↓
AnchorPromoter (promote promising propositions to anchors)
  ↓
AnchorEngine (reinforce existing anchors, manage budget)
  ↓
Neo4j persistence (Drivine ORM)
```

**Simulation Flow**:
```
SimulationView (user selects scenario and hits "Run")
  ↓
SimulationService (load scenario YAML, seed anchors, phase state machine)
  ↓
SimulationTurnExecutor (per turn):
   - Assemble anchor + system/user prompts
   - Call LLM (DM response)
   - If ATTACK turn: evaluate drift against ground truth (concurrent)
   - Extract propositions from DM response (concurrent)
   - Promote extracted propositions (concurrent)
  ↓
TieredEscalationStrategy (adaptive: pick next attack based on history)
  or scripted: use explicit player messages from scenario
  ↓
LLM again (generate adversarial player message)
  ↓
Repeat until scenario ends
  ↓
SimulationView updates with final anchor state, drift verdicts, and rankings
```

## Simulation Scenarios

The `src/main/resources/simulations/` directory contains 18 scenario YAML files. Each defines:

- **Initial anchors**: facts to defend (e.g., "The tavern burned down on Day 3")
- **Ground truth**: what actually happened (e.g., "Fire was accidental, not sabotage")
- **Personas**: NPC characters with playstyle goals and narrative arcs
- **Adversary config**: aggressiveness [0–1], escalation tier cap [1–4], preferred attack strategies
- **Turn sequence**: warm-up (baseline), establish (normal play), attack (adversarial), recall (final test)

Scenarios come in two flavors:
- **Scripted adversary**: explicit player messages defined in YAML; tests reproducibility
- **Adaptive adversary**: TieredEscalationStrategy selects attacks from the strategy catalog (YAML) based on anchor state and attack history

**Available Scenarios**:
- `adversarial-contradictory.yml` — Straightforward contradiction attacks
- `adversarial-displacement.yml` — Anchor displacement attacks (Tier 3+)
- `adversarial-poisoned-player.yml` — Attack via indirect NPC persona compromise
- `anchor-drift.yml` — Test semantic drift over long turns
- `balanced-campaign.yml` — Multi-session campaign with dormancy
- `compaction-stress.yml` — Stress-test context summarization
- `cursed-blade.yml` — Narrative scenario (artifact curse detection)
- `dead-kingdom.yml` — High-complexity kingdom state
- `dormancy-revival.yml` — Test rank decay and archive/revival
- `dungeon-of-mirrors.yml` — Illusion-based anchor confusion attacks
- `episodic-recall.yml` — Multi-episode anchor retention
- `gen-adversarial-dungeon.yml` — LLM-generated dungeon + adaptive attacks
- `gen-easy-dungeon.yml` — LLM-generated dungeon + baseline (no attacks)
- `multi-session-campaign.yml` — Anchors persisted across sessions
- `narrative-dm-driven.yml` — DM-controlled narrative (no adversary)
- `trust-evaluation-basic.yml` — Basic trust score evaluation
- `trust-evaluation-full-signals.yml` — Full trust score with all signals
- `strategy-catalog.yml` — Not a scenario; defines attack strategies for adaptive mode

## Key Concepts

### Anchor Lifecycle

```
Proposition (extracted from LLM)
  ↓
Duplicate detection (reject if near-duplicate of existing anchor)
  ↓
Promotion (if trust score > threshold)
  ↓
AnchorEngine:
   - Assign rank (default 500, clamped [100, 900])
   - Assign authority (PROVISIONAL)
   - Track reinforcement count
  ↓
Budget enforcement (if count > 20, evict lowest-ranked non-pinned)
  ↓
On reuse (LLM prompt injection):
   - Increment reinforcement count
   - Boost rank by reinforcement policy
   - Upgrade authority (PROVISIONAL → UNRELIABLE → RELIABLE if thresholds met)
  ↓
On conflict (LLM output contradicts anchor):
   - Detect via NegationConflictDetector or LlmConflictDetector
   - Resolve via AuthorityConflictResolver (higher authority wins)
  ↓
On dormancy (unused for N turns):
   - Rank decays exponentially (ExponentialDecayPolicy)
   - Eventually auto-archive when rank reaches MIN_RANK
```

### Simulation Drift Evaluation

After adversarial attacks, the system evaluates **drift** — how far anchors have strayed from ground truth:

```
DriftEvaluationResult (one per turn):
  - verdict (ANCHOR_STABLE, MINOR_DRIFT, MAJOR_DRIFT, FACT_CORRUPTED)
  - affected_anchors (which anchors changed)
  - drift_severity [0.0–1.0]
```

The simulation reports this per turn, and the UI renders a drift score graph so you can see at what point attacks succeeded.

### Budget Enforcement

Maximum 20 active anchors. When promotion or reinforcement would exceed this:
1. Collect all non-CANON, non-pinned anchors
2. Sort by rank (ascending)
3. Evict the lowest-ranked until count <= 20

This ensures the LLM always sees the most important facts, not a bloated set of marginal propositions.

## Testing

390+ tests across the `anchor/`, `assembly/`, `sim.engine/`, and `prompt/` packages. Run them with:

```bash
./mvnw.cmd test
```

Test coverage includes:
- **Anchor lifecycle**: promotion, reinforcement, rank clamping, authority upgrades
- **Budget enforcement**: eviction order, pinned anchor immunity
- **Conflict detection/resolution**: negation-based and LLM-based conflict handling
- **Prompt assembly**: token budgeting, anchor injection, context locking
- **Decay and dormancy**: exponential rank decay, archive lifecycle
- **Proposition extraction**: duplication detection, trust scoring

Integration tests (`*IT.java`) are excluded by default (they require Neo4j and LLM API keys).

## Development

### Prerequisites for Contributing

- **Java 25** (or later)
- **Docker** (for Neo4j)
- **Maven 3.9+**
- **OpenAI API key** (for testing/running)

### Build & Run Commands

See CLAUDE.md for detailed commands. Quick summary:

```bash
# Build (skip tests)
./mvnw.cmd clean compile -DskipTests

# Test
./mvnw.cmd test

# Run (full stack)
docker-compose up -d
OPENAI_API_KEY=sk-... ./mvnw.cmd spring-boot:run
```

### Coding Style

**Key conventions** (see CLAUDE.md for full details):
- **Constructor injection only** — never `@Autowired` on fields
- **Records** for DTOs, tool containers, and config
- **`var`** for local variables where type is obvious; explicit types for fields/parameters
- **Immutable collections** — `List.of()`, `Set.of()`, `Map.of()`
- **No field-level `@Autowired`** — Spring constructor injection only
- **Enum-driven state machines** — `TurnType`, `Authority`, etc. with validated transitions
- **Javadoc with invariants** on domain classes (e.g., `A1: anchor count <= budget`)
- **Minimal inline comments** — code is self-documenting; comment only non-obvious logic

### Adding Features

dice-anchors uses **OpenSpec** (spec-driven development) for structured feature work:

1. **Create a change**: `/opsx:new` — propose a feature, write a spec, design the solution
2. **Continue**: `/opsx:continue` — elaborate the spec, create tasks
3. **Implement**: `/opsx:apply` — work through implementation tasks
4. **Verify**: `/opsx:verify` — confirm implementation matches spec
5. **Archive**: `/opsx:archive` — finalize and document the change

See CLAUDE.md → "OpenSpec Workflow" for detailed instructions.

### Common Tasks

**Adding a new attack strategy**:
1. Define strategy in `src/main/resources/simulations/strategy-catalog.yml`
2. Implement prompt template in `src/main/resources/prompts/`
3. Update `AdaptiveAttackPrompter` or `TieredEscalationStrategy` if needed
4. Test via simulation scenario

**Adding a new scenario**:
1. Create YAML in `src/main/resources/simulations/your-scenario.yml`
2. Match the structure of existing scenarios (see `anchor-drift.yml`)
3. Load and run via SimulationView UI

**Adding a test**:
1. Create test class in `src/test/java/...` with pattern `*Test.java` or `*IT.java`
2. Use `@Nested` + `@DisplayName` for structure
3. Name methods: `actionConditionExpectedOutcome`
4. Run `./mvnw.cmd test`

## Technology Stack

| Component | Version |
|-----------|---------|
| Java | 25 |
| Spring Boot | 3.5.10 |
| Embabel Agent | 0.3.5-SNAPSHOT |
| DICE | 0.1.0-SNAPSHOT |
| Vaadin | 24.6.4 |
| Neo4j | 5.x (Drivine ORM) |
| Jinja2 | All prompts use Jinja2 templates |
| JUnit 5 | Testing framework |

## Links

- **CLAUDE.md** — Architecture decisions, detailed coding style, reference codebases
- **CHECKLIST.md** — Refactor checklist (dead code removal, clarity improvements, documentation)
- **Embabel Agent** — https://github.com/embabel/embabel-agent
- **DICE** — https://github.com/embabel/dice
- **impromptu** (reference codebase) — https://github.com/embabel/impromptu
- **OpenSpec** — https://github.com/Fission-AI/OpenSpec

---

**This is a DEMO repository**, not production code. It prioritizes clarity and teachability over robustness. Use it to understand how Anchors, DICE extraction, and Embabel integration work together.
