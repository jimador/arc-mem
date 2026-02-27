# CLAUDE.md

## Project

**dice-anchors** — Test bed for Anchors (enriched DICE Propositions with rank, authority, and budget management) as a working memory model for long-horizon attention stability and hallucination/contradiction control (validated with adversarial stress prompts). Working reference for DICE <-> Anchor integration. Java 25 / Spring Boot 3.5.10 / Embabel Agent 0.3.5-SNAPSHOT / DICE 0.1.0-SNAPSHOT / Vaadin 24.6.4 / Neo4j 5.x (Drivine ORM).

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
./mvnw clean compile -DskipTests

# Test
./mvnw test

# ── Host development (Neo4j in Docker, app on host) ──
docker compose up -d
OPENAI_API_KEY=sk-... ./mvnw spring-boot:run
# Then: http://localhost:8089 (sim view), http://localhost:8089/chat, http://localhost:8089/benchmark

# ── Full-stack Docker (Neo4j + Langfuse + app, all containerized) ──
OPENAI_API_KEY=sk-... docker compose -f docker-compose.app.yml up -d

# With observability enabled:
OPENAI_API_KEY=sk-... TRACING_ENABLED=true OBSERVABILITY_ENABLED=true \
  docker compose -f docker-compose.app.yml up -d

# Build image only:
OPENAI_API_KEY=sk-... docker compose -f docker-compose.app.yml build

# Neo4j browser
# http://localhost:7474 (neo4j/diceanchors123)

# Langfuse observability (standalone, or included in docker-compose.app.yml)
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

- **Single-module** Spring Boot app with Vaadin UI at four routes: `/` (SimulationView), `/chat` (ChatView), `/benchmark` (BenchmarkView), `/run` (RunInspectorView)
- **Neo4j only** -- no PostgreSQL. Drivine for ORM, Neo4j 5.x for persistence
- **Anchors = Propositions + extra fields** -- `rank > 0` means it's an anchor. No separate node type.
- **Budget enforcement** -- configurable max active anchors (default 20). Evicts lowest-ranked non-pinned when over budget.
- **Authority bidirectional** -- PROVISIONAL ↔ UNRELIABLE ↔ RELIABLE ↔ CANON. Promoted via reinforcement, demoted via rank decay or trust re-evaluation. CANON immune to auto-demotion. Pinned anchors immune to auto-demotion.
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
- Tests span test classes in `anchor/`, `assembly/`, `chat/`, `extract/`, `persistence/`, `prompt/`, and `sim/` packages

## Key Files

