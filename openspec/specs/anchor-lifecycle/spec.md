## MODIFIED Requirements

### Requirement: Bidirectional authority lifecycle

**Modifies**: Invariant A3 ("authority only upgrades") across `AnchorEngine`, `Authority`, `AnchorRepository`, and lifecycle events.

The system SHALL support both promotion (upward) and demotion (downward) authority transitions. The old "upgrade-only" invariant A3 is replaced with five new invariants:

| Invariant | Rule |
|-----------|------|
| **A3a** | CANON is never assigned by automatic promotion. Only explicit action (seed anchors, manual tool call, or approved canonization request) can set CANON. |
| **A3b** | CANON anchors are immune to automatic demotion (decay, trust re-evaluation). Only explicit action (conflict resolution DEMOTE_EXISTING, manual tool call, or approved decanonization request) can demote CANON. |
| **A3c** | Automatic demotion (via decay or trust re-evaluation) applies to RELIABLE → UNRELIABLE → PROVISIONAL. |
| **A3d** | Pinned anchors are immune to automatic demotion. Explicit demotion still works. |
| **A3e** | All authority transitions (both directions) publish `AuthorityChanged` lifecycle events. |

#### Scenario: Promote anchor through reinforcement

- **GIVEN** an anchor at PROVISIONAL authority with 2 reinforcements
- **WHEN** the anchor is reinforced a 3rd time and the reinforcement policy threshold is met
- **THEN** the anchor's authority is upgraded to UNRELIABLE and an `AuthorityChanged` event is published with direction PROMOTED

#### Scenario: Demote anchor via conflict resolution

- **GIVEN** an anchor at RELIABLE authority
- **WHEN** conflict resolution returns DEMOTE_EXISTING for a contradicting incoming proposition
- **THEN** the anchor's authority is demoted to UNRELIABLE, an `AuthorityChanged` event is published with direction DEMOTED and reason CONFLICT_EVIDENCE

#### Scenario: CANON immune to automatic demotion

- **GIVEN** an anchor at CANON authority whose rank has decayed below 400
- **WHEN** the decay policy evaluates the anchor for authority demotion
- **THEN** the anchor remains at CANON authority (invariant A3b)

#### Scenario: Pinned anchor immune to automatic demotion

- **GIVEN** a pinned anchor at RELIABLE authority whose trust score has dropped below the RELIABLE threshold
- **WHEN** trust re-evaluation runs
- **THEN** the anchor remains at RELIABLE authority (invariant A3d)

#### Scenario: Demote PROVISIONAL archives instead

- **GIVEN** an anchor at PROVISIONAL authority
- **WHEN** a demotion is requested (conflict resolution or explicit)
- **THEN** the anchor is archived instead of demoted, and an `Archived` lifecycle event is published with reason CONFLICT_EVIDENCE or MANUAL

### Requirement: Authority enum with previousLevel()

**Modifies**: `Authority` enum.

The `Authority` enum SHALL provide a `previousLevel()` method symmetric with the promotion path:
- CANON → RELIABLE
- RELIABLE → UNRELIABLE
- UNRELIABLE → PROVISIONAL
- PROVISIONAL → PROVISIONAL (floor)

#### Scenario: previousLevel for each authority

- **GIVEN** the Authority enum
- **WHEN** `previousLevel()` is called on CANON
- **THEN** RELIABLE is returned
- **WHEN** `previousLevel()` is called on RELIABLE
- **THEN** UNRELIABLE is returned
- **WHEN** `previousLevel()` is called on UNRELIABLE
- **THEN** PROVISIONAL is returned
- **WHEN** `previousLevel()` is called on PROVISIONAL
- **THEN** PROVISIONAL is returned

### Requirement: AnchorEngine.demote() method

**Modifies**: `AnchorEngine` public API.

`AnchorEngine` SHALL provide a `demote(String anchorId, DemotionReason reason)` method that:
1. Looks up the anchor's current authority
2. Computes `previousLevel()` of the current authority
3. If already PROVISIONAL, archives the anchor instead
4. Otherwise, calls `repository.setAuthority(anchorId, newAuthority)`
5. Publishes an `AuthorityChanged` event with direction DEMOTED and the given reason

