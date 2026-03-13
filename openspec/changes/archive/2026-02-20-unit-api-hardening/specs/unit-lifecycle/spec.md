## MODIFIED Requirements

### Requirement: Bidirectional authority lifecycle

**Modifies**: Invariant A3 ("authority only upgrades") across `ArcMemEngine`, `Authority`, `ContextUnitRepository`, and lifecycle events.

The system SHALL support both promotion (upward) and demotion (downward) authority transitions. The old "upgrade-only" invariant A3 is replaced with five new invariants:

| Invariant | Rule |
|-----------|------|
| **A3a** | CANON is never assigned by automatic promotion. Only explicit action (seed context units, manual tool call, or approved canonization request) can set CANON. |
| **A3b** | CANON context units are immune to automatic demotion (decay, trust re-evaluation). Only explicit action (conflict resolution DEMOTE_EXISTING, manual tool call, or approved decanonization request) can demote CANON. |
| **A3c** | Automatic demotion (via decay or trust re-evaluation) applies to RELIABLE → UNRELIABLE → PROVISIONAL. |
| **A3d** | Pinned context units are immune to automatic demotion. Explicit demotion still works. |
| **A3e** | All authority transitions (both directions) publish `AuthorityChanged` lifecycle events. |

#### Scenario: Promote context unit through reinforcement

- **GIVEN** an context unit at PROVISIONAL authority with 2 reinforcements
- **WHEN** the context unit is reinforced a 3rd time and the reinforcement policy threshold is met
- **THEN** the context unit's authority is upgraded to UNRELIABLE and an `AuthorityChanged` event is published with direction PROMOTED

#### Scenario: Demote context unit via conflict resolution

- **GIVEN** an context unit at RELIABLE authority
- **WHEN** conflict resolution returns DEMOTE_EXISTING for a contradicting incoming proposition
- **THEN** the context unit's authority is demoted to UNRELIABLE, an `AuthorityChanged` event is published with direction DEMOTED and reason CONFLICT_EVIDENCE

#### Scenario: CANON immune to automatic demotion

- **GIVEN** an context unit at CANON authority whose rank has decayed below 400
- **WHEN** the decay policy evaluates the context unit for authority demotion
- **THEN** the context unit remains at CANON authority (invariant A3b)

#### Scenario: Pinned context unit immune to automatic demotion

- **GIVEN** a pinned unit at RELIABLE authority whose trust score has dropped below the RELIABLE threshold
- **WHEN** trust re-evaluation runs
- **THEN** the context unit remains at RELIABLE authority (invariant A3d)

#### Scenario: Demote PROVISIONAL archives instead

- **GIVEN** an context unit at PROVISIONAL authority
- **WHEN** a demotion is requested (conflict resolution or explicit)
- **THEN** the context unit is archived instead of demoted, and an `Archived` lifecycle event is published with reason CONFLICT_EVIDENCE or MANUAL

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

### Requirement: ArcMemEngine.demote() method

**Modifies**: `ArcMemEngine` public API.

`ArcMemEngine` SHALL provide a `demote(String unitId, DemotionReason reason)` method that:
1. Looks up the context unit's current authority
2. Computes `previousLevel()` of the current authority
3. If already PROVISIONAL, archives the context unit instead
4. Otherwise, calls `repository.setAuthority(unitId, newAuthority)`
5. Publishes an `AuthorityChanged` event with direction DEMOTED and the given reason

#### Scenario: Demote RELIABLE context unit

- **GIVEN** an context unit "A1" at RELIABLE authority
- **WHEN** `demote("A1", DemotionReason.CONFLICT_EVIDENCE)` is called
- **THEN** the context unit's authority becomes UNRELIABLE and an `AuthorityChanged` event is published with direction DEMOTED

#### Scenario: Demote PROVISIONAL context unit falls through to archive

- **GIVEN** an context unit "A1" at PROVISIONAL authority
- **WHEN** `demote("A1", DemotionReason.RANK_DECAY)` is called
- **THEN** the context unit is archived (not demoted) and an `Archived` event is published

#### Scenario: Demote non-existent context unit

- **GIVEN** no context unit exists with ID "missing"
- **WHEN** `demote("missing", DemotionReason.MANUAL)` is called
- **THEN** a WARN-level log is emitted and no exception is thrown

### Requirement: DemotionReason enum