| Purpose | Location |
|---------|----------|
| Main app | `src/main/java/dev/dunnam/diceanchors/DiceAnchorsApplication.java` |
| Config properties | `src/main/java/dev/dunnam/diceanchors/DiceAnchorsProperties.java` |
| Anchor engine | `src/main/java/dev/dunnam/diceanchors/anchor/AnchorEngine.java` |
| Anchor model | `src/main/java/dev/dunnam/diceanchors/anchor/Anchor.java` |
| Authority enum | `src/main/java/dev/dunnam/diceanchors/anchor/Authority.java` |
| Negation conflict detection | `src/main/java/dev/dunnam/diceanchors/anchor/NegationConflictDetector.java` |
| LLM conflict detection | `src/main/java/dev/dunnam/diceanchors/anchor/LlmConflictDetector.java` |
| Composite conflict detection | `src/main/java/dev/dunnam/diceanchors/anchor/CompositeConflictDetector.java` |
| Conflict resolution | `src/main/java/dev/dunnam/diceanchors/anchor/AuthorityConflictResolver.java` |
| Decay policy | `src/main/java/dev/dunnam/diceanchors/anchor/DecayPolicy.java` |
| Reinforcement policy | `src/main/java/dev/dunnam/diceanchors/anchor/ReinforcementPolicy.java` |
| Trust pipeline | `src/main/java/dev/dunnam/diceanchors/anchor/TrustPipeline.java` |
| Trust audit record | `src/main/java/dev/dunnam/diceanchors/anchor/TrustAuditRecord.java` |
| Invariant evaluator | `src/main/java/dev/dunnam/diceanchors/anchor/InvariantEvaluator.java` |
| Canonization gate | `src/main/java/dev/dunnam/diceanchors/anchor/CanonizationGate.java` |
| Memory tier | `src/main/java/dev/dunnam/diceanchors/anchor/MemoryTier.java` |
| Domain profile | `src/main/java/dev/dunnam/diceanchors/anchor/DomainProfile.java` |
| Lifecycle events | `src/main/java/dev/dunnam/diceanchors/anchor/event/` |
| Neo4j persistence | `src/main/java/dev/dunnam/diceanchors/persistence/AnchorRepository.java` |
| Proposition node | `src/main/java/dev/dunnam/diceanchors/persistence/PropositionNode.java` |
| Proposition view | `src/main/java/dev/dunnam/diceanchors/persistence/PropositionView.java` |
| Context injection | `src/main/java/dev/dunnam/diceanchors/assembly/AnchorsLlmReference.java` |
| Context lock | `src/main/java/dev/dunnam/diceanchors/assembly/AnchorContextLock.java` |
| Token budgeting | `src/main/java/dev/dunnam/diceanchors/assembly/PromptBudgetEnforcer.java` |
| Token counting SPI | `src/main/java/dev/dunnam/diceanchors/assembly/TokenCounter.java` |
| Compacted context | `src/main/java/dev/dunnam/diceanchors/assembly/CompactedContextProvider.java` |
| Compaction validator | `src/main/java/dev/dunnam/diceanchors/assembly/CompactionValidator.java` |
| Relevance scorer | `src/main/java/dev/dunnam/diceanchors/assembly/RelevanceScorer.java` |
| Anchor promotion | `src/main/java/dev/dunnam/diceanchors/extract/AnchorPromoter.java` |
| Duplicate detection | `src/main/java/dev/dunnam/diceanchors/extract/DuplicateDetector.java` |
| Chat actions | `src/main/java/dev/dunnam/diceanchors/chat/ChatActions.java` |
| Chat context init | `src/main/java/dev/dunnam/diceanchors/chat/ChatContextInitializer.java` |
| Anchor tools | `src/main/java/dev/dunnam/diceanchors/chat/AnchorTools.java` |
| Chat UI | `src/main/java/dev/dunnam/diceanchors/chat/ChatView.java` |
| Prompt templates | `src/main/java/dev/dunnam/diceanchors/prompt/PromptTemplates.java` |
| Prompt path constants | `src/main/java/dev/dunnam/diceanchors/prompt/PromptPathConstants.java` |
| Sim service | `src/main/java/dev/dunnam/diceanchors/sim/engine/SimulationService.java` |
| Sim turn executor | `src/main/java/dev/dunnam/diceanchors/sim/engine/SimulationTurnExecutor.java` |
| Sim run context | `src/main/java/dev/dunnam/diceanchors/sim/engine/SimulationRunContext.java` |
| LLM call service | `src/main/java/dev/dunnam/diceanchors/sim/engine/LlmCallService.java` |
| Context trace | `src/main/java/dev/dunnam/diceanchors/sim/engine/ContextTrace.java` |
| Scoring service | `src/main/java/dev/dunnam/diceanchors/sim/engine/ScoringService.java` |
| Scenario loader | `src/main/java/dev/dunnam/diceanchors/sim/engine/ScenarioLoader.java` |
| Adversary strategy | `src/main/java/dev/dunnam/diceanchors/sim/engine/adversary/AdversaryStrategy.java` |
| Adaptive attack prompter | `src/main/java/dev/dunnam/diceanchors/sim/engine/adversary/AdaptiveAttackPrompter.java` |
| Sim assertions | `src/main/java/dev/dunnam/diceanchors/sim/assertions/` |
| Sim UI | `src/main/java/dev/dunnam/diceanchors/sim/views/SimulationView.java` |
| Run inspector UI | `src/main/java/dev/dunnam/diceanchors/sim/views/RunInspectorView.java` |
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
| Prompt template files | `src/main/resources/prompts/` |
| Spring config | `src/main/resources/application.yml` |
| Sim scenarios | `src/main/resources/simulations/` |
| Docker Compose (Neo4j) | `docker-compose.yml` |
| Docker Compose (full stack) | `docker-compose.app.yml` |
| Docker Compose (Langfuse) | `docker-compose.langfuse.yml` |
| Dockerfile | `Dockerfile` |

## Key Design Decisions