#### Scenario: Demote RELIABLE anchor

- **GIVEN** an anchor "A1" at RELIABLE authority
- **WHEN** `demote("A1", DemotionReason.CONFLICT_EVIDENCE)` is called
- **THEN** the anchor's authority becomes UNRELIABLE and an `AuthorityChanged` event is published with direction DEMOTED

#### Scenario: Demote PROVISIONAL anchor falls through to archive

- **GIVEN** an anchor "A1" at PROVISIONAL authority
- **WHEN** `demote("A1", DemotionReason.RANK_DECAY)` is called
- **THEN** the anchor is archived (not demoted) and an `Archived` event is published

#### Scenario: Demote non-existent anchor

- **GIVEN** no anchor exists with ID "missing"
- **WHEN** `demote("missing", DemotionReason.MANUAL)` is called
- **THEN** a WARN-level log is emitted and no exception is thrown

### Requirement: DemotionReason enum

The system SHALL define a `DemotionReason` enum with the following values:
- `CONFLICT_EVIDENCE` — contradicting evidence found via conflict resolution
- `TRUST_DEGRADATION` — trust re-evaluation scored below threshold for current authority (see anchor-trust spec: "Trust ceiling enforcement on re-evaluation" and "Trust re-evaluation trigger" for when this is invoked)
- `RANK_DECAY` — rank dropped below authority-specific threshold
- `MANUAL` — explicit user or system action

### Requirement: Repository setAuthority replaces upgradeAuthority

**Modifies**: `AnchorRepository`.

`AnchorRepository.upgradeAuthority(String anchorId, String authority)` SHALL be replaced with `setAuthority(String anchorId, String authority)`. The new method removes the Cypher `WHERE newLevel > currentLevel` guard, allowing both promotion and demotion. Business rules for transition validation live in `AnchorEngine`, not the repository.

#### Scenario: Set authority to lower level

- **GIVEN** an anchor "A1" at RELIABLE authority
- **WHEN** `setAuthority("A1", "UNRELIABLE")` is called
- **THEN** the anchor's authority is updated to UNRELIABLE in Neo4j

#### Scenario: Set authority to higher level

- **GIVEN** an anchor "A1" at PROVISIONAL authority
- **WHEN** `setAuthority("A1", "UNRELIABLE")` is called
- **THEN** the anchor's authority is updated to UNRELIABLE in Neo4j

### Requirement: AuthorityChanged lifecycle event

**Modifies**: `AnchorLifecycleEvent` sealed hierarchy (replaces `AuthorityUpgraded`).

The `AuthorityUpgraded` event type SHALL be renamed to `AuthorityChanged` and extended with:
- `AuthorityChangeDirection direction` — `PROMOTED` or `DEMOTED`
- `String reason` — human-readable reason for the change (from `DemotionReason` for demotions, or "reinforcement" / "trust-evaluation" for promotions)

The `AuthorityChangeDirection` enum SHALL have values `PROMOTED` and `DEMOTED`.

#### Scenario: Promotion publishes AuthorityChanged with PROMOTED direction

- **GIVEN** an anchor at UNRELIABLE authority
- **WHEN** reinforcement upgrades authority to RELIABLE
- **THEN** an `AuthorityChanged` event is published with previousAuthority=UNRELIABLE, newAuthority=RELIABLE, direction=PROMOTED

#### Scenario: Demotion publishes AuthorityChanged with DEMOTED direction

- **GIVEN** an anchor at RELIABLE authority
- **WHEN** conflict resolution triggers demotion to UNRELIABLE
- **THEN** an `AuthorityChanged` event is published with previousAuthority=RELIABLE, newAuthority=UNRELIABLE, direction=DEMOTED, reason="CONFLICT_EVIDENCE"

### Requirement: Eviction lifecycle events

**Modifies**: `AnchorLifecycleEvent` sealed hierarchy.

The system SHALL add an `Evicted` event type to the lifecycle event hierarchy containing: `anchorId`, `contextId`, and `previousRank`. The event is published for each anchor evicted during budget enforcement.

`AnchorRepository.evictLowestRanked()` SHALL return a `List<EvictedAnchorInfo>` (containing anchor ID and rank) instead of `int`, so the engine can publish individual eviction events.

