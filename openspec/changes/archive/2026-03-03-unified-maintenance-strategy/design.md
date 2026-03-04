## Context

dice-anchors currently manages anchor health through two independent policy interfaces: `DecayPolicy` (exponential rank decay based on time since reinforcement) and `ReinforcementPolicy` (rank boosting and authority upgrade thresholds). Both are invoked reactively by `AnchorEngine` -- decay runs per-turn, reinforcement runs when a matching proposition is observed. There is no mechanism for proactive assessment of anchor health across the full active set.

Research from the Sleeping LLM project (Guo et al., 2025) demonstrates that proactive memory consolidation recovers degraded knowledge from 40% to 100% recall within 4 sleep cycles. Their 8-step sleep architecture (health check, curate, audit, maintain, LoRA consolidation, MEMIT scale-down, validate, report) provides a reference model for periodic anchor maintenance. This change defines the coordination interface; F07 will implement the proactive sweep logic.

### Current Code Organization

```
anchor/
  DecayPolicy.java           # Interface + ExponentialDecayPolicy record
  ReinforcementPolicy.java   # Interface + ThresholdReinforcementPolicy class
  AnchorEngine.java           # Calls policies directly in reinforce() and applyDecay()
  AnchorConfiguration.java    # @Bean factories for DecayPolicy and ReinforcementPolicy
```

## Goals / Non-Goals

**Goals:**
- Define a unified `MaintenanceStrategy` sealed interface that coordinates reactive and proactive maintenance.
- Implement `ReactiveMaintenanceStrategy` that wraps existing policies with zero behavioral change.
- Stub `ProactiveMaintenanceStrategy` for F07 with a complete sweep contract.
- Implement `HybridMaintenanceStrategy` composing reactive and proactive via delegation.
- Make strategy selection configurable via `DiceAnchorsProperties`.
- Wire strategy bean in `AnchorConfiguration` based on configured mode.

**Non-Goals:**
- Implementing the proactive sweep logic (deferred to F07).
- Integrating memory pressure triggers into `shouldRunSweep()` (deferred to F04).
- Refactoring `AnchorEngine` to delegate through the strategy (can be done independently).
- Adding scenario YAML override for maintenance mode (future enhancement).
- Adding UI display of active strategy (future enhancement).

## Decisions

### D1: Sealed Interface Pattern

**Decision**: `MaintenanceStrategy` is a sealed interface permitting exactly three implementations. This is a closed hierarchy -- maintenance modes are a fixed set (reactive, proactive, hybrid) and switch expressions over the sealed type are exhaustive at compile time.

```java
public sealed interface MaintenanceStrategy
        permits ReactiveMaintenanceStrategy, ProactiveMaintenanceStrategy, HybridMaintenanceStrategy {
    void onTurnComplete(MaintenanceContext context);
    boolean shouldRunSweep(MaintenanceContext context);
    SweepResult executeSweep(MaintenanceContext context);
}
```

**Why sealed over open**: The three modes represent fundamentally different scheduling behaviors (turn-bound, periodic, combined). Open extension would require runtime validation of unknown scheduling semantics. Sealed ensures the `AnchorConfiguration` bean factory covers all cases exhaustively.

**Why not an abstract class**: No shared state or shared implementation between the three strategies. An interface is the right abstraction for a pure behavioral contract.

### D2: Composition Over Inheritance for HybridMaintenanceStrategy

**Decision**: `HybridMaintenanceStrategy` composes a `ReactiveMaintenanceStrategy` and a `ProactiveMaintenanceStrategy` via delegation, rather than inheriting from either.

```java
public non-sealed class HybridMaintenanceStrategy implements MaintenanceStrategy {
    private final ReactiveMaintenanceStrategy reactive;
    private final ProactiveMaintenanceStrategy proactive;
    // onTurnComplete -> reactive, shouldRunSweep/executeSweep -> proactive
}
```

**Why composition**: The hybrid's behavior is a pure combination of the two delegates. Inheritance would create a semantic relationship ("hybrid IS-A reactive") that misrepresents the design. Composition makes the delegation explicit and testable.

### D3: ProactiveMaintenanceStrategy is non-sealed

**Decision**: `ProactiveMaintenanceStrategy` is `non-sealed` to allow F07 to extend it with concrete sweep logic. `ReactiveMaintenanceStrategy` is `final` because its behavior is fixed (delegate to existing policies).

**Why**: The proactive strategy is explicitly a stub. Making it `non-sealed` signals that the class is designed for extension. Making the reactive strategy `final` signals that its behavior is complete and should not be overridden.

### D4: SweepResult 5-Step Contract

**Decision**: `SweepResult` records five counters corresponding to the sweep contract steps derived from Sleeping LLM's 8-step sleep architecture:

| SweepResult Field | Sleeping LLM Step | Description |
|---|---|---|
| `anchorsAudited` | Step 3 (Audit) | Anchors tested for recall/health |
| `anchorsRefreshed` | Step 4 (Maintain) | Anchors whose rank/state was refreshed |
| (implicit in pruned) | Step 5 (Consolidate) | Overlap consolidation -- counted as pruned |
| `anchorsPruned` | Step 6 (Scale-Down) | Anchors removed or archived |
| `anchorsValidated` | Step 7 (Validate) | Anchors verified post-sweep |

Steps 1 (Health Check), 2 (Curate), and 8 (Report) from Sleeping LLM are not applicable to the anchor model: health check is implicit in audit, curation happens during extraction (not maintenance), and reporting is handled by the `SweepResult` itself.

