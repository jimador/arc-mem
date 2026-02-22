## Why

Anchors currently exist in a flat priority space: rank [100-900] determines injection order and eviction priority, authority gates compliance strength, but there is no structural concept of *memory tier*. Every active anchor competes equally for prompt budget regardless of whether it was reinforced moments ago or has been decaying for hours. This creates two problems:

1. **Stale anchors crowd out fresh context.** A RELIABLE anchor with rank 400 that hasn't been reinforced in 20 turns occupies the same tier as a freshly promoted anchor with rank 400. The system has no recency signal beyond raw rank decay.
2. **No graduated lifecycle treatment.** Decay, eviction, and prompt assembly treat all non-CANON anchors uniformly. There is no mechanism to apply faster decay to ephemeral observations while preserving durable facts, nor to partition prompt budget across tiers.

Wave 1 of the Anchors Working Memory Evolution roadmap places this feature first because all subsequent features (conflict calibration, retrieval quality gates, temporal validity) depend on a well-defined tier model.

## What Changes

- Introduce a three-tier memory model: **HOT** (actively reinforced, high recency), **WARM** (established but not recently reinforced), **COLD** (decayed, approaching eviction threshold).
- Define tier boundaries as configurable rank thresholds, with automatic tier transitions driven by reinforcement and decay.
- Tier-aware prompt assembly: HOT anchors injected first within their authority band; COLD anchors are candidates for early eviction before budget is exhausted.
- Tier-aware decay modulation: HOT tier decays slower (protected window after reinforcement), COLD tier decays faster (accelerated cleanup).
- Lifecycle events for tier transitions, enabling observability and UI visibility.
- UI indicator in simulation inspector showing current tier per anchor.

## Capabilities

### New Capabilities

- `memory-tiering`: Defines the three-tier model (HOT/WARM/COLD), tier boundaries, automatic transitions on reinforcement/decay, and the `MemoryTier` enum with tier-specific policy parameters.
- `tier-aware-decay`: Extends decay policy to modulate half-life based on current memory tier. HOT anchors receive a configurable decay shield duration; COLD anchors receive an accelerated decay multiplier.
- `tier-aware-assembly`: Extends prompt assembly to prefer HOT anchors within each authority band and apply tier-based drop priority when token budget is constrained.

### Modified Capabilities

- `anchor-lifecycle`: Tier transitions MUST publish lifecycle events. `AnchorEngine` MUST track and update tier on reinforce/decay operations.
- `observability`: Tier transition events MUST include OTEL span attributes (`anchor.tier`, `anchor.tier.previous`).

## Impact

- **anchor package**: New `MemoryTier` enum, tier threshold configuration, tier field on `Anchor` record.
- **AnchorEngine**: Tier calculation on promote/reinforce/decay, tier transition event publishing.
- **DecayPolicy**: Tier-aware half-life modulation (existing `ExponentialDecayPolicy` extended or wrapped).
- **AnchorsLlmReference / PromptBudgetEnforcer**: Tier-aware ordering within authority bands, tier-based drop priority.
- **PropositionNode / AnchorRepository**: New `memoryTier` field persisted, Cypher queries updated.
- **Lifecycle events**: New `TierChanged` event type.
- **SimulationView / ContextInspectorPanel**: Tier badge display per anchor.
- **application.yml**: New config block for tier thresholds and decay modifiers.

### Constitutional Alignment

- **Article II**: All tier data persisted in Neo4j only. No new persistence backends.
- **Article III**: Constructor injection for all new components.
- **Article IV**: `MemoryTier` as enum, tier config as record.
- **Article V**: Anchor invariants preserved. Tier is orthogonal to authority and rank clamping. Budget enforcement (A1) enhanced, not weakened.
- **Article VI**: Tier scoped by contextId like all anchor data.
- **Article VII**: Tier transition logic covered by unit tests.