**Event ordering**: During `AnchorEngine.promote()`, the `Promoted` event fires before any `Evicted` events. Budget enforcement (eviction) occurs after the new anchor is written. The brief intermediate state where active count exceeds budget is not observable externally because both operations occur within a single `promote()` call.

#### Scenario: Eviction publishes events for each evicted anchor

- **GIVEN** the anchor budget is 20 and there are 21 active anchors in context "ctx-1"
- **WHEN** a new anchor is promoted in context "ctx-1"
- **THEN** the lowest-ranked non-pinned anchor is evicted AND an `Evicted` lifecycle event is published with the evicted anchor's ID and previous rank

#### Scenario: Pinned anchors not evicted

- **GIVEN** the anchor budget is 3, there are 3 active anchors, and the lowest-ranked anchor is pinned
- **WHEN** a new anchor is promoted
- **THEN** the next-lowest-ranked non-pinned anchor is evicted instead

### Requirement: Decay-based authority demotion

The `DecayPolicy` SPI SHALL include a `shouldDemoteAuthority(Anchor anchor, int newRank)` method that returns `true` when the decayed rank drops below authority-specific thresholds.

Default thresholds (configurable via `DiceAnchorsProperties`):
- `dice-anchors.anchor.reliable-rank-threshold` (default: `400`) — RELIABLE anchors with rank below this are demoted to UNRELIABLE
- `dice-anchors.anchor.unreliable-rank-threshold` (default: `200`) — UNRELIABLE anchors with rank below this are demoted to PROVISIONAL
- CANON is never demoted by decay (invariant A3b)
- PROVISIONAL has no lower threshold

`AnchorEngine.applyDecay()` SHALL check `shouldDemoteAuthority()` after applying rank decay and call `demote()` if warranted.

#### Scenario: Rank decay triggers authority demotion

- **GIVEN** an anchor at RELIABLE authority with rank 450
- **WHEN** decay reduces the rank to 380
- **THEN** the anchor's rank is updated to 380 AND `demote()` is called with reason RANK_DECAY, reducing authority to UNRELIABLE

#### Scenario: Rank decay does not demote CANON

- **GIVEN** an anchor at CANON authority with rank 300
- **WHEN** decay reduces the rank to 250
- **THEN** the anchor's rank is updated to 250 but authority remains CANON

### Requirement: Optional returns for repository finders

**Modifies**: `AnchorRepository.findPropositionNodeById()`.

`findPropositionNodeById(String id)` SHALL return `Optional<PropositionNode>` instead of nullable `PropositionNode`. All callers in `AnchorEngine` and `AnchorTools` SHALL be updated to use `Optional` methods (`ifPresent`, `ifPresentOrElse`, `orElse`, etc.).

#### Scenario: Found anchor returns present Optional

- **GIVEN** an anchor exists with ID "A1"
- **WHEN** `findPropositionNodeById("A1")` is called
- **THEN** `Optional.of(node)` is returned

#### Scenario: Missing anchor returns empty Optional

- **GIVEN** no anchor exists with ID "missing"
- **WHEN** `findPropositionNodeById("missing")` is called
- **THEN** `Optional.empty()` is returned

### Requirement: Configuration validation at startup

**Modifies**: `AnchorConfiguration`.

The system SHALL validate configuration properties at startup via a `@PostConstruct` method:
- `anchor.budget > 0`
- `anchor.autoActivateThreshold` in [0.0, 1.0]
- `assembly.promptTokenBudget >= 0`
- `anchor.minRank < anchor.maxRank`
- `anchor.initialRank` in [`anchor.minRank`, `anchor.maxRank`]
- `anchor.demoteThreshold` in [0.0, 1.0]
- Signal weights in `DomainProfile` sum to 1.0 (within tolerance 0.001)

Violations SHALL throw `IllegalStateException` with a descriptive message, preventing application startup.

#### Scenario: Invalid budget fails startup

- **GIVEN** `dice-anchors.anchor.budget` is set to 0
- **WHEN** the application starts
- **THEN** startup fails with `IllegalStateException` containing "budget must be > 0"

#### Scenario: Invalid rank range fails startup