The system SHALL define a `DemotionReason` enum with the following values:
- `CONFLICT_EVIDENCE` — contradicting evidence found via conflict resolution
- `TRUST_DEGRADATION` — trust re-evaluation scored below threshold for current authority (see unit-trust spec: "Trust ceiling enforcement on re-evaluation" and "Trust re-evaluation trigger" for when this is invoked)
- `RANK_DECAY` — rank dropped below authority-specific threshold
- `MANUAL` — explicit user or system action

### Requirement: Repository setAuthority replaces upgradeAuthority

**Modifies**: `ContextUnitRepository`.

`ContextUnitRepository.upgradeAuthority(String unitId, String authority)` SHALL be replaced with `setAuthority(String unitId, String authority)`. The new method removes the Cypher `WHERE newLevel > currentLevel` guard, allowing both promotion and demotion. Business rules for transition validation live in `ArcMemEngine`, not the repository.

#### Scenario: Set authority to lower level

- **GIVEN** an context unit "A1" at RELIABLE authority
- **WHEN** `setAuthority("A1", "UNRELIABLE")` is called
- **THEN** the context unit's authority is updated to UNRELIABLE in Neo4j

#### Scenario: Set authority to higher level

- **GIVEN** an context unit "A1" at PROVISIONAL authority
- **WHEN** `setAuthority("A1", "UNRELIABLE")` is called
- **THEN** the context unit's authority is updated to UNRELIABLE in Neo4j

### Requirement: AuthorityChanged lifecycle event

**Modifies**: `ContextUnitLifecycleEvent` sealed hierarchy (replaces `AuthorityUpgraded`).

The `AuthorityUpgraded` event type SHALL be renamed to `AuthorityChanged` and extended with:
- `AuthorityChangeDirection direction` — `PROMOTED` or `DEMOTED`
- `String reason` — human-readable reason for the change (from `DemotionReason` for demotions, or "reinforcement" / "trust-evaluation" for promotions)

The `AuthorityChangeDirection` enum SHALL have values `PROMOTED` and `DEMOTED`.

#### Scenario: Promotion publishes AuthorityChanged with PROMOTED direction

- **GIVEN** an context unit at UNRELIABLE authority
- **WHEN** reinforcement upgrades authority to RELIABLE
- **THEN** an `AuthorityChanged` event is published with previousAuthority=UNRELIABLE, newAuthority=RELIABLE, direction=PROMOTED

#### Scenario: Demotion publishes AuthorityChanged with DEMOTED direction

- **GIVEN** an context unit at RELIABLE authority
- **WHEN** conflict resolution triggers demotion to UNRELIABLE
- **THEN** an `AuthorityChanged` event is published with previousAuthority=RELIABLE, newAuthority=UNRELIABLE, direction=DEMOTED, reason="CONFLICT_EVIDENCE"

### Requirement: Eviction lifecycle events

**Modifies**: `ContextUnitLifecycleEvent` sealed hierarchy.

The system SHALL add an `Evicted` event type to the lifecycle event hierarchy containing: `unitId`, `contextId`, and `previousRank`. The event is published for each context unit evicted during budget enforcement.

`ContextUnitRepository.evictLowestRanked()` SHALL return a `List<EvictedUnitInfo>` (containing context unit ID and rank) instead of `int`, so the engine can publish individual eviction events.

**Event ordering**: During `ArcMemEngine.promote()`, the `Promoted` event fires before any `Evicted` events. Budget enforcement (eviction) occurs after the new context unit is written. The brief intermediate state where active count exceeds budget is not observable externally because both operations occur within a single `promote()` call.

#### Scenario: Eviction publishes events for each evicted context unit

- **GIVEN** the unit budget is 20 and there are 21 active context units in context "ctx-1"
- **WHEN** a new context unit is promoted in context "ctx-1"
- **THEN** the lowest-ranked non-pinned unit is evicted AND an `Evicted` lifecycle event is published with the evicted context unit's ID and previous rank

#### Scenario: Pinned context units not evicted

- **GIVEN** the unit budget is 3, there are 3 active context units, and the lowest-ranked context unit is pinned
- **WHEN** a new context unit is promoted
- **THEN** the next-lowest-ranked non-pinned unit is evicted instead

### Requirement: Decay-based authority demotion

The `DecayPolicy` SPI SHALL include a `shouldDemoteAuthority(Context Unit context unit, int newRank)` method that returns `true` when the decayed rank drops below authority-specific thresholds.

