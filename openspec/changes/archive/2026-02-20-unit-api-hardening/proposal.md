## Why

The context units codebase demonstrates a working adversarial drift resistance system, but the current implementation has grown organically around a proof-of-concept. Critical computed values go unused (trust authority ceiling), failures are silently swallowed, the DICE integration remains shallow -- using only extraction while ignoring DICE's revision classifications, importance scores, and memory maintenance capabilities -- and the Context Unit API surface lacks the documentation and contracts needed for others to understand and extend it.

The system works. The goal now is to make it **clean, clear, and well-documented** -- code that reads like a well-written explanation of what Context Units are: the **attention memory layer** for DICE. A structured, prioritized, security-enforcing working memory that gives the LLM maximum freedom to operate within well-defined boundaries.

This change prioritizes **clarity over cleverness**. Where an abstraction would improve understanding, we introduce it. Where it would add noise, we document the opportunity and move on.

## What Changes

### Core Model & Engine (Priority: High)

- **Clean up `ArcMemEngine` internal structure** -- organize methods into clear logical sections (lifecycle, budget, queries) with section-level Javadoc. The engine is monolithic but not unmanageably large; splitting into 3 services would scatter related logic and force consumers to know which service to call. Instead: clear internal organization, comprehensive Javadoc on every public method, and documented invariants.
- **Enforce trust authority ceiling on promotion**: `TrustScore.authorityCeiling` is computed today but never applied. Promotion MUST respect the ceiling (a proposition scoring 0.45 trust MUST NOT promote above PROVISIONAL). This is a correctness fix, not a new feature.
- **Publish lifecycle events for budget eviction** (currently silent). Eviction is a significant state change that callers cannot observe today.
- **Document the ArcMemEngine facade contract**: Javadoc on every public method specifying preconditions, postconditions, invariants preserved, events published, and error behavior. This is the primary API surface and MUST be self-documenting.

> **Future direction**: If ArcMemEngine grows beyond ~400 lines or gains new responsibility categories, decompose into focused services (ContextUnitLifecycleService, ContextUnitBudgetManager, ContextUnitQueryService). Document this threshold in the class Javadoc.

### Context Unit API & SPI Contracts (Priority: High)

- **Formalize existing SPI interfaces** with comprehensive Javadoc: `ConflictDetector`, `ConflictResolver`, `ReinforcementPolicy`, `DecayPolicy`, and `TrustSignal` already exist as interfaces but lack documented contracts. Add explicit invariant specifications, error handling requirements (what to do on failure), thread-safety guarantees, and usage examples.
- **Clean up `Context Unit` record**: Add Javadoc explaining what each field means, how rank/authority/pinned interact, and the lifecycle an context unit goes through. The record is the conceptual heart of the system and should read like documentation.
- **Clean up `Authority` enum**: Document the upgrade-only invariant, the relationship to trust scores, and the compliance mapping (CANON=MUST, RELIABLE=SHOULD, etc.) directly in the enum Javadoc.

> **Future direction**: `UnitMemoryProvider` SPI -- a pluggable contract for how context units are surfaced to LLM context, supporting both eager injection (system prompt) and on-demand retrieval (tool-based search). This mirrors MemGPT/Letta's two-tier memory model and DICE's `Memory` class pattern. Document this as the natural next step when the system needs to support multiple retrieval strategies.

> **Future direction**: `UnitBlock` abstraction -- named, categorized context unit groups with per-block token budgets (e.g., `safety-constraints`, `session-facts`, `world-state`). Inspired by MemGPT/Letta's memory blocks. This is the right abstraction when context unit counts grow beyond what a flat ranked list can manage clearly. Document the concept in the assembly layer Javadoc.

### DICE/Embabel Integration (Priority: High)

