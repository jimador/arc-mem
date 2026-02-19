# Anchor Lifecycle Events

## Overview

Spring ApplicationEvent-based lifecycle hooks for AnchorEngine operations, enabling structured telemetry, audit trails, and Langfuse/OTEL integration without mixing observability concerns into core engine logic.

## Requirements

### Requirement: Event Base Class
- The system MUST provide an abstract `AnchorLifecycleEvent` extending `ApplicationEvent`
- All lifecycle events MUST carry `contextId` (String) and `timestamp` (Instant)
- Events MUST be published via Spring's `ApplicationEventPublisher`

### Requirement: Core Lifecycle Events
The system MUST publish events for these operations:

1. **AnchorPromotedEvent** — after `AnchorEngine.promote()` completes
   - MUST include: propositionId, anchorId, initialRank, contextId
2. **AnchorReinforcedEvent** — after `AnchorEngine.reinforce()` completes
   - MUST include: anchorId, previousRank, newRank, reinforcementCount, contextId
3. **AnchorArchivedEvent** — after anchor archival completes
   - MUST include: anchorId, reason (enum: CONFLICT_REPLACEMENT, BUDGET_EVICTION, MANUAL), contextId
4. **ConflictDetectedEvent** — after `AnchorEngine.detectConflicts()` finds conflicts
   - MUST include: incomingText, conflictCount, contextId
   - SHOULD include: list of conflicting anchor IDs
5. **ConflictResolvedEvent** — after `AnchorEngine.resolveConflict()` returns
   - MUST include: existingAnchorId, resolution (KEEP_EXISTING/REPLACE/COEXIST), contextId
6. **AuthorityUpgradedEvent** — after authority upgrade in `reinforce()`
   - MUST include: anchorId, previousAuthority, newAuthority, reinforcementCount, contextId

### Requirement: Event Publishing
- Events MUST be published after state changes complete (not before)
- Event publishing MUST NOT throw exceptions that break the calling operation
- Event publishing SHOULD be configurable via `dice-anchors.anchor.lifecycle-events-enabled` property
- When disabled, no events MUST be published and no publisher calls MUST be made

### Requirement: Default Listener
- The system MUST provide a default `AnchorLifecycleListener` with `@EventListener` methods
- The listener MUST log each event at INFO level using structured SLF4J placeholders
- The listener SHOULD add OTEL span attributes when tracing is active
- The listener MUST be a Spring `@Component` that can be excluded or replaced

### Requirement: No Mandatory Consumers
- The system MUST NOT require any event consumer to be registered
- Events with no listeners MUST be silently discarded by Spring's event infrastructure
- No event MUST block the publishing thread (default synchronous delivery is acceptable for demo scope)

## Scenarios

#### Scenario: Anchor promoted successfully
- Given a proposition passes all promotion gates
- When `AnchorEngine.promote()` completes
- Then an `AnchorPromotedEvent` is published with the new anchor's ID and rank

#### Scenario: Conflict detected and resolved as REPLACE
- Given incoming text conflicts with an existing anchor
- When conflict is resolved as REPLACE
- Then both `ConflictDetectedEvent` and `ConflictResolvedEvent` are published
- And an `AnchorArchivedEvent` is published for the replaced anchor with reason CONFLICT_REPLACEMENT

#### Scenario: Authority upgraded during reinforcement
- Given an anchor reaches the reinforcement threshold for upgrade
- When `reinforce()` upgrades authority from PROVISIONAL to UNRELIABLE
- Then both `AnchorReinforcedEvent` and `AuthorityUpgradedEvent` are published

#### Scenario: Events disabled via configuration
- Given `dice-anchors.anchor.lifecycle-events-enabled` is false
- When any anchor lifecycle operation completes
- Then no events are published

## Constitutional Alignment

This capability follows the project's policy-based architecture pattern. Events are additive-only with no breaking changes to existing APIs. All events use constructor injection and immutable data carriers per CLAUDE.md coding style.