- **GIVEN** `dice-anchors.anchor.minRank` is set to 900 and `dice-anchors.anchor.maxRank` is set to 100
- **WHEN** the application starts
- **THEN** startup fails with `IllegalStateException` containing "minRank must be less than maxRank"

#### Scenario: Valid configuration passes validation

- **GIVEN** all configuration values are within valid ranges
- **WHEN** the application starts
- **THEN** validation passes and the application starts normally

### Requirement: Invariant enforcement hook before archive

`AnchorEngine.archive()` SHALL evaluate all applicable invariants via `InvariantEvaluator.evaluate(ARCHIVE, anchorId, contextId)` before committing the archive operation. If the evaluation returns `blocked = true` (a MUST-strength invariant is violated), the archive SHALL NOT proceed and the method SHALL return without modifying anchor state.

When the evaluation returns `blocked = false` but contains SHOULD-strength violations, the archive SHALL proceed and violation events SHALL be published.

The invariant evaluation SHALL occur after the anchor lookup but before calling `repository.archiveAnchor()`.

#### Scenario: MUST-strength invariant blocks archive

- **GIVEN** a MUST-strength `ANCHOR_PROTECTED` invariant for anchor "A1"
- **WHEN** `AnchorEngine.archive("A1", ArchiveReason.MANUAL)` is called
- **THEN** the archive SHALL be blocked, anchor "A1" SHALL remain active, and an `InvariantViolation` event SHALL be published with `blocked = true`

#### Scenario: SHOULD-strength invariant warns but allows archive

- **GIVEN** a SHOULD-strength `ANCHOR_PROTECTED` invariant for anchor "A1"
- **WHEN** `AnchorEngine.archive("A1", ArchiveReason.MANUAL)` is called
- **THEN** the archive SHALL proceed, an `InvariantViolation` event SHALL be published with `blocked = false`, and an `Archived` event SHALL be published

#### Scenario: No invariants allows archive normally

- **GIVEN** no invariants are registered
- **WHEN** `AnchorEngine.archive("A1", ArchiveReason.MANUAL)` is called
- **THEN** the archive SHALL proceed normally with no invariant evaluation overhead

### Requirement: Invariant enforcement hook before eviction

`AnchorEngine.promote()` SHALL evaluate invariants via `InvariantEvaluator.evaluate(EVICT, candidateAnchorId, contextId)` for each eviction candidate during budget enforcement. If the evaluation returns `blocked = true`, that candidate SHALL be skipped and the next-lowest-ranked non-pinned anchor SHALL be considered.

The eviction loop SHALL iterate through candidates in rank order (ascending) until either:
1. A non-blocked candidate is found and evicted, or
2. All candidates are blocked by MUST-strength invariants, in which case the budget temporarily exceeds the limit and a WARN-level log is emitted

#### Scenario: Protected anchor skipped during eviction

- **GIVEN** a MUST-strength `ANCHOR_PROTECTED` invariant for anchor "A1"
- **AND** the budget is 3 with 3 active anchors, and "A1" has the lowest rank
- **WHEN** a new anchor is promoted
- **THEN** "A1" SHALL be skipped and the next-lowest-ranked non-pinned, non-protected anchor SHALL be evicted

#### Scenario: Multiple protected anchors in eviction candidates

- **GIVEN** MUST-strength `ANCHOR_PROTECTED` invariants for anchors "A1" and "A2"
- **AND** the budget is 3 with 3 active anchors, "A1" and "A2" being the two lowest-ranked
- **WHEN** a new anchor is promoted
- **THEN** "A1" and "A2" SHALL be skipped and the third-lowest-ranked non-pinned anchor SHALL be evicted

#### Scenario: All eviction candidates protected

- **GIVEN** MUST-strength `ANCHOR_PROTECTED` invariants for all non-pinned anchors
- **AND** the budget is full
- **WHEN** a new anchor is promoted
- **THEN** no anchor SHALL be evicted, the budget SHALL temporarily exceed the limit, and a WARN-level log SHALL be emitted: "Invariant protection prevented eviction; budget exceeded"

### Requirement: Invariant enforcement hook before demotion