1. **Anchors = Propositions + extra fields** — `rank > 0` means it's an anchor. No separate node type.
2. **Neo4j everywhere** — Both chat and sim use the same `AnchorRepository` (Drivine-backed).
3. **Budget enforcement** — configurable max active anchors (default 20). Evicts lowest-ranked non-pinned when over budget.
4. **Authority bidirectional** — PROVISIONAL ↔ UNRELIABLE ↔ RELIABLE ↔ CANON. Promoted via reinforcement, demoted via rank decay or trust re-evaluation. CANON immune to auto-demotion (A3b). Pinned anchors immune to auto-demotion (A3d).
5. **Rank clamped [100-900]** — `Anchor.clampRank()`.
6. **Sim isolation via contextId** — Each run gets `sim-{uuid}`, cleaned up after.
7. **Persistence layer copied from impromptu** — re-packaged to `dev.dunnam.diceanchors.persistence`.
8. **Scene-setting turn 0** — When `scenario.setting()` is non-blank and extraction is enabled, `SimulationService` executes an ESTABLISH turn before turn 1. The DM narrates the setting; DICE extraction captures initial propositions. This gives the anchor framework material to stabilize before stress-test contradiction turns begin. Skipped if setting is blank or extraction is disabled.
9. **Drift evaluator: epistemic hedging = NOT_MENTIONED** — The drift evaluation prompt distinguishes three DM response categories: (1) contradiction (asserts the opposite), (2) world progression (narrative change that isn't a contradiction), (3) epistemic hedging (declines to affirm without asserting the opposite). Hedging is classified as NOT_MENTIONED, not CONTRADICTED. The player message is included in the evaluator prompt so the evaluator can distinguish defensive resistance from genuine forgetting.
10. **No seed anchors required** — Scenarios may have no seed anchors. The expected flow is that scene-setting turn 0 + warm-up turns allow the anchor framework to accumulate propositions organically before stress-test contradiction turns.
11. **Composite conflict detection** — `CompositeConflictDetector` chains multiple strategies (LLM semantic + negation lexical). Strategy selection is configurable via `ConflictDetectionStrategy`.
12. **Trust pipeline** — `TrustPipeline` evaluates propositions through multiple trust signals (source authority, extraction confidence, reinforcement history) before promotion. `TrustAuditRecord` captures the decision trail.
13. **Context compaction** — `CompactedContextProvider` summarizes older context when token thresholds are exceeded. `CompactionValidator` ensures protected facts survive compaction.
14. **Simulation assertions** — Post-run validation via `sim/assertions/` package. Nine assertion types (anchor-count, rank-distribution, trust-score-range, promotion-zone, authority-at-most, kg-context-contains, kg-context-empty, no-canon-auto-assigned, compaction-integrity) declared in scenario YAML.
15. **Memory tiers** — `MemoryTier` classifies propositions as `COLD`, `WARM`, or `HOT`. Tier influences decay rates and eviction priority.
16. **Invariant rules** — `InvariantEvaluator` checks propositions against domain-specific invariant rules provided by `InvariantRuleProvider`. Violations can block promotion or trigger alerts.
17. **Canonization gating** — `CanonizationGate` controls CANON authority assignment. CANON is never auto-assigned; requires explicit operator action through the gate.

## OpenSpec (Spec-Driven Development)

dice-anchors uses [OpenSpec](https://github.com/Fission-AI/OpenSpec) for structured specification management. All specs live in `openspec/` and follow the project [constitution](openspec/constitution.md).

### OpenSpec Workflow

1. **Plan a roadmap**: Use `/opsx:roadmap` to create sequenced feature docs from a high-level initiative
2. **New feature/change**: Use `/opsx:new` to create a change with proposal -> spec -> design -> tasks
3. **Continue change**: Use `/opsx:continue` to create the next artifact
4. **Fast-forward**: Use `/opsx:ff` to generate all artifacts at once
5. **Implement**: Use `/opsx:apply` to work through tasks
6. **Verify**: Use `/opsx:verify` to validate implementation matches spec
7. **Archive**: Use `/opsx:archive` to finalize completed changes

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
- **Core logic must not reference simulation concepts.** `CanonizationGate` had `autoApproveInSimulation` that sniffed for `"sim-*"` context IDs — a layer violation. Core anchor logic (`anchor/`, `assembly/`, etc.) knows nothing about simulations. Config flags must be named for what they do generally (`autoApprovePromotions`), not for which caller uses them. Dependencies flow one way: `sim/` → core, never core → sim.