- **Integrate DICE's revision classifications** (IDENTICAL, SIMILAR, CONTRADICTORY, GENERALIZES, UNRELATED) into the conflict detection and duplicate detection pipelines. The current `NegationConflictDetector` does lexical negation matching; DICE's reviser already classifies proposition relationships semantically. Use what DICE gives us rather than reimplementing it.
- **Use DICE's importance score** alongside rank for context unit priority decisions. A high-importance, low-confidence context unit should remain in attention for verification rather than being silently evicted. The importance signal exists in DICE propositions but is currently ignored after extraction.
- **Align decay with DICE's effective confidence**: DICE propositions carry a `decay` field (0.0=permanent, 1.0=ephemeral) that should inform activation score decay calculations. Currently `ExponentialDecayPolicy` uses a fixed half-life independent of DICE's decay signal.
- **Document the integration architecture**: A clear Javadoc package-info or architecture doc explaining how DICE propositions flow into context units, what DICE APIs are used, and where the boundary between DICE responsibility and context unit responsibility lies.

### Assembly & Prompt Engineering (Priority: Medium)

- **Event-driven cache invalidation**: Replace manual `refresh()` on `ArcMemLlmReference` with Spring event listeners that invalidate cached content when context unit lifecycle events fire. This eliminates a class of staleness bugs.
- **Clean up `PromptBudgetEnforcer`**: Document the budget algorithm clearly, add Javadoc explaining the drop order (PROVISIONAL first, then UNRELIABLE, then RELIABLE; never CANON), and make the mandatory overhead calculation transparent.
- **Clean up `TokenCounter` SPI**: Document that `CharHeuristicTokenCounter` (4 chars/token) is a rough heuristic and how to implement a more accurate counter.

> **Future direction**: Positional-aware prompt assembly -- placing CANON/RELIABLE context units at prompt start (primacy effect) and safety-critical context units at prompt end (mitigating "lost in the middle" attention degradation). Research supports this but implementing it requires deeper control over Embabel's prompt rendering pipeline. Document the research and the integration point.

> **Future direction**: Memory pressure monitoring -- signaling when token usage approaches budget threshold to enable proactive demotion or summarization. Document the concept and the trigger points in PromptBudgetEnforcer.

### Implementation Hardening (Priority: High)

- **Eliminate silent LLM failure swallowing**: All fallback paths MUST log at WARN level with structured context. `DuplicateDetector` and `LlmConflictDetector` currently return permissive defaults on exception without adequate logging. This is a correctness and debuggability issue.
- **Replace null-returning repository finders with `Optional<T>`**: `findPropositionNodeById()` returns null today; callers must null-check. `Optional` makes the possibility of absence explicit in the type system.
- **Add configuration validation**: budget > 0, thresholds in [0.0, 1.0], signal weights sum to 1.0. Current `ArcMemProperties` accepts invalid configurations silently. Use `@Validated` or a startup check.
- **Fix `ArcMemContextLock` concurrency**: Uses `AtomicBoolean` + `volatile String`; the volatile is redundant with AtomicBoolean. Clean up to use `AtomicReference<String>` for both locked state and owner.

### Extraction Pipeline (Priority: Medium)

- **Clean up `UnitPromoter` pipeline documentation**: The multi-gate pipeline (confidence -> dedup -> conflict -> trust -> promote) is well-designed but not documented as a cohesive flow. Add class-level Javadoc explaining the full pipeline, each gate's purpose, and what happens at each stage.
- **Improve intra-batch deduplication**: Current batch dedup filters within the incoming batch by text identity but doesn't check against existing context units. Cross-reference against active context units.
- **Clean up `ContextTools`**: Document each LLM-callable tool method with clear descriptions of what it does, its guardrails, and what the LLM should/shouldn't use it for.

> **Future direction**: Self-directed context unit management -- expanding LLM tools to allow the model to suggest promotions, flag outdated context units, and request reinforcement of validated facts. This mirrors MemGPT's insight that the LLM is often the best judge of information relevance. Requires careful guardrails (no CANON demotion, no authority bypass). Document the tool expansion roadmap in ContextTools Javadoc.

> **Future direction**: Context Unit provenance / chain of custody -- every context unit should carry a traceable history: where it was extracted from, how it earned its authority, what reinforced or challenged it, and who approved its canonization. User trust in an agentic system requires the user to understand _why the system believes what it believes_. The hardening work in this change (typed lifecycle events, `AuthorityChanged` with direction, `DemotionReason`, `CanonizationRequest` records) creates the event infrastructure needed to build provenance. A future `UnitProvenanceService` subscribes to these events and builds an append-only audit trail per context unit, surfaced in both chat and simulation UIs. Neo4j's relationship model is a natural fit for the provenance graph. See design.md "Future Directions" for the full vision.