### D5: Configuration Defers Proactive Parameters

**Decision**: `MaintenanceConfig` contains only `mode` (default: REACTIVE). Proactive sweep parameters (interval, pressure threshold, consolidation settings) are deferred to F07. This avoids speculative configuration that would change when the actual implementation is designed.

```java
public record MaintenanceConfig(
        @DefaultValue("REACTIVE") MaintenanceMode mode
) {}
```

### D6: Bean Selection via Switch Expression

**Decision**: `AnchorConfiguration.maintenanceStrategy()` uses a switch expression over `MaintenanceMode` to select the implementation. The `@ConditionalOnMissingBean` annotation allows test overrides.

```java
@Bean
@ConditionalOnMissingBean
MaintenanceStrategy maintenanceStrategy(DecayPolicy decayPolicy, ReinforcementPolicy reinforcementPolicy) {
    var mode = properties.maintenance() != null
            ? properties.maintenance().mode()
            : MaintenanceMode.REACTIVE;
    return switch (mode) {
        case REACTIVE -> new ReactiveMaintenanceStrategy(decayPolicy, reinforcementPolicy);
        case PROACTIVE -> new ProactiveMaintenanceStrategy();
        case HYBRID -> {
            var reactive = new ReactiveMaintenanceStrategy(decayPolicy, reinforcementPolicy);
            yield new HybridMaintenanceStrategy(reactive, new ProactiveMaintenanceStrategy());
        }
    };
}
```

The null check on `properties.maintenance()` handles the case where the configuration section is absent entirely, defaulting to REACTIVE.

## File Inventory

### New Files (7)

| File | Package | Type | Description |
|---|---|---|---|
| `MaintenanceStrategy.java` | `anchor/` | Sealed interface | Coordination contract with 3 methods |
| `MaintenanceMode.java` | `anchor/` | Enum | REACTIVE, PROACTIVE, HYBRID |
| `MaintenanceContext.java` | `anchor/` | Record | Runtime context for strategy decisions |
| `SweepResult.java` | `anchor/` | Record | Outcome of proactive sweep with 5 counters |
| `ReactiveMaintenanceStrategy.java` | `anchor/` | Final class | Wraps DecayPolicy + ReinforcementPolicy |
| `ProactiveMaintenanceStrategy.java` | `anchor/` | Non-sealed class | Stub for F07 |
| `HybridMaintenanceStrategy.java` | `anchor/` | Non-sealed class | Composes reactive + proactive |

### Modified Files (3)

| File | Change |
|---|---|
| `DiceAnchorsProperties.java` | Add `MaintenanceConfig` record, add `maintenance` field to root record |
| `AnchorConfiguration.java` | Add `maintenanceStrategy()` bean method |
| `application.yml` | Add `dice-anchors.maintenance.mode: REACTIVE` |

## Research Attribution

The proactive maintenance concept is directly inspired by the Sleeping LLM project (Guo et al., 2025):

- **Source**: [github.com/vbario/sleeping-llm](https://github.com/vbario/sleeping-llm)
- **Key paper**: "Per-Fact Graduated Consolidation Resolves the Capacity Ceiling in Weight-Edited Language Models" (DOI: 10.5281/zenodo.18779159)
- **Specific finding**: 30 facts at 40% initial recall recover to 100% within 4 sleep cycles, demonstrating that proactive consolidation recovers knowledge that reactive-only maintenance cannot.
- **Architectural mapping**: The 5-step sweep contract (audit, refresh, consolidate, prune, validate) maps to 5 of the 8 steps in Sleeping LLM's sleep architecture. Steps 1 (health check), 2 (curation), and 8 (reporting) are handled by other components in the anchor system.

The wake/sleep metaphor maps directly to the anchor maintenance modes:
- **REACTIVE** = always awake, maintenance runs inline with conversation.
- **PROACTIVE** = periodic sleep cycles, maintenance runs on a schedule.
- **HYBRID** = wake/sleep cycle -- lightweight maintenance per turn, deep consolidation periodically.

## Deferred Work

| Item | Deferred To | Reason |
|---|---|---|
| Proactive sweep implementation | F07 | Interface-only in this change; concrete audit/refresh/prune logic requires its own design |
| Memory pressure integration | F04 | `shouldRunSweep()` is the hook; pressure calculation is F04's scope |
| Scenario YAML maintenance mode override | Future | Configuration via properties is sufficient for initial use |
| AnchorEngine delegation refactor | Independent | Engine can route through strategy later without changing this interface |
| UI display of active strategy | Future | SimulationView and RunInspectorView enhancements |

## Risks / Trade-offs

| Risk | Mitigation |
|---|---|
| **Interface over-engineering** | Three methods is minimal. Validated against F07 requirements before finalizing. |
| **Unused stub code** | ProactiveMaintenanceStrategy is a no-op until F07. Acceptable as a well-documented extension point. |
| **Config section with one field** | `MaintenanceConfig` has only `mode` today. F07 will add proactive parameters. Starting with one field avoids speculative config. |
| **Thread safety assumptions** | All implementations are stateless or use only final fields. Thread safety is structural, not runtime-verified. |

## Open Questions

None. All design questions from the prep document have been resolved:
- Proactive sweep step ordering: deferred to F07 (interface only specifies `executeSweep()`).
- HYBRID mode interaction semantics: sequential (reactive first, proactive second).
- Config schema for proactive parameters: deferred to F07 (only `mode` in this change).
- AnchorEngine delegation: not in scope for this change (engine continues to call policies directly).