`AnchorEngine.demote()` SHALL evaluate invariants via `InvariantEvaluator.evaluate(DEMOTE, anchorId, contextId)` before committing the demotion. If the evaluation returns `blocked = true`, the demotion SHALL NOT proceed.

The invariant evaluation SHALL occur after the canonization gate check (CANON anchors route through the gate first) but before computing `previousLevel()` and writing the new authority.

#### Scenario: AUTHORITY_FLOOR invariant blocks demotion

- **GIVEN** a MUST-strength `AUTHORITY_FLOOR` invariant for anchor "A1" with minimum authority RELIABLE
- **AND** anchor "A1" is at RELIABLE authority
- **WHEN** `demote("A1", DemotionReason.RANK_DECAY)` is called
- **THEN** the demotion SHALL be blocked and anchor "A1" SHALL remain at RELIABLE

#### Scenario: AUTHORITY_FLOOR allows demotion above floor

- **GIVEN** a MUST-strength `AUTHORITY_FLOOR` invariant for anchor "A1" with minimum authority UNRELIABLE
- **AND** anchor "A1" is at RELIABLE authority
- **WHEN** `demote("A1", DemotionReason.RANK_DECAY)` is called
- **THEN** the demotion SHALL proceed (RELIABLE to UNRELIABLE is at the floor, not below it)

#### Scenario: CONTEXT_FROZEN blocks all demotions in context

- **GIVEN** a MUST-strength `CONTEXT_FROZEN` invariant for context "ctx-1"
- **WHEN** `demote("A1", DemotionReason.CONFLICT_EVIDENCE)` is called for an anchor in "ctx-1"
- **THEN** the demotion SHALL be blocked

### Requirement: Invariant enforcement hook before authority change

All authority transitions (both promotion and demotion) SHALL be evaluated against `AUTHORITY_CHANGE` invariants via `InvariantEvaluator.evaluate(AUTHORITY_CHANGE, anchorId, contextId)`. This applies to:

1. Authority promotions in `AnchorEngine.reinforce()` (when `shouldUpgradeAuthority()` returns true)
2. Authority demotions in `AnchorEngine.demote()`
3. Authority changes via `CanonizationGate.approve()`

The `AUTHORITY_CHANGE` evaluation is in addition to the action-specific evaluation (e.g., `DEMOTE` check runs first, then `AUTHORITY_CHANGE` check). Both evaluations MUST pass for the action to proceed.

#### Scenario: Authority promotion blocked by CONTEXT_FROZEN

- **GIVEN** a MUST-strength `CONTEXT_FROZEN` invariant for context "ctx-1"
- **AND** an anchor in "ctx-1" reaches the reinforcement threshold for authority upgrade
- **WHEN** `reinforce()` attempts to promote authority
- **THEN** the authority promotion SHALL be blocked, but the rank boost SHALL still be applied

#### Scenario: Both DEMOTE and AUTHORITY_CHANGE evaluated

- **GIVEN** a MUST-strength `AUTHORITY_FLOOR` invariant (constrains DEMOTE and AUTHORITY_CHANGE)
- **WHEN** `demote("A1", DemotionReason.TRUST_DEGRADATION)` is called
- **THEN** both `evaluate(DEMOTE, ...)` and `evaluate(AUTHORITY_CHANGE, ...)` SHALL be called, and the demotion SHALL be blocked if either returns `blocked = true`

### Requirement: Hook evaluation order

When a lifecycle operation triggers invariant evaluation, the hooks SHALL be evaluated in the following order:

1. Canonization gate check (for CANON transitions only)
2. Invariant evaluation for the primary action (`ARCHIVE`, `EVICT`, or `DEMOTE`)
3. Invariant evaluation for `AUTHORITY_CHANGE` (if the action involves an authority transition)

If any step blocks the action, subsequent steps SHALL NOT be evaluated. This short-circuit behavior avoids unnecessary work and prevents confusing violation events for actions that were already blocked.

#### Scenario: Canonization gate blocks before invariant check

- **GIVEN** the canonization gate is enabled and a MUST-strength invariant exists
- **WHEN** `demote()` is called on a CANON anchor
- **THEN** the canonization gate creates a pending request and returns; invariant evaluation SHALL NOT run

#### Scenario: Primary action blocked before AUTHORITY_CHANGE check