> **Known issue**: The `EntityMentionNetworkView` graph display in the simulation UI does not work correctly. Not in scope for this change, but it is the natural home for context unit provenance visualization once the event infrastructure is proven. See design.md for details.

### Testing (Priority: Medium)

- All 27 existing tests MUST continue passing (some updated for new invariants).
- Add tests for bidirectional authority transitions (promotion and demotion).
- Add tests for canonization gate (pending, approve, reject flows).
- Add tests for CANON demotion immunity to automatic triggers.
- Add tests for trust ceiling enforcement on promotion.
- Add tests for eviction event publishing.
- Add tests for DICE revision classification integration.
- Add tests for configuration validation.

## Capabilities

### New Capabilities

- `canonization-gate`: Human-in-the-loop approval gate for all CANON authority transitions (both canonization and decanonization). Simple pending-request queue with approve/reject, surfaced in chat and simulation UIs.

### Modified Capabilities

- `unit-lifecycle`: **BREAKING**: Replace upgrade-only authority model with bidirectional authority lifecycle (promotion AND demotion). Add `ArcMemEngine.demote()`. Replace `upgradeAuthority()` with `setAuthority()`. New invariants A3a-A3e replace old A3. Clean up ArcMemEngine organization and documentation; enforce trust ceiling on promotion; publish eviction events; structured error handling for all failure paths.
- `unit-conflict`: Integrate DICE revision classifications into conflict detection; improve Javadoc contracts on ConflictDetector/ConflictResolver SPIs.
- `unit-trust`: Enforce authority ceiling from TrustScore on promotion; integrate importance-aware priority alongside rank; validate domain profile weights at startup.
- `unit-extraction`: Improve promotion pipeline documentation; intra-batch dedup against existing context units; clean up ContextTools documentation; eliminate silent failure swallowing.
- `unit-assembly`: Event-driven cache invalidation; clean up PromptBudgetEnforcer documentation; DICE importance integration for budget decisions.

## Impact

**Code**: Focused changes across `context unit/` (ArcMemEngine cleanup, trust ceiling enforcement, eviction events), `assembly/` (event-driven cache, budget enforcer docs), `extract/` (pipeline docs, dedup improvement), `persistence/` (Optional returns), and `chat/` (tools docs). No major structural changes -- the package architecture stays the same.

**APIs**: Breaking changes in unit-lifecycle: `upgradeAuthority()` replaced by `setAuthority()`, `AuthorityUpgraded` event renamed to `AuthorityChanged`, `threshHold()` renamed to `threshold()`, `evictLowestRanked()` return type changed from `int` to `List<EvictedUnitInfo>`. New methods: `ArcMemEngine.demote()`, `ArcMemEngine.reEvaluateTrust()`. New types: `DemotionReason`, `AuthorityChangeDirection`, `CanonizationRequest`, `CanonizationGate`. Repository finders change from nullable to `Optional` (compile-error if callers don't update). SPI interfaces gain documentation and new methods (`DecayPolicy.shouldDemoteAuthority()`).

**Dependencies**: No new external dependencies. Deeper use of existing DICE APIs (revision classifications, importance, decay).

**Testing**: All 27 existing tests MUST continue passing (some updated for new invariants). ~15-20 new tests for bidirectional authority, canonization gate, trust ceiling, eviction events, DICE integration, and config validation.

**Configuration**: Existing configuration keys preserved with identical defaults. New validation on existing properties. New configuration properties: `canonization-gate-enabled` (default: true), `auto-approve-in-simulation` (default: true), `reliable-rank-threshold` (default: 400), `unreliable-rank-threshold` (default: 200), `demoteThreshold` (default: 0.6).

**Documentation**: Comprehensive Javadoc across all public APIs, SPI contracts, and integration points. Future direction notes embedded where abstractions were considered but deferred.
