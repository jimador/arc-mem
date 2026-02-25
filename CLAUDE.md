# CLAUDE.md

## Project

**dice-anchors** — Standalone demo app showing how Anchors (enriched DICE Propositions with rank, authority, and budget management) resist adversarial prompt drift. Working reference for DICE <-> Anchor integration. Java 25 / Spring Boot 3.5.10 / Embabel Agent 0.3.5-SNAPSHOT / DICE 0.1.0-SNAPSHOT / Vaadin 24.6.4 / Neo4j 5.x (Drivine ORM).

Single-module Maven project.

## RFC 2119 Keyword Compliance

This document uses keywords per [RFC 2119](https://www.ietf.org/rfc/rfc2119.txt):

| Keyword | Meaning |
|---------|---------|
| **MUST** / **REQUIRED** / **SHALL** | Absolute requirement. Non-compliance is a defect. |
| **MUST NOT** / **SHALL NOT** | Absolute prohibition. Violation is a defect. |
| **SHOULD** / **RECOMMENDED** | Strong recommendation. Deviation requires documented justification. |
| **SHOULD NOT** / **NOT RECOMMENDED** | Strong recommendation against. Deviation requires documented justification. |
| **MAY** / **OPTIONAL** | Truly optional. No justification needed for omission. |

**For LLM Agents**: When executing tasks, treat MUST/SHALL/REQUIRED as blocking requirements. Do not proceed if these cannot be satisfied. SHOULD items should be implemented unless explicitly overridden in the specification.

## How I Work

- **Delegate aggressively** -- Use Task tool subagents for any work that would bloat main context (exploration, multi-file reads, research, implementation of isolated components). Keep main context for orchestration and user communication.
- **Investigate before solving** -- Check specs, existing code, and memory before proposing solutions.
- **Plan when uncertain** -- Use `/plan` for anything non-trivial. Don't guess at architecture.
- **Stop when stuck** -- Don't thrash. Explain what was tried and why it failed.
- **Fix autonomously** -- Bug reports, failing tests, error logs -> fix them without hand-holding.
- **Verify before done** -- Run tests, demonstrate correctness. Don't claim "done" without proof.
- **Capture lessons immediately** -- Update Mistakes Log after any correction, before continuing.

## Subagent Strategy

**Default workflow: Task tool subagents (NOT full agent teams).** Teams (TeamCreate) are reserved for special occasions — they use credits too fast.

**Always delegate to subagents (Task tool):**
- Codebase exploration -- use `subagent_type=Explore` for broad searches
- Multi-file implementation of isolated components -- use `subagent_type=general-purpose`
- Research tasks -- design patterns, library APIs, framework behavior
- Any work that would consume significant context window

**Keep in main context:**
- User communication and clarification
- Orchestration decisions
- Small targeted edits (< 3 files, clear scope)
- Git operations (commits, PRs, branch management)
- Test writing

## Commands

```bash
# Build (skip tests)
./mvnw.cmd clean compile -DskipTests

# Test (84 tests)
./mvnw.cmd test

# Run (needs Neo4j + LLM API key)
docker-compose up -d
OPENAI_API_KEY=sk-... ./mvnw.cmd spring-boot:run
# Then: http://localhost:8089 (sim view), http://localhost:8089/chat, http://localhost:8089/benchmark

# Neo4j browser
# http://localhost:7474 (neo4j/diceanchors123)

# Langfuse observability (optional, separate stack)
docker compose -f docker-compose.langfuse.yml up -d
# Then: http://localhost:3000 (dev@diceanchors.dev / Welcome1!)
# OTEL endpoint: http://localhost:3000/api/public/otel
```

## Code Style

IMPORTANT: Adhere to coding style in `.dice-anchors/coding-style.md` for all code generation. Apply these rules proactively — do not wait to be asked to "deslop" or "clean up".

**Before writing any code, verify it meets these standards:**
- No comment slop (section banners, tautological Javadoc, restating the code)
- No defensive checks on framework-managed values
- Records for all immutable data; sealed interfaces for fixed type hierarchies
- Modern Java 25: switch expressions, pattern matching, `.toList()`, text blocks
- Constructor injection only — never `@Autowired` on fields
- `logger.info("Processing {}", id)` — never string concatenation in log statements
- `List.of()`, `Set.of()`, `Map.of()` for collection literals
- Tests verify business logic only — no structural tests, no trivial enum tests, no getter tests

**The `deslop` skill** defines the full catalog of patterns to avoid. Use it after any code generation pass or when reviewing existing code for cleanup.

See `.dice-anchors/coding-style.md` for the complete reference.

## Architecture

- **Single-module** Spring Boot app with Vaadin UI at three routes: `/` (SimulationView), `/chat` (ChatView), `/benchmark` (BenchmarkView)
- **Neo4j only** -- no PostgreSQL. Drivine for ORM, Neo4j 5.x for persistence
- **Anchors = Propositions + extra fields** -- `rank > 0` means it's an anchor. No separate node type.
- **Budget enforcement** -- max 20 active anchors. Evicts lowest-ranked non-pinned when over budget.
- **Authority upgrade-only** -- PROVISIONAL -> UNRELIABLE -> RELIABLE -> CANON. Never downgrade. CANON never auto-assigned.
- **Rank clamped [100-900]** -- `Anchor.clampRank()`
- **Sim isolation via contextId** -- Each run gets `sim-{uuid}`, cleaned up after.
- **Embabel Agent** for chat orchestration (`ChatActions` @EmbabelComponent)
- **DICE extraction** for proposition management in chat flow
- **Simulation harness** with YAML-defined adversarial/baseline scenarios, turn-by-turn execution, drift evaluation, scene-setting turn 0
- **Benchmarking** with multi-condition ablation experiments, statistical aggregation, resilience scoring, and Markdown report export

## Testing

- **JUnit 5** + **Mockito** + **AssertJ**
- `@Nested` + `@DisplayName` for test structure
- Method naming: `actionConditionExpectedOutcome` (no test prefix)
- Integration tests (`*IT.java`, `@Tag("integration")`) excluded by default via Surefire
- 84 tests across test classes in `anchor/`, `assembly/`, `chat/`, `extract/`, and `sim/` packages

## Key Files

| Purpose | Location |
|---------|----------|
| Main app | `src/main/java/dev/dunnam/diceanchors/DiceAnchorsApplication.java` |
| Config properties | `src/main/java/dev/dunnam/diceanchors/DiceAnchorsProperties.java` |
| Anchor engine | `src/main/java/dev/dunnam/diceanchors/anchor/AnchorEngine.java` |
| Anchor model | `src/main/java/dev/dunnam/diceanchors/anchor/Anchor.java` |
| Authority enum | `src/main/java/dev/dunnam/diceanchors/anchor/Authority.java` |
| Conflict detection | `src/main/java/dev/dunnam/diceanchors/anchor/NegationConflictDetector.java` |
| Conflict resolution | `src/main/java/dev/dunnam/diceanchors/anchor/AuthorityConflictResolver.java` |
| Decay policy | `src/main/java/dev/dunnam/diceanchors/anchor/ExponentialDecayPolicy.java` |
| Reinforcement policy | `src/main/java/dev/dunnam/diceanchors/anchor/ThresholdReinforcementPolicy.java` |
| Lifecycle events | `src/main/java/dev/dunnam/diceanchors/anchor/event/` |
| Neo4j persistence | `src/main/java/dev/dunnam/diceanchors/persistence/AnchorRepository.java` |
| Proposition node | `src/main/java/dev/dunnam/diceanchors/persistence/PropositionNode.java` |
| Context injection | `src/main/java/dev/dunnam/diceanchors/assembly/AnchorsLlmReference.java` |
| Context lock | `src/main/java/dev/dunnam/diceanchors/assembly/AnchorContextLock.java` |
| Token budgeting | `src/main/java/dev/dunnam/diceanchors/assembly/PromptBudgetEnforcer.java` |
| Token counting SPI | `src/main/java/dev/dunnam/diceanchors/assembly/TokenCounter.java` |
| Anchor promotion | `src/main/java/dev/dunnam/diceanchors/extract/AnchorPromoter.java` |
| Duplicate detection | `src/main/java/dev/dunnam/diceanchors/extract/DuplicateDetector.java` |
| Chat actions | `src/main/java/dev/dunnam/diceanchors/chat/ChatActions.java` |
| Chat UI | `src/main/java/dev/dunnam/diceanchors/chat/ChatView.java` |
| Sim service | `src/main/java/dev/dunnam/diceanchors/sim/engine/SimulationService.java` |
| Sim turn executor | `src/main/java/dev/dunnam/diceanchors/sim/engine/SimulationTurnExecutor.java` |
| Sim run context | `src/main/java/dev/dunnam/diceanchors/sim/engine/SimulationRunContext.java` |
| Scoring service | `src/main/java/dev/dunnam/diceanchors/sim/engine/ScoringService.java` |
| Scenario loader | `src/main/java/dev/dunnam/diceanchors/sim/engine/ScenarioLoader.java` |
| Sim UI | `src/main/java/dev/dunnam/diceanchors/sim/views/SimulationView.java` |
| Context inspector | `src/main/java/dev/dunnam/diceanchors/sim/views/ContextInspectorPanel.java` |
| Entity mention graph view | `src/main/java/dev/dunnam/diceanchors/sim/views/EntityMentionNetworkView.java` |
| Benchmark UI | `src/main/java/dev/dunnam/diceanchors/sim/views/BenchmarkView.java` |
| Condition comparison panel | `src/main/java/dev/dunnam/diceanchors/sim/views/ConditionComparisonPanel.java` |
| Fact drill-down panel | `src/main/java/dev/dunnam/diceanchors/sim/views/FactDrillDownPanel.java` |
| Benchmark runner | `src/main/java/dev/dunnam/diceanchors/sim/benchmark/BenchmarkRunner.java` |
| Resilience report | `src/main/java/dev/dunnam/diceanchors/sim/report/ResilienceReport.java` |
| Report builder | `src/main/java/dev/dunnam/diceanchors/sim/report/ResilienceReportBuilder.java` |
| Markdown renderer | `src/main/java/dev/dunnam/diceanchors/sim/report/MarkdownReportRenderer.java` |
| Resilience score | `src/main/java/dev/dunnam/diceanchors/sim/report/ResilienceScore.java` |
| Score calculator | `src/main/java/dev/dunnam/diceanchors/sim/report/ResilienceScoreCalculator.java` |
| Scenario section | `src/main/java/dev/dunnam/diceanchors/sim/report/ScenarioSection.java` |
| Fact survival loader | `src/main/java/dev/dunnam/diceanchors/sim/report/FactSurvivalLoader.java` |
| Contradiction detail loader | `src/main/java/dev/dunnam/diceanchors/sim/report/ContradictionDetailLoader.java` |
| Prompt templates | `src/main/resources/prompts/` |
| Spring config | `src/main/resources/application.yml` |
| Sim scenarios | `src/main/resources/simulations/` |
| Docker Compose | `docker-compose.yml` (Neo4j only) |

## Key Design Decisions

1. **Anchors = Propositions + extra fields** — `rank > 0` means it's an anchor. No separate node type.
2. **Neo4j everywhere** — Both chat and sim use the same `AnchorRepository` (Drivine-backed).
3. **Budget enforcement** — max 20 active anchors. Evicts lowest-ranked non-pinned when over budget.
4. **Authority upgrade-only** — PROVISIONAL -> UNRELIABLE -> RELIABLE -> CANON. Never downgrade. CANON never auto-assigned.
5. **Rank clamped [100-900]** — `Anchor.clampRank()`.
6. **Sim isolation via contextId** — Each run gets `sim-{uuid}`, cleaned up after.
7. **Persistence layer copied from impromptu** — re-packaged to `dev.dunnam.diceanchors.persistence`.
8. **Scene-setting turn 0** — When `scenario.setting()` is non-blank and extraction is enabled, `SimulationService` executes an ESTABLISH turn before turn 1. The DM narrates the setting; DICE extraction captures initial propositions. This gives the anchor framework material to defend before adversarial pressure begins. Skipped if setting is blank or extraction is disabled.
9. **Drift evaluator: epistemic hedging = NOT_MENTIONED** — The drift evaluation prompt distinguishes three DM response categories: (1) contradiction (asserts the opposite), (2) world progression (narrative change that isn't a contradiction), (3) epistemic hedging (declines to affirm without asserting the opposite). Hedging is classified as NOT_MENTIONED, not CONTRADICTED. The player message is included in the evaluator prompt so the evaluator can distinguish defensive resistance from genuine forgetting.
10. **No seed anchors required** — Scenarios may have no seed anchors. The expected flow is that scene-setting turn 0 + warm-up turns allow the anchor framework to accumulate propositions organically before adversarial pressure.

## OpenSpec (Spec-Driven Development)

dice-anchors uses [OpenSpec](https://github.com/Fission-AI/OpenSpec) for structured specification management. All specs live in `openspec/` and follow the project [constitution](openspec/constitution.md).

### OpenSpec Workflow

1. **New feature/change**: Use `/opsx:new` to create a change with proposal -> spec -> design -> tasks
2. **Continue change**: Use `/opsx:continue` to create the next artifact
3. **Fast-forward**: Use `/opsx:ff` to generate all artifacts at once
4. **Implement**: Use `/opsx:apply` to work through tasks
5. **Verify**: Use `/opsx:verify` to validate implementation matches spec
6. **Archive**: Use `/opsx:archive` to finalize completed changes

## What NOT to Do

- Do not use field-level `@Autowired` -- constructor injection only
- Do not return null from service methods -- use `Optional` or throw
- Do not build Cypher via string concatenation -- use `@Query` with parameters
- Do not add wildcard imports
- Do not use mutable defaults -- `List.of()` not `new ArrayList<>()`
- Do not create checked exceptions
- Do not modify anchor rank outside [100, 900] range -- use `clampRank()`
- Do not auto-promote propositions to anchors -- promotion is always explicit
- Do not assume `ContextTrace.assembledPrompt` contains the full LLM prompt -- it only contains the anchor injection block; use `fullSystemPrompt`/`fullUserPrompt` for the complete prompt
- Do not classify DM epistemic hedging as CONTRADICTED -- "the guardian's properties aren't established yet" is NOT_MENTIONED, not a contradiction

## Reference Codebases

- **impromptu**: `https://github.com/embabel/impromptu`
- **urbot**: `https://github.com/embabel/urbot`
- **DICE**: `https://github.com/embabel/dice`

## Embabel References

| Resource                  | URL |
|---------------------------|-----|
| Embabel CLAUDE.md         | https://github.com/embabel/embabel-agent/blob/main/CLAUDE.md |
| Embabel Coding Style      | https://github.com/embabel/embabel-agent/blob/main/embabel-agent-api/.embabel/coding-style.md |
| Embabel DeepWiki          | https://deepwiki.com/embabel/embabel-agent |
| Embabel Examples (Java)   | https://github.com/embabel/embabel-agent-examples/tree/main/examples-java |
| API Docs (0.3.5-SNAPSHOT) | https://docs.embabel.com/embabel-agent/api-docs/0.3.5-SNAPSHOT/index.html |

## Mistakes Log

- Persistence layer copied from impromptu required re-packaging to `dev.dunnam.diceanchors.persistence`
- `DiceRestConfiguration` moved to `com.embabel.dice.web.rest` (not the expected package)
- Drivine annotations in `org.drivine.autoconfigure` (not `org.drivine.config`)
- `LlmReference` doesn't exist in embabel — `AnchorsLlmReference` is a plain class
- `ConversationAnalysisListener` deleted — DICE incremental API type mismatch, not needed for demo
- `PropositionView.toDice()` uses 13-param `Proposition.create()` overload
- `AnchorRepository` switch expression needed `default` branch for Kotlin enum
- Drivine `GraphResultMapper.mapToNative()` returns a `List` (not a `Map`) for multi-column RETURN queries. `.transform(Map.class)` then fails with `Cannot deserialize value of type LinkedHashMap from Array`. Fix: wrap multi-column RETURN values in a Cypher map literal (`RETURN {key: val, ...} AS result`) so the result is a single column containing a Map, hitting the `keys.size == 1` branch
- **Never manually archive or sync OpenSpec changes.** Always use `/opsx:archive` (which handles sync assessment, user confirmation, and proper archival) and `/opsx:sync` for spec syncing. Manual `cp`/`mv` of spec files bypasses the proper workflow and can miss documentation updates or transformations.
