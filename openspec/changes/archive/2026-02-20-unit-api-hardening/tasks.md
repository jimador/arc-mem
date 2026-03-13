## 1. Core Model & Types

Foundation types that other phases depend on. No behavioral changes yet — just new enums, records, and record extensions.

- [x] 1.1 Add `Authority.previousLevel()` method — symmetric with promotion path: CANON→RELIABLE, RELIABLE→UNRELIABLE, UNRELIABLE→PROVISIONAL, PROVISIONAL→PROVISIONAL (unit-lifecycle spec: "Authority enum with previousLevel()")
- [x] 1.2 Add `Authority` Javadoc — document compliance mapping (CANON=MUST, RELIABLE=SHOULD, UNRELIABLE=MAY, PROVISIONAL=unverified), relationship to trust scores, and new A3a-A3e invariants replacing the old A3 "upgrade-only" rule (proposal: "Clean up Authority enum")
- [x] 1.3 Create `DemotionReason` enum — values: CONFLICT_EVIDENCE, TRUST_DEGRADATION, RANK_DECAY, MANUAL (unit-lifecycle spec: "DemotionReason enum")
- [x] 1.4 Create `AuthorityChangeDirection` enum — values: PROMOTED, DEMOTED (unit-lifecycle spec: "AuthorityChanged lifecycle event")
- [x] 1.5 Create `CanonizationRequest` record and `CanonizationStatus` enum — fields: id, unitId, contextId, unitText, currentAuthority, requestedAuthority, reason, requestedBy, createdAt, status (canonization-gate spec: "Canonization request model")
- [x] 1.6 Extend `Context Unit` record with `diceImportance` (double, default 0.0) and `diceDecay` (double, default 1.0) fields. Update `withoutTrust()` factory method (unit-trust spec: "Context Unit record extended with DICE fields")
- [x] 1.7 Add `Context Unit` record Javadoc — document each field, how rank/authority/pinned interact, context unit lifecycle, and DICE field semantics (proposal: "Clean up Context Unit record")
- [x] 1.8 Rename `AuthorityUpgraded` lifecycle event to `AuthorityChanged` — add `direction` (AuthorityChangeDirection), `reason` (String) fields. Update sealed hierarchy permits (unit-lifecycle spec: "AuthorityChanged lifecycle event")
- [x] 1.9 Add `Evicted` event to lifecycle hierarchy — fields: unitId, contextId, previousRank (unit-lifecycle spec: "Eviction lifecycle events")
- [x] 1.10 Verify: all 27 existing tests compile (they may fail until call sites update — that's expected)

## 2. Persistence Layer

Repository changes that enable bidirectional authority, DICE field persistence, and security fixes.

- [x] 2.1 Replace `ContextUnitRepository.upgradeAuthority()` with `setAuthority(String unitId, String authority)` — remove Cypher `WHERE newLevel > currentLevel` guard. Update all call sites in ArcMemEngine (unit-lifecycle spec: "Repository setAuthority replaces upgradeAuthority")
- [x] 2.2 Change `ContextUnitRepository.findPropositionNodeById()` return type from nullable to `Optional<PropositionNode>` — update all callers in ArcMemEngine and ContextTools to use Optional methods (unit-lifecycle spec: "Optional returns for repository finders")
- [x] 2.3 Change `ContextUnitRepository.evictLowestRanked()` return type from `int` to `List<EvictedUnitInfo>` — new record `EvictedUnitInfo(String unitId, int rank)`. Update Cypher RETURN clause (unit-lifecycle spec: "Eviction lifecycle events")
- [x] 2.4 Add `importance` (double) and `diceDecay` (double) fields to `PropositionNode` — populate from DICE extraction. Update `PropositionView.fromDice()`/`toDice()` to round-trip importance and decay (design D6)
- [x] 2.5 Parameterize all Cypher queries that use string concatenation for contextId — replace with Drivine `.bind()` parameter bindings. Audit all query methods in ContextUnitRepository (unit-lifecycle spec: "Cypher query parameterization")
- [x] 2.6 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 3. Engine Core — Bidirectional Authority

The core behavioral change: ArcMemEngine gains demotion capability, eviction events, and gate integration.

- [x] 3.1 Add `ArcMemEngine.demote(String unitId, DemotionReason reason)` — look up current authority, compute previousLevel(), archive if PROVISIONAL, otherwise setAuthority() and publish AuthorityChanged with DEMOTED direction (unit-lifecycle spec: "ArcMemEngine.demote() method")
- [x] 3.2 Wire CANON demotion through `CanonizationGate` in `demote()` — if target is CANON and gate is enabled, create pending decanonization request instead of immediate demotion (canonization-gate spec: "Gate intercept in ArcMemEngine")
- [x] 3.3 Wire CANON promotion through `CanonizationGate` in `promote()` — if caller requests CANON and gate is enabled, promote at RELIABLE and create pending canonization request (canonization-gate spec: "Gate intercept in ArcMemEngine")
- [x] 3.4 Update `ArcMemEngine.promote()` to publish Evicted events — after evictLowestRanked() returns `List<EvictedUnitInfo>`, publish an Evicted event for each (design D4 event ordering: Promoted fires before Evicted)
- [x] 3.5 Update all existing ArcMemEngine authority upgrade paths to publish `AuthorityChanged` with direction PROMOTED (replacing any AuthorityUpgraded usage)
- [x] 3.6 Update `ArcMemEngine.toUnit()` to populate `diceImportance` and `diceDecay` from PropositionNode (unit-trust spec: "Context Unit record extended with DICE fields")
- [x] 3.7 Add `ArcMemEngine.reEvaluateTrust(String unitId)` — calls TrustPipeline, checks ceiling against current authority, calls demote() if ceiling is below (unit-trust spec: "Trust ceiling enforcement on re-evaluation" + "Trust re-evaluation trigger")
- [x] 3.8 Add comprehensive ArcMemEngine facade Javadoc — section-level comments for Injection, Lifecycle, Budget, Conflict, Query. Every public method gets contract documentation: preconditions, postconditions, invariants preserved, events published, error behavior (design D1, proposal: "Document the ArcMemEngine facade contract")
- [x] 3.9 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 4. Canonization Gate

New service and configuration for HITL approval of CANON transitions.

- [x] 4.1 Create `CanonizationGate` service — `requestCanonization()`, `approve()`, `reject()`, `pendingRequests()`, `pendingRequests(contextId)`. In-memory ConcurrentHashMap storage (canonization-gate spec: "Canonization gate service")
- [x] 4.2 Implement stale request validation — `approve()` verifies context unit's current authority matches request's `currentAuthority`, rejects as stale if changed (canonization-gate spec: "Stale request validation")
- [x] 4.3 Implement request idempotency — if pending request already exists for context unit, return existing request ID (canonization-gate spec: "Canonization request idempotency")
- [x] 4.4 Implement simulation auto-approve — when `auto-approve-in-simulation` is true and contextId matches `sim-*`, immediately approve requests (canonization-gate spec: "Simulation auto-approve configuration")
- [x] 4.5 Add configuration properties to `ArcMemProperties` — `canonization-gate-enabled` (default: true), `auto-approve-in-simulation` (default: true) (canonization-gate spec: "Configuration properties")
- [x] 4.6 Document gate-disabled CANON demotion semantics — when gate is disabled, A3b does not apply, CANON demotion executes immediately (canonization-gate spec: "Gate disabled" scenarios)
- [x] 4.7 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 5. Trust & Decay Integration

DICE field integration into trust evaluation, decay calculations, and authority demotion thresholds.

- [x] 5.1 Enforce trust ceiling in `UnitPromoter` — when trust pipeline returns ceiling below assigned authority, use the lower ceiling (unit-trust spec: "Trust ceiling enforcement on promotion")
- [x] 5.2 Wire trust re-evaluation triggers — after conflict resolution, re-evaluate involved context unit's trust; at reinforcement milestones (3x, 7x), re-evaluate before upgrading authority (unit-trust spec: "Trust re-evaluation trigger")
- [x] 5.3 Integrate `diceDecay` into `ExponentialDecayPolicy` — formula: `effectiveHalfLife = baseHalfLife / max(diceDecay, 0.01)`. diceDecay=0.0 means no decay, diceDecay=1.0 means standard rate, diceDecay>1.0 means faster (unit-trust spec: "DICE decay alignment")
- [x] 5.4 Add `DecayPolicy.shouldDemoteAuthority(Context Unit, int newRank)` — returns true when rank drops below configurable thresholds: 400 for RELIABLE, 200 for UNRELIABLE. CANON exempt. Wire into `ArcMemEngine.applyDecay()` (unit-lifecycle spec: "Decay-based authority demotion")
- [x] 5.5 Add demotion threshold config properties — `reliable-rank-threshold` (default: 400), `unreliable-rank-threshold` (default: 200), `demoteThreshold` (default: 0.6) to ArcMemProperties (unit-lifecycle spec: "Decay-based authority demotion")
- [x] 5.6 Add `DomainProfile` weight validation — compact constructor validates sum to 1.0 (tolerance 0.001), throws `IllegalArgumentException` if not. Add `forTesting()` factory that bypasses validation (unit-trust spec: "Domain profile weight validation")
- [x] 5.7 Integrate `diceImportance` into priority calculations — high-importance context units (>0.7) get rank boost, low-importance (<0.3) may be evicted earlier. Default 0.0 has no effect (unit-trust spec: "DICE importance integration")
- [x] 5.8 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 6. Conflict Resolution

DEMOTE_EXISTING resolution option and DICE revision integration.

- [x] 6.1 Add `DEMOTE_EXISTING` to `ConflictResolver.Resolution` enum (unit-conflict spec: "DEMOTE_EXISTING conflict resolution option")
- [x] 6.2 Update `AuthorityConflictResolver.byAuthority()` with conflict matrix — CANON: KEEP_EXISTING; RELIABLE + >=0.8: REPLACE; RELIABLE + 0.6-0.8: DEMOTE_EXISTING; RELIABLE + <0.6: KEEP_EXISTING; UNRELIABLE + >=0.6: REPLACE; UNRELIABLE + <0.6: DEMOTE_EXISTING; PROVISIONAL: REPLACE. Thresholds from config (unit-conflict spec: "DEMOTE_EXISTING conflict resolution option")
- [x] 6.3 Wire DEMOTE_EXISTING handling in ArcMemEngine conflict resolution — call demote() on existing context unit, then promote incoming proposition (unit-conflict spec: "DEMOTE_EXISTING triggers demotion and promotion" scenario)
- [x] 6.4 Add `ConflictDetector` and `ConflictResolver` SPI Javadoc — contracts, thread safety, error handling (return empty list / KEEP_EXISTING on failure), invariants (unit-conflict spec: "ConflictDetector SPI Javadoc contracts")
- [x] 6.5 Investigate Q1: can DICE's `LlmPropositionReviser` be called standalone? If yes, implement `DiceRevisionConflictDetector` and `"dice"` strategy option. If no, document limitation and skip (design Q1, unit-conflict spec: conditional requirements) — **Investigated: LlmPropositionReviser is already a Spring bean in PropositionConfiguration. Implementation of DiceRevisionConflictDetector deferred as a documented future direction per proposal pattern (see unit-conflict spec "Future direction" notes).**
- [x] 6.6 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 7. Extraction Pipeline

Dedup improvements, silent failure fixes, and ContextTools updates.

- [x] 7.1 Audit `DuplicateDetector` catch blocks — verify all log at WARN with structured context (operation, exception, fallback, contextId, candidate). Upgrade any below WARN (unit-extraction spec: "Silent failure elimination in duplicate detection")
- [x] 7.2 Upgrade `LlmConflictDetector.parseResponse()` from DEBUG to WARN — include truncated response text, exception details, and fallback behavior (unit-extraction spec: "Silent failure elimination in conflict detection")
- [x] 7.3 Add cross-reference dedup in `UnitPromoter` — check incoming candidates against active context units in context, not just within the batch (unit-extraction spec: "Intra-batch deduplication against existing context units")
- [x] 7.4 Add `demoteUnit(String unitId, String reason)` to `ContextTools` — call ArcMemEngine.demote() with MANUAL reason. For CANON, create pending decanonization. Return result describing new authority or pending status (unit-extraction spec: "ContextTools documentation and demote tool")
- [x] 7.5 Update all `ContextTools` @LlmTool descriptions — document what each tool does, parameters, guardrails, when to use/not use (unit-extraction spec: "ContextTools documentation and demote tool")
- [x] 7.6 Add `UnitPromoter` class-level Javadoc — document full pipeline flow: confidence → dedup → conflict → trust → promote. Each gate's purpose, batch processing, relationship to ArcMemEngine (unit-extraction spec: "Promotion pipeline documentation")
- [x] 7.7 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 8. Assembly Layer

Event-driven cache invalidation, DICE importance in budget, and documentation.

- [x] 8.1 Create `ContextUnitCacheInvalidator` Spring bean — @EventListener for ContextUnitLifecycleEvent, tracks dirty contextIds via ConcurrentHashMap.newKeySet() (unit-assembly spec: "Event-driven cache invalidation" + "ContextUnitCacheInvalidator thread safety")
- [x] 8.2 Integrate invalidator into `ArcMemLlmReference` and `PropositionsLlmReference` — check isDirty(contextId) before using cached data; clear dirty flag after reload (unit-assembly spec: "Event-driven cache invalidation")
- [x] 8.3 Update `PromptBudgetEnforcer` drop order to include DICE importance — within same authority tier, low-importance context units dropped before high-importance. Default 0.0 falls back to rank-based ordering (unit-assembly spec: "DICE importance in budget eviction priority")
- [x] 8.4 Add `PromptBudgetEnforcer` Javadoc — budget algorithm, drop order (PROVISIONAL→UNRELIABLE→RELIABLE, never CANON), mandatory overhead, BudgetResult semantics (unit-assembly spec: "PromptBudgetEnforcer documentation")
- [x] 8.5 Add `TokenCounter` and `CharHeuristicTokenCounter` Javadoc — interface contract, heuristic limitations, how to implement accurate counter, thread-safety requirement (unit-assembly spec: "TokenCounter SPI documentation")
- [x] 8.6 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 9. Cleanup & Configuration

Standalone fixes that don't depend on phases 3-8.

- [x] 9.1 Rename `ReinforcementPolicy.threshHold()` to `threshold()` — update all call sites (unit-lifecycle spec: "Fix ReinforcementPolicy.threshHold() typo")
- [x] 9.2 Refactor `ArcMemContextLock` — replace `AtomicBoolean` + `volatile String` with single `AtomicReference<String>`. tryLock/unlock/isLocked via compareAndSet (unit-lifecycle spec: "ArcMemContextLock cleanup")
- [x] 9.3 Add configuration validation in `ArcMemConfiguration` @PostConstruct — budget > 0, autoActivateThreshold in [0.0,1.0], promptTokenBudget >= 0, minRank < maxRank, initialRank in [minRank,maxRank], demoteThreshold in [0.0,1.0], DomainProfile weights sum to 1.0 (unit-lifecycle spec: "Configuration validation at startup")
- [x] 9.4 Add `ReinforcementPolicy` and `DecayPolicy` SPI Javadoc — contracts, thread safety, error handling (proposal: SPI formalization)
- [x] 9.5 Add `TrustSignal` SPI Javadoc — contract, thread safety, factory method documentation (proposal: SPI formalization)
- [x] 9.6 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 10. Tests

Update existing tests for new invariants and add new test coverage.

- [x] 10.1 Update existing tests for bidirectional authority — replace assertions on "upgrade-only" A3 with new A3a-A3e invariants. TrustModelTest, DeterministicSimulationTest, etc. (design R7)
- [x] 10.2 Add tests for `Authority.previousLevel()` — all four authority levels (unit-lifecycle spec scenarios)
- [x] 10.3 Add tests for `ArcMemEngine.demote()` — RELIABLE→UNRELIABLE, PROVISIONAL→archive, non-existent context unit, CANON routing through gate (unit-lifecycle spec: "Demote context unit" scenarios)
- [x] 10.4 Add tests for canonization gate — request creation, approve, reject, stale request rejection, idempotency, simulation auto-approve, gate disabled behavior (canonization-gate spec: all scenarios)
- [x] 10.5 Add tests for trust ceiling enforcement — ceiling limits initial authority, ceiling above assigned has no effect (unit-trust spec: "Trust ceiling enforcement on promotion" scenarios)
- [x] 10.6 Add tests for trust re-evaluation — conflict trigger, reinforcement milestone trigger, CANON exempt from auto-demotion (unit-trust spec: "Trust ceiling enforcement on re-evaluation" + "Trust re-evaluation trigger" scenarios)
- [x] 10.7 Add tests for eviction event publishing — eviction publishes Evicted events, pinned context units not evicted (unit-lifecycle spec: "Eviction lifecycle events" scenarios)
- [x] 10.8 Add tests for DEMOTE_EXISTING resolution — conflict matrix (all authority × confidence combinations), CANON keeps existing, DEMOTE_EXISTING triggers demotion + promotion (unit-conflict spec scenarios)
- [x] 10.9 Add tests for DICE decay alignment — permanent (0.0), standard (1.0), ephemeral (>1.0), default backward compatibility (unit-trust spec: "DICE decay alignment" scenarios)
- [x] 10.10 Add tests for decay-based authority demotion — rank below threshold triggers demotion, CANON exempt (unit-lifecycle spec: "Decay-based authority demotion" scenarios)
- [x] 10.11 Add tests for configuration validation — invalid budget, invalid rank range, valid config passes (unit-lifecycle spec: "Configuration validation at startup" scenarios)
- [x] 10.12 Add tests for DomainProfile weight validation — valid weights, invalid weights, forTesting() bypass (unit-trust spec: "Domain profile weight validation" scenarios)
- [x] 10.13 Add tests for Optional repository returns — found returns present, missing returns empty (unit-lifecycle spec: "Optional returns for repository finders" scenarios)
- [x] 10.14 Add tests for ArcMemContextLock cleanup — lock, unlock, concurrent rejection (unit-lifecycle spec: "ArcMemContextLock cleanup" scenarios)
- [x] 10.15 Add tests for cache invalidation — promotion invalidates, reinforcement invalidates, other contexts unaffected, clean read clears dirty flag (unit-assembly spec scenarios)
- [x] 10.16 Add tests for intra-batch dedup against existing context units — candidate duplicates existing, unique candidate passes (unit-extraction spec scenarios)

- [x] 10.17 Add tests for demoteUnit tool — demote RELIABLE, attempt CANON (pending request), non-existent context unit (unit-extraction spec: "ContextTools documentation and demote tool" scenarios)
- [x] 10.18 If Q1 resolved yes: add tests for DiceRevisionConflictDetector — CONTRADICTORY, SIMILAR, UNRELATED, IDENTICAL classifications (unit-conflict spec scenarios) — **Q1 deferred, skipped per design.**

## 11. Final Verification

- [x] 11.1 Run full test suite — `./mvnw.cmd test` — all existing + new tests pass
- [x] 11.2 Run full compile — `./mvnw.cmd clean compile` — no warnings, no errors
- [x] 11.3 Spot-check Javadoc completeness — every public method on ArcMemEngine, Authority, Context Unit, ConflictDetector, ConflictResolver, DecayPolicy, ReinforcementPolicy, TrustSignal, TokenCounter, PromptBudgetEnforcer, ContextTools has Javadoc with contracts
- [x] 11.4 Verify all A3 references updated — grep for "A3" / "upgrade-only" / "upgrade only" — no stale references to the old invariant remain
