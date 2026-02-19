# Design: Lifecycle Listener SPI

## 1. Event Hierarchy

New package: `dev.dunnam.diceanchors.anchor.event`

```
AnchorLifecycleEvent (abstract, extends ApplicationEvent)
├── contextId: String
├── timestamp: Instant
│
├── AnchorPromotedEvent
│   ├── propositionId: String
│   ├── anchorId: String
│   └── initialRank: int
│
├── AnchorReinforcedEvent
│   ├── anchorId: String
│   ├── previousRank: int
│   ├── newRank: int
│   └── reinforcementCount: int
│
├── AnchorArchivedEvent
│   ├── anchorId: String
│   └── reason: ArchiveReason (enum: CONFLICT_REPLACEMENT, BUDGET_EVICTION, MANUAL)
│
├── ConflictDetectedEvent
│   ├── incomingText: String
│   ├── conflictCount: int
│   └── conflictingAnchorIds: List<String>
│
├── ConflictResolvedEvent
│   ├── existingAnchorId: String
│   └── resolution: ConflictResolver.Resolution
│
└── AuthorityUpgradedEvent
    ├── anchorId: String
    ├── previousAuthority: Authority
    ├── newAuthority: Authority
    └── reinforcementCount: int
```

## 2. AnchorEngine Modification

Add `ApplicationEventPublisher` to constructor. Add `boolean lifecycleEventsEnabled` from properties.

```java
public AnchorEngine(
        AnchorRepository repository,
        DiceAnchorsProperties properties,
        ConflictDetector conflictDetector,
        ConflictResolver conflictResolver,
        ReinforcementPolicy reinforcementPolicy,
        ApplicationEventPublisher eventPublisher) {  // NEW
    // ...
    this.eventPublisher = eventPublisher;
    this.lifecycleEventsEnabled = properties.anchor().lifecycleEventsEnabled();
}
```

Helper method to guard publishing:

```java
private void publish(AnchorLifecycleEvent event) {
    if (lifecycleEventsEnabled) {
        eventPublisher.publishEvent(event);
    }
}
```

### Publish points:

| Method | Event | When |
|--------|-------|------|
| `promote()` | `AnchorPromotedEvent` | After successful promotion + eviction |
| `reinforce()` | `AnchorReinforcedEvent` | After rank update |
| `reinforce()` | `AuthorityUpgradedEvent` | After authority upgrade (conditional) |
| `detectConflicts()` | `ConflictDetectedEvent` | After detection, only if conflicts found |
| `resolveConflict()` | `ConflictResolvedEvent` | After resolution returned |

Archive events are published from `AnchorPromoter` when REPLACE resolution archives the existing anchor.

## 3. Default Listener

```java
@Component
public class AnchorLifecycleListener {

    private static final Logger logger = LoggerFactory.getLogger(AnchorLifecycleListener.class);

    @EventListener
    public void onPromoted(AnchorPromotedEvent event) {
        logger.info("[LIFECYCLE] Anchor promoted: {} rank={} context={}",
                event.getAnchorId(), event.getInitialRank(), event.getContextId());
    }

    @EventListener
    public void onReinforced(AnchorReinforcedEvent event) { ... }

    @EventListener
    public void onArchived(AnchorArchivedEvent event) { ... }

    @EventListener
    public void onConflictDetected(ConflictDetectedEvent event) { ... }

    @EventListener
    public void onConflictResolved(ConflictResolvedEvent event) { ... }

    @EventListener
    public void onAuthorityUpgraded(AuthorityUpgradedEvent event) { ... }
}
```

Each method logs at INFO with `[LIFECYCLE]` prefix for easy Langfuse filtering.

## 4. Configuration

Add to `DiceAnchorsProperties.AnchorConfig`:
```java
@DefaultValue("true") boolean lifecycleEventsEnabled
```

In `application.yml`:
```yaml
dice-anchors:
  anchor:
    lifecycle-events-enabled: true
```

## 5. Key Design Decisions

1. **Spring ApplicationEvent over custom SPI** — Reuses existing `ConversationAnalysisRequestEvent` pattern. No new listener management code needed.
2. **Events are classes, not records** — `ApplicationEvent` requires `super(source)` constructor call, which records can't cleanly express. Use regular classes with getters.
3. **Guard method `publish()`** — Single check point for enabled/disabled. Avoids per-call conditionals scattered through engine.
4. **No async delivery** — Default Spring synchronous delivery is fine for demo scope. Events are lightweight (no IO in listeners).
5. **ArchiveReason enum** — Distinguishes why an anchor was archived, enabling richer audit trails.
6. **AnchorPromoter publishes archive events** — The REPLACE resolution path in AnchorPromoter is where archival happens, so it publishes `AnchorArchivedEvent`. Requires passing `eventPublisher` through.

## 6. Non-Goals

- No custom OTEL span creation (rely on `@Observed` and Langfuse auto-instrumentation)
- No event persistence (events are transient, consumed by listeners)
- No AnchorPromoter gate-level events (keep scope to Tier 1 engine events)
