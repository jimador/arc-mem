## Why

AnchorEngine lifecycle operations (promote, reinforce, archive, conflict detect/resolve) currently log to SLF4J but have no structured event emission. This means Langfuse and OpenTelemetry cannot consume anchor lifecycle data for audit trails, telemetry dashboards, or demo observability. The existing `ConversationAnalysisRequestEvent` pattern proves Spring ApplicationEvent works well in this codebase — extending it to anchor operations is a natural fit.

## What Changes

- Add abstract `AnchorLifecycleEvent` base class extending `ApplicationEvent` with `contextId` and `timestamp`
- Add 6 concrete event classes for core lifecycle operations: promoted, reinforced, archived, conflict-detected, conflict-resolved, authority-upgraded
- Inject `ApplicationEventPublisher` into `AnchorEngine` via constructor (additive — no breaking changes)
- Publish events after each state-changing operation completes
- Create default `AnchorLifecycleListener` that bridges events to SLF4J structured logging and OTEL span attributes
- Add configuration property to enable/disable event publishing

## Capabilities

### New Capabilities
- `anchor-lifecycle-events`: Spring ApplicationEvent-based lifecycle hooks for anchor engine operations, with default Langfuse/OTEL bridge listener

### Modified Capabilities
_(none — additive only)_

## Impact

- `AnchorEngine` constructor gains `ApplicationEventPublisher` parameter
- New `anchor/event/` package with 7 classes (1 base + 6 events)
- New `AnchorLifecycleListener` in `anchor/event/` package
- New config: `dice-anchors.anchor.lifecycle-events-enabled` (default: true)
- All existing tests that construct `AnchorEngine` need updated constructor calls