Default thresholds (configurable via `ArcMemProperties`):
- `context units.context unit.reliable-rank-threshold` (default: `400`) — RELIABLE context units with rank below this are demoted to UNRELIABLE
- `context units.context unit.unreliable-rank-threshold` (default: `200`) — UNRELIABLE context units with rank below this are demoted to PROVISIONAL
- CANON is never demoted by decay (invariant A3b)
- PROVISIONAL has no lower threshold

`ArcMemEngine.applyDecay()` SHALL check `shouldDemoteAuthority()` after applying rank decay and call `demote()` if warranted.

#### Scenario: Rank decay triggers authority demotion

- **GIVEN** an context unit at RELIABLE authority with rank 450
- **WHEN** decay reduces the rank to 380
- **THEN** the context unit's rank is updated to 380 AND `demote()` is called with reason RANK_DECAY, reducing authority to UNRELIABLE

#### Scenario: Rank decay does not demote CANON

- **GIVEN** an context unit at CANON authority with rank 300
- **WHEN** decay reduces the rank to 250
- **THEN** the context unit's rank is updated to 250 but authority remains CANON

### Requirement: Optional returns for repository finders

**Modifies**: `ContextUnitRepository.findPropositionNodeById()`.

`findPropositionNodeById(String id)` SHALL return `Optional<PropositionNode>` instead of nullable `PropositionNode`. All callers in `ArcMemEngine` and `ContextTools` SHALL be updated to use `Optional` methods (`ifPresent`, `ifPresentOrElse`, `orElse`, etc.).

#### Scenario: Found context unit returns present Optional

- **GIVEN** an context unit exists with ID "A1"
- **WHEN** `findPropositionNodeById("A1")` is called
- **THEN** `Optional.of(node)` is returned

#### Scenario: Missing context unit returns empty Optional

- **GIVEN** no context unit exists with ID "missing"
- **WHEN** `findPropositionNodeById("missing")` is called
- **THEN** `Optional.empty()` is returned

### Requirement: Configuration validation at startup

**Modifies**: `ArcMemConfiguration`.

The system SHALL validate configuration properties at startup via a `@PostConstruct` method:
- `context unit.budget > 0`
- `context unit.autoActivateThreshold` in [0.0, 1.0]
- `assembly.promptTokenBudget >= 0`
- `context unit.minRank < context unit.maxRank`
- `context unit.initialRank` in [`context unit.minRank`, `context unit.maxRank`]
- `context unit.demoteThreshold` in [0.0, 1.0]
- Signal weights in `DomainProfile` sum to 1.0 (within tolerance 0.001)

Violations SHALL throw `IllegalStateException` with a descriptive message, preventing application startup.

#### Scenario: Invalid budget fails startup

- **GIVEN** `context units.context unit.budget` is set to 0
- **WHEN** the application starts
- **THEN** startup fails with `IllegalStateException` containing "budget must be > 0"

#### Scenario: Invalid rank range fails startup

- **GIVEN** `context units.context unit.minRank` is set to 900 and `context units.context unit.maxRank` is set to 100
- **WHEN** the application starts
- **THEN** startup fails with `IllegalStateException` containing "minRank must be less than maxRank"

#### Scenario: Valid configuration passes validation

- **GIVEN** all configuration values are within valid ranges
- **WHEN** the application starts
- **THEN** validation passes and the application starts normally

### Requirement: Fix ReinforcementPolicy.threshHold() typo

**Modifies**: `ReinforcementPolicy` interface.

The method `threshHold()` SHALL be renamed to `threshold()`. All call sites SHALL be updated.

### Requirement: ArcMemContextLock cleanup

**Modifies**: `ArcMemContextLock`.

`ArcMemContextLock` SHALL replace `AtomicBoolean locked` + `volatile String lockedBy` with a single `AtomicReference<String> lockedBy`. Lock state is derived from whether `lockedBy` is null:
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

**Modifies**: `ContextUnitRepository`.

All dynamic values in Cypher queries SHALL use parameterized bindings (Drivine `.bind()`) instead of string concatenation. The current codebase contains string concatenation for `contextId` in several query methods, which is vulnerable to Cypher injection.

This is a correctness and security fix — the system resists adversarial prompt drift, and the persistence layer should not be vulnerable to injection.

#### Scenario: Context ID with special characters handled safely

- **GIVEN** a context ID containing special characters (e.g., `"ctx-'; DROP (n) --"`)
- **WHEN** any repository method is called with this context ID
- **THEN** the query executes safely with the context ID treated as a literal parameter value, not as Cypher syntax
