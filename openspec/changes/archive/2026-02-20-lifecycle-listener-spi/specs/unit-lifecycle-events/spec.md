# Context Unit Lifecycle Events

## Overview

Spring ApplicationEvent-based lifecycle hooks for ArcMemEngine operations, enabling structured telemetry, audit trails, and Langfuse/OTEL integration without mixing observability concerns into core engine logic.

## Requirements

### Requirement: Event Base Class
- The system MUST provide an abstract `ContextUnitLifecycleEvent` extending `ApplicationEvent`
- All lifecycle events MUST carry `contextId` (String) and `timestamp` (Instant)
- Events MUST be published via Spring's `ApplicationEventPublisher`

### Requirement: Core Lifecycle Events
The system MUST publish events for these operations:

1. **ContextUnitPromotedEvent** — after `ArcMemEngine.promote()` completes
   - MUST include: propositionId, unitId, initialRank, contextId
2. **ContextUnitReinforcedEvent** — after `ArcMemEngine.reinforce()` completes
   - MUST include: unitId, previousRank, newRank, reinforcementCount, contextId
3. **ContextUnitArchivedEvent** — after context unit archival completes
   - MUST include: unitId, reason (enum: CONFLICT_REPLACEMENT, BUDGET_EVICTION, MANUAL), contextId
4. **ConflictDetectedEvent** — after `ArcMemEngine.detectConflicts()` finds conflicts
   - MUST include: incomingText, conflictCount, contextId
   - SHOULD include: list of conflicting context unit IDs
5. **ConflictResolvedEvent** — after `ArcMemEngine.resolveConflict()` returns
   - MUST include: existingUnitId, resolution (KEEP_EXISTING/REPLACE/COEXIST), contextId
6. **AuthorityUpgradedEvent** — after authority upgrade in `reinforce()`
   - MUST include: unitId, previousAuthority, newAuthority, reinforcementCount, contextId

### Requirement: Event Publishing
- Events MUST be published after state changes complete (not before)
- Event publishing MUST NOT throw exceptions that break the calling operation
- Event publishing SHOULD be configurable via `context units.context unit.lifecycle-events-enabled` property
- When disabled, no events MUST be published and no publisher calls MUST be made

### Requirement: Default Listener
- The system MUST provide a default `ContextUnitLifecycleListener` with `@EventListener` methods
- The listener MUST log each event at INFO level using structured SLF4J placeholders
- The listener SHOULD add OTEL span attributes when tracing is active
- The listener MUST be a Spring `@Component` that can be excluded or replaced

### Requirement: No Mandatory Consumers
- The system MUST NOT require any event consumer to be registered
- Events with no listeners MUST be silently discarded by Spring's event infrastructure
- No event MUST block the publishing thread (default synchronous delivery is acceptable for demo scope)

## Scenarios

#### Scenario: Context Unit promoted successfully
- Given a proposition passes all promotion gates
- When `ArcMemEngine.promote()` completes
- Then an `ContextUnitPromotedEvent` is published with the new context unit's ID and rank

#### Scenario: Conflict detected and resolved as REPLACE
- Given incoming text conflicts with an existing context unit
- When conflict is resolved as REPLACE
- Then both `ConflictDetectedEvent` and `ConflictResolvedEvent` are published
- And an `ContextUnitArchivedEvent` is published for the replaced context unit with reason CONFLICT_REPLACEMENT

#### Scenario: Authority upgraded during reinforcement
- Given an context unit reaches the reinforcement threshold for upgrade
- When `reinforce()` upgrades authority from PROVISIONAL to UNRELIABLE
- Then both `ContextUnitReinforcedEvent` and `AuthorityUpgradedEvent` are published

#### Scenario: Events disabled via configuration
- Given `context units.context unit.lifecycle-events-enabled` is false
- When any context unit lifecycle operation completes
- Then no events are published

## Constitutional Alignment

This capability follows the project's policy-based architecture pattern. Events are additive-only with no breaking changes to existing APIs. All events use constructor injection and immutable data carriers per CLAUDE.md coding style.