- **GIVEN** a MUST-strength `ANCHOR_PROTECTED` invariant blocks `DEMOTE` for anchor "A1"
- **AND** a separate `AUTHORITY_CHANGE` invariant exists
- **WHEN** `demote("A1", ...)` is called
- **THEN** the `DEMOTE` invariant blocks the action and the `AUTHORITY_CHANGE` invariant SHALL NOT be evaluated

### Requirement: Fix ReinforcementPolicy.threshHold() typo

**Modifies**: `ReinforcementPolicy` interface.

The method `threshHold()` SHALL be renamed to `threshold()`. All call sites SHALL be updated.

### Requirement: AnchorContextLock cleanup

**Modifies**: `AnchorContextLock`.

`AnchorContextLock` SHALL replace `AtomicBoolean locked` + `volatile String lockedBy` with a single `AtomicReference<String> lockedBy`. Lock state is derived from whether `lockedBy` is null:
- `tryLock(turnId)` → `lockedBy.compareAndSet(null, turnId)`
- `unlock(turnId)` → `lockedBy.compareAndSet(turnId, null)`
- `isLocked()` → `lockedBy.get() != null`

#### Scenario: Lock and unlock with AtomicReference

- **GIVEN** the context lock is unlocked
- **WHEN** `tryLock("turn-1")` is called
- **THEN** the lock is acquired and `isLocked()` returns true
- **WHEN** `unlock("turn-1")` is called
- **THEN** the lock is released and `isLocked()` returns false

#### Scenario: Concurrent lock attempt rejected

- **GIVEN** the context lock is held by "turn-1"
- **WHEN** `tryLock("turn-2")` is called
- **THEN** the lock is not acquired and `tryLock` returns false

### Requirement: Cypher query parameterization

**Modifies**: `AnchorRepository`.

All dynamic values in Cypher queries SHALL use parameterized bindings (Drivine `.bind()`) instead of string concatenation. The current codebase contains string concatenation for `contextId` in several query methods, which is vulnerable to Cypher injection.

This is a correctness and security fix — the system resists adversarial prompt drift, and the persistence layer should not be vulnerable to injection.

#### Scenario: Context ID with special characters handled safely

- **GIVEN** a context ID containing special characters (e.g., `"ctx-'; DROP (n) --"`)
- **WHEN** any repository method is called with this context ID
- **THEN** the query executes safely with the context ID treated as a literal parameter value, not as Cypher syntax

### Requirement: Temporal fields set on promotion

**Modifies**: `AnchorEngine.promote()` and the "Eviction lifecycle events" requirement.

`AnchorEngine.promote()` SHALL set `validFrom = Instant.now()` and `transactionStart = Instant.now()` on the newly promoted anchor, in addition to all existing promotion behavior (rank assignment, tier computation, budget enforcement, event publishing).

The temporal fields SHALL be persisted to `PropositionNode` as part of the promotion write operation.

#### Scenario: Promotion sets temporal fields

- **GIVEN** a proposition passes all promotion gates
- **WHEN** `AnchorEngine.promote()` completes at instant T1
- **THEN** the new anchor SHALL have `validFrom = T1` and `transactionStart = T1`
- **AND** `validTo` and `transactionEnd` SHALL be null

#### Scenario: Promotion temporal fields coexist with tier assignment

- **GIVEN** a proposition promoted with `initialRank = 700`
- **WHEN** `AnchorEngine.promote()` completes at instant T1
- **THEN** the anchor SHALL have `memoryTier = HOT`, `validFrom = T1`, and `transactionStart = T1`

### Requirement: Temporal fields and supersession on archive

**Modifies**: `AnchorEngine.archive()` (implicit in "AnchorArchivedEvent" requirement from anchor-lifecycle-events spec, and "Eviction lifecycle events" requirement).

`AnchorEngine.archive()` SHALL set `validTo = Instant.now()` and `transactionEnd = Instant.now()` on the archived anchor, in addition to existing archive behavior (status change, event publishing).

When archiving due to conflict replacement (reason = `CONFLICT_REPLACEMENT`), `archive()` SHALL also:
1. Create a `SUPERSEDES` relationship from the incoming anchor to the archived anchor
2. Set `supersededBy` on the archived anchor to the incoming anchor's ID
3. Set `supersedes` on the incoming anchor to the archived anchor's ID

