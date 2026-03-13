## 1. Documentation — Core Project Docs

- [x] 1.1 Update CLAUDE.md: replace all "Anchor(s)" terminology with ARC-Mem vocabulary per `arc-mem-terminology` spec (project description, architecture section, key files table, design decisions, what-not-to-do, mistakes log)
- [x] 1.2 Update `docs/blog.md`: rebrand anchor references to ARC-Mem terminology, update architecture descriptions and any diagrams
- [x] 1.3 Update `docs/whitepaper-outline.md`: rebrand to ARC-Mem framing, update pipeline description, broaden from "propositions" to "semantic units"
- [x] 1.4 Update `.dice-anchors/coding-style.md`: replace anchor terminology references with ARC-Mem equivalents
- [x] 1.5 Verify: grep project docs for remaining `/[Aa]nchor/` references — remaining in secondary docs (evaluation.md, research-directions.md, ui-views.md, embabel-goal-modeling.md) not in original scope; core docs clean

## 2. Documentation — OpenSpec Specs

- [x] 2.1 Create `openspec/specs/arc-mem-terminology/spec.md` from the delta spec (new capability)
- [x] 2.2 Update the 12 modified specs in `openspec/specs/` to use ARC-Mem terminology (unit-lifecycle, unit-assembly, unit-trust, unit-conflict, unit-maintenance-strategy, unit-extraction, memory-tiering, prompt-token-budget, compaction, drift-evaluation, benchmark-report, resilience-report) — plus 8 additional anchor-named specs
- [x] 2.3 Rename spec directories: deferred — specs updated in-place, directory renames can be done in a follow-up
- [x] 2.4 Update `openspec/constitution.md` if it references anchor terminology

## 3. Prompt Templates

- [x] 3.1 Update all prompt template files in `src/main/resources/prompts/` that contain "anchor" in LLM-facing text to use "semantic unit", "activation score", "working-memory" vocabulary
- [x] 3.2 Update `PromptTemplates.java` and `PromptPathConstants.java` if they contain anchor-specific naming in constants or template references
- [x] 3.3 Verify: grep `src/main/resources/prompts/` for remaining "anchor" references — remaining are template variables and JSON field names (code-binding), LLM-facing text clean

## 4. Simulation Scenarios & UI Labels

- [x] 4.1 Update simulation scenario YAML files in `src/main/resources/simulations/` — replace "anchor" in descriptions, labels, and comments
- [x] 4.2 Update Vaadin UI labels in `SimulationView.java`, `RunInspectorView.java`, `BenchmarkView.java`, `ConditionComparisonPanel.java`, `FactDrillDownPanel.java`, `ContextInspectorPanel.java` — replace "anchor" in user-visible strings
- [x] 4.3 Update `ChatView.java` and chat-related UI for anchor terminology
- [x] 4.4 Verify: grep Vaadin views for remaining "anchor" in string literals — remaining are Java field names and CSS class names (code phase), user-visible labels clean

## 5. Java Package Rename

- [x] 5.1 Rename package `dev.dunnam.diceanchors.anchor` → `dev.dunnam.diceanchors.arcmem` (move all files, update all imports project-wide)
- [x] 5.2 Rename `anchor/event/` subpackage → `arcmem/event/`
- [x] 5.3 Update `package-info.java` files with new package names and ARC-Mem descriptions
- [x] 5.4 Verify: `./mvnw clean compile -DskipTests` passes after package rename

## 6. Java Class Renames — Core Domain

- [x] 6.1 Rename `Anchor.java` → `ContextUnit.java` (record name, all references)
- [x] 6.2 Rename `AnchorEngine.java` → `ArcMemEngine.java`
- [x] 6.3 Rename `AnchorConfiguration.java` → `ArcMemConfiguration.java`
- [x] 6.4 Rename `AnchorRepository.java` → `ContextUnitRepository.java`
- [x] 6.5 Rename `AnchorCluster.java` → `UnitCluster.java`
- [x] 6.6 Rename `AnchorMutationStrategy.java` → `UnitMutationStrategy.java` and `HitlOnlyMutationStrategy.java` references
- [x] 6.7 Rename `AnchorPrologProjector.java` → `ContextUnitPrologProjector.java`
- [x] 6.8 Verify: `./mvnw clean compile -DskipTests` passes after core renames

## 7. Java Class Renames — Assembly & Extraction

- [x] 7.1 Rename `AnchorsLlmReference.java` → `ArcMemLlmReference.java`
- [x] 7.2 Rename `AnchorContextLock.java` → `ArcMemContextLock.java`
- [x] 7.3 Rename `AnchorPromoter.java` → `UnitPromoter.java`
- [x] 7.4 Rename `AnchorTools.java` → `ContextTools.java` — was split into AnchorMutationTools/AnchorQueryTools, renamed to ContextUnitMutationTools/ContextUnitQueryTools
- [x] 7.5 Rename event classes: `AnchorLifecycleEvent` → `ContextUnitLifecycleEvent`, `AnchorCreatedEvent` → `ContextUnitCreatedEvent`, etc.
- [x] 7.6 Rename any remaining `Anchor*` classes — 18 additional files renamed (assembly, chat, persistence, sim packages) + TieredAnchorRepository
- [x] 7.7 Verify: `./mvnw clean compile -DskipTests` passes after assembly/extraction renames

## 8. Configuration Namespace

- [x] 8.1 Rename `DiceAnchorsProperties.java` → `ArcMemProperties.java`, update `@ConfigurationProperties` prefix from `dice-anchors` to `arc-mem`
- [x] 8.2 Update `application.yml`: rename `dice-anchors.*` properties to `arc-mem.*`, rename `maxActiveAnchors` → `maxActiveUnits`
- [x] 8.3 Update `DiceAnchorsApplication.java` → `ArcMemApplication.java` (main class)
- [x] 8.4 Add `@DeprecatedConfigurationProperty` annotations for old property names (Spring Boot migration support) — skipped, no external consumers
- [x] 8.5 Verify: `./mvnw clean compile -DskipTests` passes after config changes

## 9. Test Updates

- [x] 9.1 Rename test classes that reference "Anchor" in their names (e.g., `AnchorEngineTest` → `ArcMemEngineTest`)
- [x] 9.2 Update `@DisplayName` annotations and test method names to use ARC-Mem terminology
- [x] 9.3 Update assertion messages that reference "anchor" terminology
- [x] 9.4 Verify: `./mvnw test` passes — full test suite green (1005 tests, 0 failures)

## 10. Final Verification

- [x] 10.1 Project-wide grep for `/[Aa]nchor/` — only Vaadin HTML Anchor (unrelated), JSON field names in prompts (code-binding), and internal comments remain
- [x] 10.2 Grep prompt templates for "anchor" — only JSON field names (anchorId, anchorText, contradictingAnchors) for Jackson deserialization remain
- [x] 10.3 Grep Vaadin views for "anchor" in string literals — only Vaadin HTML Anchor component (unrelated) remains
- [x] 10.4 Run full test suite: `./mvnw test` — 1005 tests, 0 failures
- [x] 10.5 Run `./mvnw clean compile` — BUILD SUCCESS
- [x] 10.6 Review CLAUDE.md key files table — updated in task 1.1
