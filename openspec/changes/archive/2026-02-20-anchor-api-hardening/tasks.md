## 1. Core Model & Types

Foundation types that other phases depend on. No behavioral changes yet — just new enums, records, and record extensions.

- [x] 1.1 Add `Authority.previousLevel()` method — symmetric with promotion path: CANON→RELIABLE, RELIABLE→UNRELIABLE, UNRELIABLE→PROVISIONAL, PROVISIONAL→PROVISIONAL (anchor-lifecycle spec: "Authority enum with previousLevel()")
- [x] 1.2 Add `Authority` Javadoc — document compliance mapping (CANON=MUST, RELIABLE=SHOULD, UNRELIABLE=MAY, PROVISIONAL=unverified), relationship to trust scores, and new A3a-A3e invariants replacing the old A3 "upgrade-only" rule (proposal: "Clean up Authority enum")
- [x] 1.3 Create `DemotionReason` enum — values: CONFLICT_EVIDENCE, TRUST_DEGRADATION, RANK_DECAY, MANUAL (anchor-lifecycle spec: "DemotionReason enum")
- [x] 1.4 Create `AuthorityChangeDirection` enum — values: PROMOTED, DEMOTED (anchor-lifecycle spec: "AuthorityChanged lifecycle event")
- [x] 1.5 Create `CanonizationRequest` record and `CanonizationStatus` enum — fields: id, anchorId, contextId, anchorText, currentAuthority, requestedAuthority, reason, requestedBy, createdAt, status (canonization-gate spec: "Canonization request model")
- [x] 1.6 Extend `Anchor` record with `diceImportance` (double, default 0.0) and `diceDecay` (double, default 1.0) fields. Update `withoutTrust()` factory method (anchor-trust spec: "Anchor record extended with DICE fields")
- [x] 1.7 Add `Anchor` record Javadoc — document each field, how rank/authority/pinned interact, anchor lifecycle, and DICE field semantics (proposal: "Clean up Anchor record")
- [x] 1.8 Rename `AuthorityUpgraded` lifecycle event to `AuthorityChanged` — add `direction` (AuthorityChangeDirection), `reason` (String) fields. Update sealed hierarchy permits (anchor-lifecycle spec: "AuthorityChanged lifecycle event")
- [x] 1.9 Add `Evicted` event to lifecycle hierarchy — fields: anchorId, contextId, previousRank (anchor-lifecycle spec: "Eviction lifecycle events")
- [x] 1.10 Verify: all 27 existing tests compile (they may fail until call sites update — that's expected)

## 2. Persistence Layer

Repository changes that enable bidirectional authority, DICE field persistence, and security fixes.

- [x] 2.1 Replace `AnchorRepository.upgradeAuthority()` with `setAuthority(String anchorId, String authority)` — remove Cypher `WHERE newLevel > currentLevel` guard. Update all call sites in AnchorEngine (anchor-lifecycle spec: "Repository setAuthority replaces upgradeAuthority")
- [x] 2.2 Change `AnchorRepository.findPropositionNodeById()` return type from nullable to `Optional<PropositionNode>` — update all callers in AnchorEngine and AnchorTools to use Optional methods (anchor-lifecycle spec: "Optional returns for repository finders")
- [x] 2.3 Change `AnchorRepository.evictLowestRanked()` return type from `int` to `List<EvictedAnchorInfo>` — new record `EvictedAnchorInfo(String anchorId, int rank)`. Update Cypher RETURN clause (anchor-lifecycle spec: "Eviction lifecycle events")
- [x] 2.4 Add `importance` (double) and `diceDecay` (double) fields to `PropositionNode` — populate from DICE extraction. Update `PropositionView.fromDice()`/`toDice()` to round-trip importance and decay (design D6)
- [x] 2.5 Parameterize all Cypher queries that use string concatenation for contextId — replace with Drivine `.bind()` parameter bindings. Audit all query methods in AnchorRepository (anchor-lifecycle spec: "Cypher query parameterization")
- [x] 2.6 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 3. Engine Core — Bidirectional Authority

The core behavioral change: AnchorEngine gains demotion capability, eviction events, and gate integration.

- [x] 3.1 Add `AnchorEngine.demote(String anchorId, DemotionReason reason)` — look up current authority, compute previousLevel(), archive if PROVISIONAL, otherwise setAuthority() and publish AuthorityChanged with DEMOTED direction (anchor-lifecycle spec: "AnchorEngine.demote() method")
- [x] 3.2 Wire CANON demotion through `CanonizationGate` in `demote()` — if target is CANON and gate is enabled, create pending decanonization request instead of immediate demotion (canonization-gate spec: "Gate intercept in AnchorEngine")
- [x] 3.3 Wire CANON promotion through `CanonizationGate` in `promote()` — if caller requests CANON and gate is enabled, promote at RELIABLE and create pending canonization request (canonization-gate spec: "Gate intercept in AnchorEngine")
- [x] 3.4 Update `AnchorEngine.promote()` to publish Evicted events — after evictLowestRanked() returns `List<EvictedAnchorInfo>`, publish an Evicted event for each (design D4 event ordering: Promoted fires before Evicted)
- [x] 3.5 Update all existing AnchorEngine authority upgrade paths to publish `AuthorityChanged` with direction PROMOTED (replacing any AuthorityUpgraded usage)
- [x] 3.6 Update `AnchorEngine.toAnchor()` to populate `diceImportance` and `diceDecay` from PropositionNode (anchor-trust spec: "Anchor record extended with DICE fields")
- [x] 3.7 Add `AnchorEngine.reEvaluateTrust(String anchorId)` — calls TrustPipeline, checks ceiling against current authority, calls demote() if ceiling is below (anchor-trust spec: "Trust ceiling enforcement on re-evaluation" + "Trust re-evaluation trigger")
- [x] 3.8 Add comprehensive AnchorEngine facade Javadoc — section-level comments for Injection, Lifecycle, Budget, Conflict, Query. Every public method gets contract documentation: preconditions, postconditions, invariants preserved, events published, error behavior (design D1, proposal: "Document the AnchorEngine facade contract")
- [x] 3.9 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 4. Canonization Gate

New service and configuration for HITL approval of CANON transitions.

- [x] 4.1 Create `CanonizationGate` service — `requestCanonization()`, `approve()`, `reject()`, `pendingRequests()`, `pendingRequests(contextId)`. In-memory ConcurrentHashMap storage (canonization-gate spec: "Canonization gate service")
- [x] 4.2 Implement stale request validation — `approve()` verifies anchor's current authority matches request's `currentAuthority`, rejects as stale if changed (canonization-gate spec: "Stale request validation")
- [x] 4.3 Implement request idempotency — if pending request already exists for anchor, return existing request ID (canonization-gate spec: "Canonization request idempotency")
- [x] 4.4 Implement simulation auto-approve — when `auto-approve-in-simulation` is true and contextId matches `sim-*`, immediately approve requests (canonization-gate spec: "Simulation auto-approve configuration")
- [x] 4.5 Add configuration properties to `DiceAnchorsProperties` — `canonization-gate-enabled` (default: true), `auto-approve-in-simulation` (default: true) (canonization-gate spec: "Configuration properties")
- [x] 4.6 Document gate-disabled CANON demotion semantics — when gate is disabled, A3b does not apply, CANON demotion executes immediately (canonization-gate spec: "Gate disabled" scenarios)
- [x] 4.7 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 5. Trust & Decay Integration

DICE field integration into trust evaluation, decay calculations, and authority demotion thresholds.

- [x] 5.1 Enforce trust ceiling in `AnchorPromoter` — when trust pipeline returns ceiling below assigned authority, use the lower ceiling (anchor-trust spec: "Trust ceiling enforcement on promotion")
- [x] 5.2 Wire trust re-evaluation triggers — after conflict resolution, re-evaluate involved anchor's trust; at reinforcement milestones (3x, 7x), re-evaluate before upgrading authority (anchor-trust spec: "Trust re-evaluation trigger")
- [x] 5.3 Integrate `diceDecay` into `ExponentialDecayPolicy` — formula: `effectiveHalfLife = baseHalfLife / max(diceDecay, 0.01)`. diceDecay=0.0 means no decay, diceDecay=1.0 means standard rate, diceDecay>1.0 means faster (anchor-trust spec: "DICE decay alignment")
- [x] 5.4 Add `DecayPolicy.shouldDemoteAuthority(Anchor, int newRank)` — returns true when rank drops below configurable thresholds: 400 for RELIABLE, 200 for UNRELIABLE. CANON exempt. Wire into `AnchorEngine.applyDecay()` (anchor-lifecycle spec: "Decay-based authority demotion")
- [x] 5.5 Add demotion threshold config properties — `reliable-rank-threshold` (default: 400), `unreliable-rank-threshold` (default: 200), `demoteThreshold` (default: 0.6) to DiceAnchorsProperties (anchor-lifecycle spec: "Decay-based authority demotion")
- [x] 5.6 Add `DomainProfile` weight validation — compact constructor validates sum to 1.0 (tolerance 0.001), throws `IllegalArgumentException` if not. Add `forTesting()` factory that bypasses validation (anchor-trust spec: "Domain profile weight validation")
- [x] 5.7 Integrate `diceImportance` into priority calculations — high-importance anchors (>0.7) get rank boost, low-importance (<0.3) may be evicted earlier. Default 0.0 has no effect (anchor-trust spec: "DICE importance integration")
- [x] 5.8 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 6. Conflict Resolution

DEMOTE_EXISTING resolution option and DICE revision integration.

- [x] 6.1 Add `DEMOTE_EXISTING` to `ConflictResolver.Resolution` enum (anchor-conflict spec: "DEMOTE_EXISTING conflict resolution option")
- [x] 6.2 Update `AuthorityConflictResolver.byAuthority()` with conflict matrix — CANON: KEEP_EXISTING; RELIABLE + >=0.8: REPLACE; RELIABLE + 0.6-0.8: DEMOTE_EXISTING; RELIABLE + <0.6: KEEP_EXISTING; UNRELIABLE + >=0.6: REPLACE; UNRELIABLE + <0.6: DEMOTE_EXISTING; PROVISIONAL: REPLACE. Thresholds from config (anchor-conflict spec: "DEMOTE_EXISTING conflict resolution option")
- [x] 6.3 Wire DEMOTE_EXISTING handling in AnchorEngine conflict resolution — call demote() on existing anchor, then promote incoming proposition (anchor-conflict spec: "DEMOTE_EXISTING triggers demotion and promotion" scenario)
- [x] 6.4 Add `ConflictDetector` and `ConflictResolver` SPI Javadoc — contracts, thread safety, error handling (return empty list / KEEP_EXISTING on failure), invariants (anchor-conflict spec: "ConflictDetector SPI Javadoc contracts")
- [x] 6.5 Investigate Q1: can DICE's `LlmPropositionReviser` be called standalone? If yes, implement `DiceRevisionConflictDetector` and `"dice"` strategy option. If no, document limitation and skip (design Q1, anchor-conflict spec: conditional requirements) — **Investigated: LlmPropositionReviser is already a Spring bean in PropositionConfiguration. Implementation of DiceRevisionConflictDetector deferred as a documented future direction per proposal pattern (see anchor-conflict spec "Future direction" notes).**
- [x] 6.6 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 7. Extraction Pipeline

Dedup improvements, silent failure fixes, and AnchorTools updates.

- [x] 7.1 Audit `DuplicateDetector` catch blocks — verify all log at WARN with structured context (operation, exception, fallback, contextId, candidate). Upgrade any below WARN (anchor-extraction spec: "Silent failure elimination in duplicate detection")
- [x] 7.2 Upgrade `LlmConflictDetector.parseResponse()` from DEBUG to WARN — include truncated response text, exception details, and fallback behavior (anchor-extraction spec: "Silent failure elimination in conflict detection")
- [x] 7.3 Add cross-reference dedup in `AnchorPromoter` — check incoming candidates against active anchors in context, not just within the batch (anchor-extraction spec: "Intra-batch deduplication against existing anchors")
- [x] 7.4 Add `demoteAnchor(String anchorId, String reason)` to `AnchorTools` — call AnchorEngine.demote() with MANUAL reason. For CANON, create pending decanonization. Return result describing new authority or pending status (anchor-extraction spec: "AnchorTools documentation and demote tool")
- [x] 7.5 Update all `AnchorTools` @LlmTool descriptions — document what each tool does, parameters, guardrails, when to use/not use (anchor-extraction spec: "AnchorTools documentation and demote tool")
- [x] 7.6 Add `AnchorPromoter` class-level Javadoc — document full pipeline flow: confidence → dedup → conflict → trust → promote. Each gate's purpose, batch processing, relationship to AnchorEngine (anchor-extraction spec: "Promotion pipeline documentation")
- [x] 7.7 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 8. Assembly Layer

Event-driven cache invalidation, DICE importance in budget, and documentation.

- [x] 8.1 Create `AnchorCacheInvalidator` Spring bean — @EventListener for AnchorLifecycleEvent, tracks dirty contextIds via ConcurrentHashMap.newKeySet() (anchor-assembly spec: "Event-driven cache invalidation" + "AnchorCacheInvalidator thread safety")
- [x] 8.2 Integrate invalidator into `AnchorsLlmReference` and `PropositionsLlmReference` — check isDirty(contextId) before using cached data; clear dirty flag after reload (anchor-assembly spec: "Event-driven cache invalidation")
- [x] 8.3 Update `PromptBudgetEnforcer` drop order to include DICE importance — within same authority tier, low-importance anchors dropped before high-importance. Default 0.0 falls back to rank-based ordering (anchor-assembly spec: "DICE importance in budget eviction priority")
- [x] 8.4 Add `PromptBudgetEnforcer` Javadoc — budget algorithm, drop order (PROVISIONAL→UNRELIABLE→RELIABLE, never CANON), mandatory overhead, BudgetResult semantics (anchor-assembly spec: "PromptBudgetEnforcer documentation")
- [x] 8.5 Add `TokenCounter` and `CharHeuristicTokenCounter` Javadoc — interface contract, heuristic limitations, how to implement accurate counter, thread-safety requirement (anchor-assembly spec: "TokenCounter SPI documentation")
- [x] 8.6 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 9. Cleanup & Configuration

Standalone fixes that don't depend on phases 3-8.

- [x] 9.1 Rename `ReinforcementPolicy.threshHold()` to `threshold()` — update all call sites (anchor-lifecycle spec: "Fix ReinforcementPolicy.threshHold() typo")
- [x] 9.2 Refactor `AnchorContextLock` — replace `AtomicBoolean` + `volatile String` with single `AtomicReference<String>`. tryLock/unlock/isLocked via compareAndSet (anchor-lifecycle spec: "AnchorContextLock cleanup")
- [x] 9.3 Add configuration validation in `AnchorConfiguration` @PostConstruct — budget > 0, autoActivateThreshold in [0.0,1.0], promptTokenBudget >= 0, minRank < maxRank, initialRank in [minRank,maxRank], demoteThreshold in [0.0,1.0], DomainProfile weights sum to 1.0 (anchor-lifecycle spec: "Configuration validation at startup")
- [x] 9.4 Add `ReinforcementPolicy` and `DecayPolicy` SPI Javadoc — contracts, thread safety, error handling (proposal: SPI formalization)
- [x] 9.5 Add `TrustSignal` SPI Javadoc — contract, thread safety, factory method documentation (proposal: SPI formalization)
- [x] 9.6 Verify: `./mvnw.cmd clean compile -DskipTests` succeeds

## 10. Tests

Update existing tests for new invariants and add new test coverage.

- [x] 10.1 Update existing tests for bidirectional authority — replace assertions on "upgrade-only" A3 with new A3a-A3e invariants. TrustModelTest, DeterministicSimulationTest, etc. (design R7)
- [x] 10.2 Add tests for `Authority.previousLevel()` — all four authority levels (anchor-lifecycle spec scenarios)
- [x] 10.3 Add tests for `AnchorEngine.demote()` — RELIABLE→UNRELIABLE, PROVISIONAL→archive, non-existent anchor, CANON routing through gate (anchor-lifecycle spec: "Demote anchor" scenarios)
- [x] 10.4 Add tests for canonization gate — request creation, approve, reject, stale request rejection, idempotency, simulation auto-approve, gate disabled behavior (canonization-gate spec: all scenarios)
- [x] 10.5 Add tests for trust ceiling enforcement — ceiling limits initial authority, ceiling above assigned has no effect (anchor-trust spec: "Trust ceiling enforcement on promotion" scenarios)
- [x] 10.6 Add tests for trust re-evaluation — conflict trigger, reinforcement milestone trigger, CANON exempt from auto-demotion (anchor-trust spec: "Trust ceiling enforcement on re-evaluation" + "Trust re-evaluation trigger" scenarios)
- [x] 10.7 Add tests for eviction event publishing — eviction publishes Evicted events, pinned anchors not evicted (anchor-lifecycle spec: "Eviction lifecycle events" scenarios)
- [x] 10.8 Add tests for DEMOTE_EXISTING resolution — conflict matrix (all authority × confidence combinations), CANON keeps existing, DEMOTE_EXISTING triggers demotion + promotion (anchor-conflict spec scenarios)
- [x] 10.9 Add tests for DICE decay alignment — permanent (0.0), standard (1.0), ephemeral (>1.0), default backward compatibility (anchor-trust spec: "DICE decay alignment" scenarios)
- [x] 10.10 Add tests for decay-based authority demotion — rank below threshold triggers demotion, CANON exempt (anchor-lifecycle spec: "Decay-based authority demotion" scenarios)
- [x] 10.11 Add tests for configuration validation — invalid budget, invalid rank range, valid config passes (anchor-lifecycle spec: "Configuration validation at startup" scenarios)
- [x] 10.12 Add tests for DomainProfile weight validation — valid weights, invalid weights, forTesting() bypass (anchor-trust spec: "Domain profile weight validation" scenarios)
- [x] 10.13 Add tests for Optional repository returns — found returns present, missing returns empty (anchor-lifecycle spec: "Optional returns for repository finders" scenarios)
- [x] 10.14 Add tests for AnchorContextLock cleanup — lock, unlock, concurrent rejection (anchor-lifecycle spec: "AnchorContextLock cleanup" scenarios)
- [x] 10.15 Add tests for cache invalidation — promotion invalidates, reinforcement invalidates, other contexts unaffected, clean read clears dirty flag (anchor-assembly spec scenarios)
- [x] 10.16 Add tests for intra-batch dedup against existing anchors — candidate duplicates existing, unique candidate passes (anchor-extraction spec scenarios)

- [x] 10.17 Add tests for demoteAnchor tool — demote RELIABLE, attempt CANON (pending request), non-existent anchor (anchor-extraction spec: "AnchorTools documentation and demote tool" scenarios)
- [x] 10.18 If Q1 resolved yes: add tests for DiceRevisionConflictDetector — CONTRADICTORY, SIMILAR, UNRELATED, IDENTICAL classifications (anchor-conflict spec scenarios) — **Q1 deferred, skipped per design.**

## 11. Final Verification

- [x] 11.1 Run full test suite — `./mvnw.cmd test` — all existing + new tests pass
- [x] 11.2 Run full compile — `./mvnw.cmd clean compile` — no warnings, no errors
- [x] 11.3 Spot-check Javadoc completeness — every public method on AnchorEngine, Authority, Anchor, ConflictDetector, ConflictResolver, DecayPolicy, ReinforcementPolicy, TrustSignal, TokenCounter, PromptBudgetEnforcer, AnchorTools has Javadoc with contracts
- [x] 11.4 Verify all A3 references updated — grep for "A3" / "upgrade-only" / "upgrade only" — no stale references to the old invariant remain