#### Scenario: Archive sets temporal end fields

- **GIVEN** an active anchor "A1" with `validFrom = T1`
- **WHEN** `AnchorEngine.archive("A1", ArchiveReason.MANUAL)` is called at instant T5
- **THEN** anchor "A1" SHALL have `validTo = T5` and `transactionEnd = T5`

#### Scenario: Archive for conflict replacement creates supersession

- **GIVEN** an active anchor "A1" conflicting with incoming proposition
- **AND** conflict resolution returns REPLACE
- **WHEN** the engine archives "A1" and promotes the incoming proposition as "A2" at instant T5
- **THEN** anchor "A1" SHALL have `validTo = T5`, `transactionEnd = T5`, and `supersededBy = "A2"`
- **AND** anchor "A2" SHALL have `supersedes = "A1"`
- **AND** a `SUPERSEDES` relationship SHALL exist from "A2" to "A1" with `reason = "CONFLICT_REPLACEMENT"`

#### Scenario: Archive for eviction sets temporal fields

- **GIVEN** the anchor budget is full and a new anchor is being promoted
- **WHEN** anchor "A1" is evicted (lowest-ranked non-pinned) at instant T5
- **THEN** anchor "A1" SHALL have `validTo = T5` and `transactionEnd = T5`

## ADDED Requirements

### Requirement: TierChanged lifecycle event

The system SHALL publish a `TierChanged` lifecycle event whenever an anchor's `memoryTier` changes as a result of a rank-modifying operation (reinforce, decay, promote). The event SHALL be a member of the `AnchorLifecycleEvent` sealed hierarchy.

The `TierChanged` event SHALL include:
- `anchorId` (String)
- `previousTier` (MemoryTier)
- `newTier` (MemoryTier)
- `contextId` (String)
- `occurredAt` (Instant)

Event publishing SHALL be gated by `anchorConfig.lifecycleEventsEnabled()`, consistent with all other lifecycle events.

#### Scenario: Reinforcement causes tier upgrade event

- **GIVEN** an anchor in WARM tier with `rank = 580` and `hotThreshold = 600`
- **WHEN** `AnchorEngine.reinforce()` boosts rank to 630
- **THEN** a `TierChanged` event SHALL be published with `previousTier = WARM` and `newTier = HOT`

#### Scenario: Decay causes tier downgrade event

- **GIVEN** an anchor in WARM tier with `rank = 360` and `warmThreshold = 350`
- **WHEN** decay reduces rank to 340
- **THEN** a `TierChanged` event SHALL be published with `previousTier = WARM` and `newTier = COLD`

#### Scenario: Rank change without tier change

- **GIVEN** an anchor in HOT tier with `rank = 800`
- **WHEN** decay reduces rank to 750 (still above `hotThreshold = 600`)
- **THEN** no `TierChanged` event SHALL be published

#### Scenario: Events disabled

- **GIVEN** `anchorConfig.lifecycleEventsEnabled() = false`
- **WHEN** a tier transition occurs
- **THEN** no `TierChanged` event SHALL be published

### Requirement: AnchorEngine tier tracking

`AnchorEngine` SHALL compute and compare memory tier before and after every rank-modifying operation (`promote`, `reinforce`, `applyDecay`). When the tier changes, the engine SHALL:
1. Update the persisted `memoryTier` on the anchor
2. Publish a `TierChanged` event (if events enabled)

#### Scenario: Promote with tier tracking

- **GIVEN** a proposition promoted with `initialRank = 700`
- **WHEN** `AnchorEngine.promote()` completes
- **THEN** the anchor SHALL have `memoryTier = HOT` persisted and no `TierChanged` event (initial assignment, not a transition)

#### Scenario: Sequential reinforcements crossing tiers

- **GIVEN** an anchor with `rank = 340` (COLD, `warmThreshold = 350`)
- **WHEN** `AnchorEngine.reinforce()` boosts rank to 390
- **THEN** `memoryTier` SHALL be updated to WARM and a `TierChanged(COLD → WARM)` event SHALL be published
