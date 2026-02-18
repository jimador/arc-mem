# Implementation Tasks

## 1. Event Base Class & Event Types

- [x] 1.1 Create `anchor/event/` package
- [x] 1.2 Create `AnchorLifecycleEvent` abstract class extending `ApplicationEvent`
  - [x] 1.2.1 Fields: `contextId` (String), `timestamp` (Instant)
  - [x] 1.2.2 Constructor: `(Object source, String contextId)`
  - [x] 1.2.3 Getters for contextId and timestamp
- [x] 1.3 Create `ArchiveReason` enum: CONFLICT_REPLACEMENT, BUDGET_EVICTION, MANUAL
- [x] 1.4 Create `AnchorPromotedEvent` extending `AnchorLifecycleEvent`
  - Fields: propositionId, anchorId, initialRank
- [x] 1.5 Create `AnchorReinforcedEvent` extending `AnchorLifecycleEvent`
  - Fields: anchorId, previousRank, newRank, reinforcementCount
- [x] 1.6 Create `AnchorArchivedEvent` extending `AnchorLifecycleEvent`
  - Fields: anchorId, reason (ArchiveReason)
- [x] 1.7 Create `ConflictDetectedEvent` extending `AnchorLifecycleEvent`
  - Fields: incomingText, conflictCount, conflictingAnchorIds (List<String>)
- [x] 1.8 Create `ConflictResolvedEvent` extending `AnchorLifecycleEvent`
  - Fields: existingAnchorId, resolution (ConflictResolver.Resolution)
- [x] 1.9 Create `AuthorityUpgradedEvent` extending `AnchorLifecycleEvent`
  - Fields: anchorId, previousAuthority, newAuthority, reinforcementCount

## 2. AnchorEngine Integration

- [x] 2.1 Add `ApplicationEventPublisher` to `AnchorEngine` constructor
- [x] 2.2 Add `lifecycleEventsEnabled` boolean from `AnchorConfig`
- [x] 2.3 Add private `publish(AnchorLifecycleEvent)` guard method
- [x] 2.4 Publish `AnchorPromotedEvent` at end of `promote()`
- [x] 2.5 Publish `AnchorReinforcedEvent` at end of `reinforce()`
- [x] 2.6 Publish `AuthorityUpgradedEvent` after authority upgrade in `reinforce()` (conditional)
- [x] 2.7 Publish `ConflictDetectedEvent` at end of `detectConflicts()` (only when conflicts found)
- [x] 2.8 Publish `ConflictResolvedEvent` at end of `resolveConflict()`

## 3. AnchorPromoter Integration

- [x] 3.1 Pass `ApplicationEventPublisher` + `lifecycleEventsEnabled` to `AnchorPromoter` constructor
- [x] 3.2 Publish `AnchorArchivedEvent` with reason CONFLICT_REPLACEMENT when REPLACE resolution archives an anchor

## 4. Default Listener

- [x] 4.1 Create `AnchorLifecycleListener` @Component in `anchor/event/`
- [x] 4.2 Add `@EventListener` method for each of the 6 event types
- [x] 4.3 Each method logs at INFO with `[LIFECYCLE]` prefix and structured placeholders
- [x] 4.4 Add `@ConditionalOnMissingBean` so listener can be replaced

## 5. Configuration

- [x] 5.1 Add `lifecycleEventsEnabled` to `DiceAnchorsProperties.AnchorConfig` (@DefaultValue("true"))
- [x] 5.2 Add `lifecycle-events-enabled: true` to `application.yml`

## 6. Testing

- [x] 6.1 Unit tests for `AnchorEngine` event publishing
  - [x] 6.1.1 Test `promote()` publishes `AnchorPromotedEvent`
  - [x] 6.1.2 Test `reinforce()` publishes `AnchorReinforcedEvent`
  - [x] 6.1.3 Test `reinforce()` publishes `AuthorityUpgradedEvent` when threshold met
  - [x] 6.1.4 Test `detectConflicts()` publishes `ConflictDetectedEvent` when conflicts exist
  - [x] 6.1.5 Test `detectConflicts()` does NOT publish when no conflicts
  - [x] 6.1.6 Test `resolveConflict()` publishes `ConflictResolvedEvent`
  - [x] 6.1.7 Test events NOT published when `lifecycleEventsEnabled` is false
- [x] 6.2 Unit tests for `AnchorLifecycleListener` (verify logging output)
- [x] 6.3 Update existing `AnchorEngine` tests for new constructor parameter
- [x] 6.4 Update existing `AnchorPromoter` tests for new constructor parameters

## 7. Verification

- [x] 7.1 Run full test suite: `./mvnw.cmd test`
- [x] 7.2 Build without tests: `./mvnw.cmd clean compile -DskipTests`
- [ ] 7.3 Manual smoke test: Start app, verify lifecycle events appear in logs
- [ ] 7.4 Verify events visible in Langfuse when tracing enabled

## 8. Documentation & Cleanup

- [x] 8.1 Add Javadoc to all event classes
- [x] 8.2 Add Javadoc to `AnchorLifecycleListener`
- [x] 8.3 Update CLAUDE.md Key Files table with event package
- [ ] 8.4 Verify no debug logging left in code
- [x] 8.5 Code style check per CLAUDE.md
