<!-- sync: openspec/specs/run-history-persistence -->
<!-- last-synced: 2026-02-25 -->

# Reproducibility Guide

## 1. Prerequisites

| Requirement | Version | Notes |
|-------------|---------|-------|
| Java | 25 | Required by the project |
| Docker | Recent | For Neo4j container |
| Docker Compose | v2+ | Bundled with Docker Desktop |
| LLM API Key | -- | OpenAI key required for simulation and chat |
| Maven | Bundled | Use the included `./mvnw.cmd` wrapper |

## 2. Quick Start

```bash
# Clone
git clone <repo-url>
cd dice-anchors

# Start Neo4j
docker-compose up -d

# Build (skip tests for faster iteration)
./mvnw.cmd clean compile -DskipTests

# Run the application
OPENAI_API_KEY=sk-... ./mvnw.cmd spring-boot:run
```

The application starts on `http://localhost:8089`.

## 3. Running the Simulation UI

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

## 4. Running Benchmarks

Navigate to `http://localhost:8089/benchmark`.

**Configure the benchmark matrix:**
- **Conditions**: `FULL_ANCHORS`, `NO_ANCHORS`, `FLAT_AUTHORITY` (and `NO_TRUST` once implemented)
- **Scenario pack**: Select deterministic claim pack for primary evidence, stochastic stress pack for secondary
- **Repetitions**: 10-20 per cell for reliable results

Implementation: `BenchmarkRunner` (`src/main/java/dev/dunnam/diceanchors/sim/benchmark/BenchmarkRunner.java`) orchestrates multi-condition runs. Reports are built by `ResilienceReportBuilder` and rendered by `MarkdownReportRenderer`.

**Retain after export:**
- Experiment report IDs
- Benchmark report IDs
- Per-run records with full manifest data

See [evaluation.md](evaluation.md) for metrics definitions, integrity checks, and interpretation guidance.

## 5. Running Tests

```bash
./mvnw.cmd test
```

696 tests across packages: `anchor/`, `assembly/`, `chat/`, `extract/`, `sim/`.

- **Unit tests**: JUnit 5 + Mockito + AssertJ
- **Integration tests** (`*IT.java`, `@Tag("integration")`): excluded by default via Surefire configuration
- Test structure uses `@Nested` + `@DisplayName`
- Method naming: `actionConditionExpectedOutcome`

## 6. Neo4j Access

| Property | Value |
|----------|-------|
| Browser URL | `http://localhost:7474` |
| Username | `neo4j` |
| Password | `diceanchors123` |
| Bolt port | `7687` |

Started via `docker-compose.yml`. Both chat and simulation use the same `AnchorRepository` (Drivine-backed, scoped by `contextId`).

## 7. Langfuse Observability (Optional)

Separate Docker Compose stack for OTEL-based observability.

```bash
docker compose -f docker-compose.langfuse.yml up -d
```

| Property | Value |
|----------|-------|
| UI URL | `http://localhost:3000` |
| Login | `dev@diceanchors.dev` / `Welcome1!` |
| OTEL endpoint | `http://localhost:3000/api/public/otel` |

## 8. Scenario Configuration

Scenarios are YAML files in `src/main/resources/simulations/`. Each file defines a complete test case.

**Key fields:**

| Field | Purpose |
|-------|---------|
| `id`, `category`, `adversarial` | Identification and classification |
| `persona` | Player character (name, description, playStyle) |
| `model`, `temperature`, `maxTurns`, `warmUpTurns` | Execution parameters |
| `setting` | Multi-line campaign context injected into the DM system prompt |
| `groundTruth` | Facts to evaluate against (id + text) |
| `seedAnchors` | Pre-seeded anchors (text, authority, rank) |
| `turns` | Scripted player turns with type, strategy, prompt, targetFact |
| `assertions` | Post-run validation (anchor-count, rank-distribution, kg-context-contains, etc.) |
| `trustConfig` | Optional trust profile and weight overrides |
| `compactionConfig` | Optional compaction triggers and thresholds |

**Scene-setting turn 0**: When `setting` is non-blank and extraction is enabled, an ESTABLISH turn executes before turn 1. The DM narrates the setting; DICE extraction captures initial propositions as anchors.

Current corpus: 24 scenarios, 357 scripted turns, 180 evaluated turns.

To list scenarios:
```bash
ls src/main/resources/simulations/*.y*ml
```

### Run History Persistence

Configure via `dice-anchors.run-history.store` in `src/main/resources/application.yml`:

| Value | Storage | Lifecycle |
|-------|---------|-----------|
| `memory` (default) | ConcurrentHashMap | Lost on restart |
| `neo4j` | Neo4j nodes with JSON payload | Persistent across restarts |

## 9. Troubleshooting

| Issue | Resolution |
|-------|------------|
| Neo4j connection refused | Verify `docker-compose up -d` completed; check `docker ps` for healthy container |
| `OPENAI_API_KEY` errors | Ensure the environment variable is set before `spring-boot:run` |
| Port 8089 in use | Stop conflicting process or change `server.port` in `application.yml` |
| Test failures on clean clone | Run `./mvnw.cmd clean compile` first; integration tests (`*IT.java`) require a running Neo4j instance |
| Langfuse not starting | Ensure port 3000 is free; the Langfuse stack is independent of the main `docker-compose.yml` |
| Stale simulation data | Each run uses an isolated `contextId`; stale data from previous runs does not affect new runs |
| Build failures on Java version | Java 25 is required; verify with `java -version` |
